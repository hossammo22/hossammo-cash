package com.hossammo.cash.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.hossammo.cash.data.db.AppDatabase
import com.hossammo.cash.data.db.entity.AttachmentEntity
import com.hossammo.cash.data.db.entity.DebtEntity
import com.hossammo.cash.data.db.entity.DebtPaymentEntity
import com.hossammo.cash.data.model.ConfirmReason
import com.hossammo.cash.data.model.DebtWithPayments
import com.hossammo.cash.data.model.OpError
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.domain.calc.DebtCalculator
import com.hossammo.cash.domain.model.DebtDirection
import com.hossammo.cash.domain.model.Money
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DebtDraft(
    val id: String? = null,
    val name: String,
    val direction: DebtDirection,
    val originalAmount: BigDecimal?,
    val startDate: LocalDate?,
    val dueDate: LocalDate?,
    val notes: String?,
)

data class PaymentDraft(
    val id: String? = null,
    val debtId: String,
    val amount: BigDecimal,
    val date: LocalDate,
    val method: String?,
    val reference: String?,
    val notes: String?,
)

/**
 * مستودع الديون — منفصل تمامًا عن السيولة. لا توجد أي دالة هنا تؤثر على أرصدة السيولة أو إجمالياتها.
 */
@Singleton
class DebtRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val audit: AuditLogger,
) {
    private val debtDao = db.debtDao()
    private val paymentDao = db.paymentDao()
    private val attachmentDao = db.attachmentDao()
    private fun now() = System.currentTimeMillis()
    private fun uuid() = UUID.randomUUID().toString()

    fun observeDebts(): Flow<List<DebtWithPayments>> =
        combine(debtDao.observeActive(), paymentDao.observeActive()) { debts, payments ->
            val byDebt = payments.groupBy { it.debtId }
            debts.map { DebtWithPayments(it, byDebt[it.id].orEmpty()) }
        }

    suspend fun debtById(id: String): DebtEntity? = debtDao.byId(id)

    fun observeDeletedDebts() = debtDao.observeDeleted()
    fun observeDeletedPayments() = paymentDao.observeDeleted()
    fun observeAttachments(ownerType: String, ownerId: String) = attachmentDao.observeFor(ownerType, ownerId)

    suspend fun saveDebt(d: DebtDraft): OpResult {
        if (d.name.isBlank()) return OpResult.Failure(OpError.NAME_REQUIRED)
        if (d.originalAmount != null && d.originalAmount.signum() < 0) return OpResult.Failure(OpError.INVALID_AMOUNT)
        val t = now()
        val existing = d.id?.let { debtDao.byId(it) }
        val e = existing?.copy(
            name = d.name.trim(), direction = d.direction, originalAmount = d.originalAmount?.let(Money::money),
            startDate = d.startDate?.toEpochDay(), dueDate = d.dueDate?.toEpochDay(),
            notes = d.notes?.trim()?.ifEmpty { null }, updatedAt = t,
        ) ?: DebtEntity(
            id = uuid(), name = d.name.trim(), direction = d.direction, originalAmount = d.originalAmount?.let(Money::money),
            startDate = d.startDate?.toEpochDay(), dueDate = d.dueDate?.toEpochDay(), notes = d.notes?.trim()?.ifEmpty { null },
            sortOrder = debtDao.maxSort() + 1, createdAt = t, updatedAt = t,
        )
        debtDao.upsert(e)
        audit.log(AuditLogger.Type.DEBT, e.id, if (existing == null) AuditLogger.Action.CREATE else AuditLogger.Action.UPDATE, e.name)
        return OpResult.Success
    }

    suspend fun softDeleteDebt(id: String) {
        val d = debtDao.byId(id) ?: return
        debtDao.upsert(d.copy(deletedAt = now(), updatedAt = now()))
        audit.log(AuditLogger.Type.DEBT, id, AuditLogger.Action.DELETE, d.name)
    }

    suspend fun restoreDebt(id: String) {
        val d = debtDao.byId(id) ?: return
        debtDao.upsert(d.copy(deletedAt = null, updatedAt = now()))
        audit.log(AuditLogger.Type.DEBT, id, AuditLogger.Action.RESTORE, d.name)
    }

    suspend fun purgeDebt(id: String) {
        debtDao.hardDelete(id)
        audit.log(AuditLogger.Type.DEBT, id, AuditLogger.Action.PURGE, "")
    }

    /**
     * إضافة/تعديل دفعة. إذا تجاوز مجموع الدفعات المبلغ الأصلي تُطلب موافقة صريحة.
     */
    suspend fun savePayment(p: PaymentDraft, confirmOverpay: Boolean): OpResult = db.withTransaction {
        if (p.amount.signum() <= 0) return@withTransaction OpResult.Failure(OpError.INVALID_AMOUNT)
        val debt = debtDao.byId(p.debtId) ?: return@withTransaction OpResult.Failure(OpError.UNKNOWN)
        val others = paymentDao.activeForDebt(p.debtId).filter { it.id != p.id }.map { it.amount }
        val check = DebtCalculator.checkPayment(debt.originalAmount, others, p.amount)
        if (check.exceedsOriginal && !confirmOverpay) {
            return@withTransaction OpResult.NeedsConfirmation(ConfirmReason.PAYMENT_EXCEEDS_ORIGINAL)
        }
        val t = now()
        val existing = p.id?.let { paymentDao.byId(it) }
        val e = DebtPaymentEntity(
            id = existing?.id ?: uuid(), debtId = p.debtId, amount = Money.money(p.amount),
            paymentDate = p.date.toEpochDay(), method = p.method?.trim()?.ifEmpty { null },
            reference = p.reference?.trim()?.ifEmpty { null }, notes = p.notes?.trim()?.ifEmpty { null },
            createdAt = existing?.createdAt ?: t, updatedAt = t,
        )
        paymentDao.upsert(e)
        debtDao.upsert(debt.copy(updatedAt = t))
        audit.log(AuditLogger.Type.PAYMENT, e.id, if (existing == null) AuditLogger.Action.CREATE else AuditLogger.Action.UPDATE, debt.name)
        OpResult.Success
    }

    suspend fun softDeletePayment(id: String) {
        val p = paymentDao.byId(id) ?: return
        paymentDao.upsert(p.copy(deletedAt = now(), updatedAt = now()))
        audit.log(AuditLogger.Type.PAYMENT, id, AuditLogger.Action.DELETE, "")
    }

    suspend fun restorePayment(id: String) {
        val p = paymentDao.byId(id) ?: return
        paymentDao.upsert(p.copy(deletedAt = null, updatedAt = now()))
        audit.log(AuditLogger.Type.PAYMENT, id, AuditLogger.Action.RESTORE, "")
    }

    suspend fun purgePayment(id: String) {
        paymentDao.hardDelete(id)
        audit.log(AuditLogger.Type.PAYMENT, id, AuditLogger.Action.PURGE, "")
    }

    // ---------- المرفقات المحلية ----------

    /** ينسخ الملف المختار إلى مجلد خاص بالتطبيق (لا يُرفع لأي مكان). */
    suspend fun addAttachment(ownerType: String, ownerId: String, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var name = "attachment"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
            val id = uuid()
            val safeName = name.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").take(80)
            val target = File(dir, "${id}_$safeName")
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: error("cannot open")
            attachmentDao.upsert(
                AttachmentEntity(id, ownerType, ownerId, name, resolver.getType(uri), target.absolutePath, target.length(), now())
            )
            audit.log(AuditLogger.Type.ATTACHMENT, id, AuditLogger.Action.CREATE, name)
        }
    }

    suspend fun deleteAttachment(id: String) = withContext(Dispatchers.IO) {
        val a = attachmentDao.byId(id) ?: return@withContext
        File(a.localPath).delete()
        attachmentDao.hardDelete(id)
        audit.log(AuditLogger.Type.ATTACHMENT, id, AuditLogger.Action.DELETE, a.fileName)
    }

    companion object {
        const val OWNER_DEBT = "DEBT"
        const val OWNER_PAYMENT = "PAYMENT"
    }
}
