package com.hossammo.cash.data.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.hossammo.cash.BuildConfig
import com.hossammo.cash.data.db.AppDatabase
import com.hossammo.cash.data.db.entity.BackupHistoryEntity
import com.hossammo.cash.data.repo.AuditLogger
import com.hossammo.cash.domain.model.MonthKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * النسخ الاحتياطي والاستعادة محليًا فقط (لا رفع تلقائي لأي سحابة).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val audit: AuditLogger,
) {
    private val maxAttachmentBytes = 15L * 1024 * 1024

    /** يجمع البيانات في Payload. period != null => نسخة لفترة مختارة (أرصدة وتحويلات الفترة + البيانات المرجعية + الديون). */
    suspend fun buildPayload(period: Pair<MonthKey, MonthKey>? = null): BackupPayload = withContext(Dispatchers.IO) {
        var tables = BackupTables(
            categories = db.categoryDao().all(),
            locations = db.locationDao().all(),
            items = db.itemDao().all(),
            months = db.monthDao().all(),
            balances = db.balanceDao().all(),
            transfers = db.transferDao().all(),
            rates = db.rateDao().all(),
            snapshots = db.snapshotDao().all(),
            debts = db.debtDao().all(),
            payments = db.paymentDao().all(),
            attachments = db.attachmentDao().all(),
            settings = db.settingDao().all(),
            audit = if (period == null) db.auditDao().all() else emptyList(),
        )
        if (period != null) {
            val (from, to) = period
            fun inRange(k: Int) = k >= from.value && k <= to.value
            tables = tables.copy(
                months = tables.months.filter { inRange(it.monthKey) },
                balances = tables.balances.filter { inRange(it.monthKey) },
                transfers = tables.transfers.filter { inRange(it.monthKey) },
                snapshots = tables.snapshots.filter { inRange(it.monthKey) },
            )
        }
        // تضمين ملفات المرفقات إذا كان حجمها مقبولًا
        var budget = maxAttachmentBytes
        val files = tables.attachments.filter { it.deletedAt == null }.mapNotNull { a ->
            val f = File(a.localPath)
            if (!f.exists() || f.length() > budget) return@mapNotNull null
            budget -= f.length()
            BackupAttachmentFile(a.id, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
        }
        tables = tables.copy(attachmentFiles = files)
        val tablesJson = BackupJson.encodeToString(tables)
        val meta = BackupMeta(
            schemaVersion = AppDatabase.VERSION,
            appVersion = BuildConfig.VERSION_NAME,
            createdAt = System.currentTimeMillis(),
            type = if (period == null) TYPE_FULL else TYPE_PERIOD,
            periodFrom = period?.first?.value,
            periodTo = period?.second?.value,
            counts = mapOf(
                "items" to tables.items.size, "months" to tables.months.size, "balances" to tables.balances.size,
                "transfers" to tables.transfers.size, "debts" to tables.debts.size, "payments" to tables.payments.size,
                "attachments" to tables.attachments.size,
            ),
            checksum = BackupCrypto.sha256(tablesJson.toByteArray()),
        )
        BackupPayload(meta, tables)
    }

    fun encode(payload: BackupPayload, password: CharArray): ByteArray =
        BackupCrypto.encrypt(BackupJson.encodeToString(payload).toByteArray(Charsets.UTF_8), password)

    /** إنشاء نسخة احتياطية وكتابتها إلى المكان الذي اختاره المستخدم. */
    suspend fun createBackup(target: Uri, password: CharArray, period: Pair<MonthKey, MonthKey>? = null): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildPayload(period)
            val bytes = encode(payload, password)
            context.contentResolver.openOutputStream(target, "wt")?.use { it.write(bytes) } ?: error("cannot write")
            db.backupDao().insert(
                BackupHistoryEntity(
                    UUID.randomUUID().toString(), System.currentTimeMillis(), target.lastPathSegment ?: "backup",
                    target.toString(), bytes.size.toLong(), payload.meta.type, true,
                )
            )
            audit.log(AuditLogger.Type.BACKUP, payload.meta.type, AuditLogger.Action.EXPORT, "${bytes.size}")
            bytes.size.toLong()
        }.onFailure {
            db.backupDao().insert(
                BackupHistoryEntity(UUID.randomUUID().toString(), System.currentTimeMillis(), "-", target.toString(), 0, if (period == null) TYPE_FULL else TYPE_PERIOD, false, it.javaClass.simpleName)
            )
        }
    }

    fun readBytes(uri: Uri): ByteArray = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)

    /** فحص النسخة ومعاينتها قبل الاستعادة دون تعديل أي بيانات. */
    fun inspect(bytes: ByteArray, password: CharArray): BackupInspectResult {
        if (!BackupCrypto.isBackup(bytes)) return BackupInspectResult.NotABackup
        val plain = BackupCrypto.decrypt(bytes, password) ?: return BackupInspectResult.WrongPasswordOrCorrupt
        val payload = try {
            BackupJson.decodeFromString(BackupPayload.serializer(), plain.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            return BackupInspectResult.WrongPasswordOrCorrupt
        }
        if (payload.meta.format > BackupFormat.FORMAT_VERSION || payload.meta.schemaVersion > AppDatabase.VERSION) {
            return BackupInspectResult.NewerVersion
        }
        val checksum = BackupCrypto.sha256(BackupJson.encodeToString(payload.tables.copy()).toByteArray())
        if (payload.meta.checksum.isNotEmpty() && payload.meta.checksum != checksum) return BackupInspectResult.ChecksumMismatch
        return BackupInspectResult.Valid(payload.meta, payload)
    }

    suspend fun inspectUri(uri: Uri, password: CharArray): BackupInspectResult = withContext(Dispatchers.IO) {
        inspect(readBytes(uri), password)
    }

    /**
     * الاستعادة:
     * 1) تُنشأ نسخة أمان للحالة الحالية داخل التطبيق (بنفس كلمة المرور).
     * 2) النسخة الكاملة تستبدل البيانات داخل Transaction واحدة؛ نسخة الفترة تُدمج.
     * إذا فشل أي شيء لا تتغير البيانات (Rollback).
     */
    suspend fun restore(payload: BackupPayload, password: CharArray): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val safety = createSafetyBackup(password)
            val tables = migrate(payload)
            db.withTransaction {
                if (payload.meta.type == TYPE_FULL) replaceAll(tables) else merge(tables)
            }
            restoreAttachmentFiles(tables)
            db.backupDao().insert(
                BackupHistoryEntity(UUID.randomUUID().toString(), System.currentTimeMillis(), "restore", "-", 0, TYPE_RESTORE, true, payload.meta.type)
            )
            audit.log(AuditLogger.Type.BACKUP, payload.meta.type, AuditLogger.Action.IMPORT, payload.meta.appVersion)
            safety
        }
    }

    /** نسخة أمان تلقائية قبل الاستعادة في مجلد خاص بالتطبيق. */
    suspend fun createSafetyBackup(password: CharArray): File {
        val dir = File(context.filesDir, "safety_backups").apply { mkdirs() }
        val file = File(dir, "before_restore_${System.currentTimeMillis()}.${BackupFormat.FILE_EXTENSION}")
        file.writeBytes(encode(buildPayload(null), password))
        // الاحتفاظ بآخر 5 نسخ فقط
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
        db.backupDao().insert(
            BackupHistoryEntity(UUID.randomUUID().toString(), System.currentTimeMillis(), file.name, file.absolutePath, file.length(), TYPE_AUTO, true)
        )
        return file
    }

    fun observeHistory() = db.backupDao().observeAll()
    fun observeLastSuccessful() = db.backupDao().observeLastSuccessful()

    fun safetyBackups(): List<File> =
        File(context.filesDir, "safety_backups").listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()

    /** التعامل مع اختلاف إصدار قاعدة البيانات: تحويل البيانات القديمة إلى البنية الحالية. */
    private fun migrate(payload: BackupPayload): BackupTables {
        var tables = payload.tables
        // الإصدار 1 هو الحالي. عند إضافة إصدارات جديدة تُضاف التحويلات هنا خطوة بخطوة:
        // if (payload.meta.schemaVersion < 2) tables = migrateV1toV2(tables)
        if (payload.meta.schemaVersion < 1) tables = tables.copy()
        return tables
    }

    private suspend fun replaceAll(t: BackupTables) {
        val m = db.maintenanceDao()
        m.clearAttachments(); m.clearPayments(); m.clearDebts(); m.clearTransfers(); m.clearSnapshots()
        m.clearBalances(); m.clearMonths(); m.clearItems(); m.clearLocations(); m.clearCategories()
        m.clearRates(); m.clearSettings(); m.clearAudit()
        db.categoryDao().insertAll(t.categories)
        db.locationDao().insertAll(t.locations)
        db.itemDao().insertAll(t.items)
        db.monthDao().insertAll(t.months)
        db.balanceDao().insertAll(t.balances)
        db.transferDao().insertAll(t.transfers)
        db.rateDao().insertAll(t.rates)
        db.snapshotDao().insertAll(t.snapshots)
        db.debtDao().insertAll(t.debts)
        db.paymentDao().insertAll(t.payments)
        db.attachmentDao().insertAll(t.attachments)
        db.settingDao().insertAll(t.settings)
        db.auditDao().insertAll(t.audit)
    }

    private suspend fun merge(t: BackupTables) {
        t.categories.forEach { db.categoryDao().upsert(it) }
        t.locations.forEach { db.locationDao().upsert(it) }
        t.items.forEach { db.itemDao().upsert(it) }
        t.months.forEach { incoming ->
            val existing = db.monthDao().byKey(incoming.monthKey)
            if (existing == null || existing.id == incoming.id) db.monthDao().upsert(incoming)
        }
        val monthIdByKey = db.monthDao().all().associate { it.monthKey to it.id }
        t.balances.forEach { b ->
            val monthId = monthIdByKey[b.monthKey] ?: return@forEach
            val existing = db.balanceDao().byMonthAndItem(monthId, b.itemId)
            db.balanceDao().upsert(b.copy(id = existing?.id ?: b.id, monthId = monthId))
        }
        t.transfers.forEach { db.transferDao().upsert(it) }
        t.rates.forEach { incoming ->
            val existing = db.rateDao().byCode(incoming.currencyCode)
            if (existing == null || existing.fetchedAt < incoming.fetchedAt) db.rateDao().upsert(incoming)
        }
        t.snapshots.forEach { s ->
            val monthId = monthIdByKey[s.monthKey] ?: return@forEach
            val existing = db.snapshotDao().get(monthId, s.currencyCode)
            db.snapshotDao().upsert(s.copy(id = existing?.id ?: s.id, monthId = monthId))
        }
        t.debts.forEach { db.debtDao().upsert(it) }
        t.payments.forEach { db.paymentDao().upsert(it) }
        t.attachments.forEach { db.attachmentDao().upsert(it) }
    }

    private suspend fun restoreAttachmentFiles(t: BackupTables) {
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val byId = t.attachments.associateBy { it.id }
        for (f in t.attachmentFiles) {
            val a = byId[f.id] ?: continue
            val target = File(dir, File(a.localPath).name)
            target.writeBytes(Base64.decode(f.base64, Base64.NO_WRAP))
            if (target.absolutePath != a.localPath) {
                db.attachmentDao().upsert(a.copy(localPath = target.absolutePath))
            }
        }
    }

    /** حذف جميع البيانات المالية (بعد تأكيد قوي في الواجهة). */
    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        db.withTransaction {
            val m = db.maintenanceDao()
            m.clearAttachments(); m.clearPayments(); m.clearDebts(); m.clearTransfers(); m.clearSnapshots()
            m.clearBalances(); m.clearMonths(); m.clearItems(); m.clearLocations(); m.clearCategories()
            m.clearRates(); m.clearSettings(); m.clearAudit(); m.clearBackupHistory()
        }
        // إعادة بناء الملف لإزالة الصفحات المحذوفة (القاعدة مشفرة أصلًا)
        runCatching { db.openHelper.writableDatabase.execSQL("VACUUM") }
        File(context.filesDir, "attachments").deleteRecursively()
        File(context.filesDir, "safety_backups").deleteRecursively()
        File(context.cacheDir, "reports").deleteRecursively()
    }

    companion object {
        const val TYPE_FULL = "FULL"
        const val TYPE_PERIOD = "PERIOD"
        const val TYPE_AUTO = "AUTO_BEFORE_RESTORE"
        const val TYPE_RESTORE = "RESTORE"
    }
}
