package com.hossammo.cash.data.repo

import androidx.room.withTransaction
import com.hossammo.cash.data.db.AppDatabase
import com.hossammo.cash.data.db.entity.LiquidityItemEntity
import com.hossammo.cash.data.db.entity.LocationEntity
import com.hossammo.cash.data.db.entity.MonthlyBalanceEntity
import com.hossammo.cash.data.db.entity.MonthlyCurrencySnapshotEntity
import com.hossammo.cash.data.db.entity.SavingsCategoryEntity
import com.hossammo.cash.data.db.entity.SavingsMonthEntity
import com.hossammo.cash.data.db.entity.TransferEntity
import com.hossammo.cash.data.model.ConfirmReason
import com.hossammo.cash.data.model.LiquidityData
import com.hossammo.cash.data.model.OpError
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.domain.calc.CurrencyConverter
import com.hossammo.cash.domain.calc.CurrentRate
import com.hossammo.cash.domain.calc.TransferCalculator
import com.hossammo.cash.domain.calc.TransferInput
import com.hossammo.cash.domain.calc.TransferValidation
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.domain.model.RateSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** معاملات حفظ عنصر سيولة من النموذج. */
data class ItemDraft(
    val id: String? = null,
    val name: String,
    val categoryId: String,
    val locationName: String?,
    val assetType: com.hossammo.cash.domain.model.AssetType,
    val currencyCode: String,
    val colorHex: String,
    val icon: String,
    val liquidityLevel: com.hossammo.cash.domain.model.LiquidityLevel,
    val includeInTotal: Boolean,
    val isVariable: Boolean,
    val notes: String?,
)

/** معاملات حفظ رصيد شهري. */
data class BalanceDraft(
    val monthKey: MonthKey,
    val itemId: String,
    val amount: BigDecimal,
    /** تثبيت السعر لهذا الشهر؟ */
    val lockRate: Boolean = false,
    /** سعر خاص بالشهر (يُستخدم عند التثبيت). إذا كان null يُستخدم آخر سعر حالي. */
    val customRate: BigDecimal? = null,
    val notes: String? = null,
)

data class TransferDraft(
    val fromItemId: String,
    val toItemId: String,
    val amount: BigDecimal,
    val fee: BigDecimal = BigDecimal.ZERO,
    val date: LocalDate,
    val reference: String? = null,
    val notes: String? = null,
    /** تطبيق التحويل على أرصدة شهر التاريخ. */
    val applyToBalances: Boolean = true,
)

/** خطأ عملية يُلغي الـ Transaction. */
class OpException(val error: OpError) : IllegalStateException(error.name)

@Singleton
class LiquidityRepository @Inject constructor(
    private val db: AppDatabase,
    private val audit: AuditLogger,
    private val appSettings: AppSettingsStore,
) {
    private val itemDao = db.itemDao()
    private val categoryDao = db.categoryDao()
    private val locationDao = db.locationDao()
    private val monthDao = db.monthDao()
    private val balanceDao = db.balanceDao()
    private val transferDao = db.transferDao()
    private val rateDao = db.rateDao()
    private val snapshotDao = db.snapshotDao()

    private fun now() = System.currentTimeMillis()
    private fun uuid() = UUID.randomUUID().toString()

    fun observeData(): Flow<LiquidityData> {
        val base = combine(
            appSettings.baseCurrency,
            categoryDao.observeActive(),
            locationDao.observeActive(),
            itemDao.observeActive(),
            monthDao.observeActive(),
        ) { b, c, l, i, m -> Base(b, c, l, i, m) }
        return combine(
            base,
            balanceDao.observeActive(),
            transferDao.observeActive(),
            rateDao.observeAll(),
            snapshotDao.observeAll(),
        ) { b, bal, tr, rates, snaps ->
            LiquidityData(b.base, b.categories, b.locations, b.items, b.months, bal, tr, rates, snaps)
        }
    }

    private data class Base(
        val base: String,
        val categories: List<SavingsCategoryEntity>,
        val locations: List<LocationEntity>,
        val items: List<LiquidityItemEntity>,
        val months: List<SavingsMonthEntity>,
    )

    fun observeDeletedItems() = itemDao.observeDeleted()
    fun observeDeletedMonths() = monthDao.observeDeleted()
    fun observeDeletedBalances() = balanceDao.observeDeleted()
    fun observeDeletedTransfers() = transferDao.observeDeleted()
    fun observeItem(id: String) = itemDao.observeById(id)

    // ------------------------------------------------------------------
    // الفئات والمواقع
    // ------------------------------------------------------------------

    suspend fun saveCategory(id: String?, name: String, colorHex: String, icon: String): OpResult {
        if (name.isBlank()) return OpResult.Failure(OpError.NAME_REQUIRED)
        val t = now()
        val existing = id?.let { categoryDao.byId(it) }
        val e = existing?.copy(name = name.trim(), colorHex = colorHex, icon = icon, updatedAt = t)
            ?: SavingsCategoryEntity(uuid(), name.trim(), null, colorHex, icon, categoryDao.maxSort() + 1, t, t)
        categoryDao.upsert(e)
        audit.log(AuditLogger.Type.CATEGORY, e.id, if (existing == null) AuditLogger.Action.CREATE else AuditLogger.Action.UPDATE, e.name)
        return OpResult.Success
    }

    suspend fun deleteCategory(id: String): OpResult {
        if (itemDao.countInCategory(id) > 0) return OpResult.Failure(OpError.CATEGORY_NOT_EMPTY)
        val c = categoryDao.byId(id) ?: return OpResult.Failure(OpError.UNKNOWN)
        categoryDao.upsert(c.copy(deletedAt = now(), updatedAt = now()))
        audit.log(AuditLogger.Type.CATEGORY, id, AuditLogger.Action.DELETE, c.name)
        return OpResult.Success
    }

    private suspend fun locationIdFor(name: String?): String? {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return null
        locationDao.byName(n)?.let { return it.id }
        val t = now()
        val e = LocationEntity(uuid(), n, null, t, t)
        locationDao.upsert(e)
        return e.id
    }

    // ------------------------------------------------------------------
    // عناصر السيولة
    // ------------------------------------------------------------------

    /** يحفظ عنصرًا جديدًا أو يعدل عنصرًا موجودًا، مع رصيد اختياري لشهر محدد. */
    suspend fun saveItem(draft: ItemDraft, initialBalance: BalanceDraft? = null): Result<String> = runCatching {
        require(draft.name.isNotBlank()) { "name" }
        db.withTransaction {
            val t = now()
            val locId = locationIdFor(draft.locationName)
            val existing = draft.id?.let { itemDao.byId(it) }
            val entity = if (existing != null) {
                existing.copy(
                    name = draft.name.trim(), categoryId = draft.categoryId, locationId = locId,
                    assetType = draft.assetType, currencyCode = draft.currencyCode.uppercase(),
                    colorHex = draft.colorHex, icon = draft.icon, liquidityLevel = draft.liquidityLevel,
                    includeInTotal = draft.includeInTotal, isVariable = draft.isVariable,
                    notes = draft.notes?.trim()?.ifEmpty { null }, updatedAt = t,
                )
            } else {
                LiquidityItemEntity(
                    id = uuid(), name = draft.name.trim(), categoryId = draft.categoryId, locationId = locId,
                    assetType = draft.assetType, currencyCode = draft.currencyCode.uppercase(), colorHex = draft.colorHex,
                    icon = draft.icon, liquidityLevel = draft.liquidityLevel, includeInTotal = draft.includeInTotal,
                    isVariable = draft.isVariable, notes = draft.notes?.trim()?.ifEmpty { null },
                    sortOrder = itemDao.maxSort() + 1, createdAt = t, updatedAt = t,
                )
            }
            itemDao.upsert(entity)
            // عند تغيير العملة لعنصر موجود: إعادة حساب أرصدة الأشهر غير المثبتة
            if (existing != null && existing.currencyCode != entity.currencyCode) {
                val base = appSettings.baseCurrency()
                db.monthDao().all().forEach { m ->
                    balanceDao.byMonthAndItem(m.id, entity.id)?.let { b ->
                        if (b.deletedAt == null) balanceDao.upsert(rebuildBalance(b.copy(currencyCode = entity.currencyCode, rateLocked = false), base, null))
                    }
                }
            }
            audit.log(AuditLogger.Type.ITEM, entity.id, if (existing == null) AuditLogger.Action.CREATE else AuditLogger.Action.UPDATE, entity.name)
            if (initialBalance != null) {
                val r = upsertBalanceInternal(initialBalance.copy(itemId = entity.id))
                if (r is OpResult.Failure) throw OpException(r.error)
            }
            entity.id
        }
    }

    /** نسخ عنصر (بدون أرصدته). */
    suspend fun duplicateItem(id: String, copySuffix: String): Result<String> = runCatching {
        val src = itemDao.byId(id) ?: error("not found")
        val t = now()
        val copy = src.copy(id = uuid(), name = "${src.name} $copySuffix".trim(), systemKey = null, sortOrder = itemDao.maxSort() + 1, createdAt = t, updatedAt = t, deletedAt = null)
        itemDao.upsert(copy)
        audit.log(AuditLogger.Type.ITEM, copy.id, AuditLogger.Action.COPY, copy.name)
        copy.id
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        val e = itemDao.byId(id) ?: return
        itemDao.upsert(e.copy(isArchived = archived, updatedAt = now()))
        audit.log(AuditLogger.Type.ITEM, id, if (archived) AuditLogger.Action.ARCHIVE else AuditLogger.Action.UNARCHIVE, e.name)
    }

    suspend fun softDeleteItem(id: String) {
        val e = itemDao.byId(id) ?: return
        itemDao.upsert(e.copy(deletedAt = now(), updatedAt = now()))
        audit.log(AuditLogger.Type.ITEM, id, AuditLogger.Action.DELETE, e.name)
    }

    suspend fun restoreItem(id: String) {
        val e = itemDao.byId(id) ?: return
        itemDao.upsert(e.copy(deletedAt = null, updatedAt = now()))
        audit.log(AuditLogger.Type.ITEM, id, AuditLogger.Action.RESTORE, e.name)
    }

    /** حذف نهائي من سلة المحذوفات (يحذف أرصدته وتحويلاته). */
    suspend fun purgeItem(id: String) {
        val e = itemDao.byId(id) ?: return
        itemDao.hardDelete(id)
        audit.log(AuditLogger.Type.ITEM, id, AuditLogger.Action.PURGE, e.name)
    }

    // ------------------------------------------------------------------
    // الأشهر
    // ------------------------------------------------------------------

    /**
     * إنشاء شهر جديد. إذا كان [copyPrevious] = true تُنسخ أرصدة آخر شهر سابق كبداية.
     * الأسعار غير المثبتة تُحسب بآخر سعر حالي، والمثبتة تُنسخ كما هي.
     */
    suspend fun createMonth(key: MonthKey, copyPrevious: Boolean, restoreIfDeleted: Boolean = false): OpResult = db.withTransaction {
        val existing = monthDao.byKey(key.value)
        if (existing != null && existing.deletedAt == null) return@withTransaction OpResult.Failure(OpError.MONTH_EXISTS)
        if (existing != null && !restoreIfDeleted) return@withTransaction OpResult.NeedsConfirmation(ConfirmReason.MONTH_DELETED_EXISTS)
        val t = now()
        val month = existing?.copy(deletedAt = null, updatedAt = t)
            ?: SavingsMonthEntity(uuid(), key.value, key.year, key.month, null, t, t)
        monthDao.upsert(month)
        audit.log(AuditLogger.Type.MONTH, month.id, if (existing != null) AuditLogger.Action.RESTORE else AuditLogger.Action.CREATE, key.toString())
        if (copyPrevious && existing == null) {
            val prev = monthDao.previousActive(key.value)
                ?: return@withTransaction OpResult.Success
            copyBalances(prev, month)
        }
        OpResult.Success
    }

    /** نسخ أرصدة الشهر السابق إلى شهر موجود (للعناصر التي ليس لها رصيد فيه بعد). */
    suspend fun copyPreviousInto(key: MonthKey): OpResult = db.withTransaction {
        val month = monthDao.byKey(key.value)?.takeIf { it.deletedAt == null }
            ?: return@withTransaction OpResult.Failure(OpError.MONTH_NOT_FOUND)
        val prev = monthDao.previousActive(key.value) ?: return@withTransaction OpResult.Failure(OpError.NO_PREVIOUS_MONTH)
        copyBalances(prev, month)
        OpResult.Success
    }

    private suspend fun copyBalances(from: SavingsMonthEntity, to: SavingsMonthEntity) {
        val base = appSettings.baseCurrency()
        val t = now()
        val items = itemDao.all().associateBy { it.id }
        for (b in balanceDao.activeForMonth(from.id)) {
            val item = items[b.itemId] ?: continue
            if (item.deletedAt != null || item.isArchived) continue
            val existing = balanceDao.byMonthAndItem(to.id, b.itemId)
            if (existing != null && existing.deletedAt == null) continue
            val draft = MonthlyBalanceEntity(
                id = existing?.id ?: uuid(), monthId = to.id, monthKey = to.monthKey, itemId = b.itemId,
                amountOriginal = b.amountOriginal, currencyCode = item.currencyCode,
                exchangeRate = null, valueBase = null, rateSource = RateSource.SAVED,
                rateLocked = false, notes = null, createdAt = existing?.createdAt ?: t, updatedAt = t,
            )
            balanceDao.upsert(rebuildBalance(draft, base, snapshotDao.get(to.id, item.currencyCode)?.rate))
        }
        audit.log(AuditLogger.Type.MONTH, to.id, AuditLogger.Action.COPY, "${MonthKey(from.monthKey)} → ${MonthKey(to.monthKey)}")
    }

    suspend fun softDeleteMonth(key: MonthKey) {
        val m = monthDao.byKey(key.value) ?: return
        monthDao.upsert(m.copy(deletedAt = now(), updatedAt = now()))
        audit.log(AuditLogger.Type.MONTH, m.id, AuditLogger.Action.DELETE, key.toString())
    }

    suspend fun restoreMonth(id: String) {
        val m = monthDao.byId(id) ?: return
        monthDao.upsert(m.copy(deletedAt = null, updatedAt = now()))
        audit.log(AuditLogger.Type.MONTH, m.id, AuditLogger.Action.RESTORE, MonthKey(m.monthKey).toString())
    }

    suspend fun purgeMonth(id: String) {
        monthDao.hardDelete(id)
        audit.log(AuditLogger.Type.MONTH, id, AuditLogger.Action.PURGE, "")
    }

    // ------------------------------------------------------------------
    // الأرصدة الشهرية
    // ------------------------------------------------------------------

    /** هل يوجد رصيد نشط لهذا العنصر في هذا الشهر؟ (لتنبيه التكرار) */
    suspend fun hasBalance(key: MonthKey, itemId: String): Boolean {
        val m = monthDao.byKey(key.value) ?: return false
        if (m.deletedAt != null) return false
        val b = balanceDao.byMonthAndItem(m.id, itemId) ?: return false
        return b.deletedAt == null
    }

    /**
     * حفظ رصيد شهري. إذا وُجد رصيد لنفس العنصر والشهر ولم يؤكد المستخدم ([confirmOverwrite]=false)
     * تُعاد NeedsConfirmation(DUPLICATE_BALANCE).
     */
    suspend fun saveBalance(draft: BalanceDraft, confirmOverwrite: Boolean, isEdit: Boolean = false): OpResult {
        if (draft.amount.signum() < 0) return OpResult.Failure(OpError.INVALID_AMOUNT)
        return db.withTransaction {
            val item = itemDao.byId(draft.itemId) ?: return@withTransaction OpResult.Failure(OpError.ITEM_NOT_FOUND)
            if (!isEdit && !confirmOverwrite && hasBalance(draft.monthKey, item.id)) {
                return@withTransaction OpResult.NeedsConfirmation(ConfirmReason.DUPLICATE_BALANCE)
            }
            upsertBalanceInternal(draft)
        }
    }

    private suspend fun ensureMonth(key: MonthKey): SavingsMonthEntity {
        val existing = monthDao.byKey(key.value)
        if (existing != null) {
            if (existing.deletedAt != null) {
                val revived = existing.copy(deletedAt = null, updatedAt = now())
                monthDao.upsert(revived)
                return revived
            }
            return existing
        }
        val t = now()
        val m = SavingsMonthEntity(uuid(), key.value, key.year, key.month, null, t, t)
        monthDao.upsert(m)
        audit.log(AuditLogger.Type.MONTH, m.id, AuditLogger.Action.CREATE, key.toString())
        return m
    }

    private suspend fun upsertBalanceInternal(draft: BalanceDraft): OpResult {
        val item = itemDao.byId(draft.itemId) ?: return OpResult.Failure(OpError.ITEM_NOT_FOUND)
        val base = appSettings.baseCurrency()
        val month = ensureMonth(draft.monthKey)
        val t = now()
        val existing = balanceDao.byMonthAndItem(month.id, item.id)
        val isForeign = !item.currencyCode.equals(base, true)
        var lockedRate: BigDecimal? = null
        if (isForeign && draft.lockRate) {
            lockedRate = draft.customRate ?: snapshotDao.get(month.id, item.currencyCode)?.rate ?: rateDao.byCode(item.currencyCode)?.rateToBase
            if (lockedRate == null || lockedRate.signum() <= 0) return OpResult.Failure(OpError.MISSING_RATE)
            snapshotDao.upsert(
                MonthlyCurrencySnapshotEntity(
                    id = snapshotDao.get(month.id, item.currencyCode)?.id ?: uuid(), monthId = month.id,
                    monthKey = month.monthKey, currencyCode = item.currencyCode, rate = Money.rate(lockedRate),
                    source = if (draft.customRate != null) "manual" else "current", createdAt = t, updatedAt = t,
                )
            )
        }
        val entity = MonthlyBalanceEntity(
            id = existing?.id ?: uuid(), monthId = month.id, monthKey = month.monthKey, itemId = item.id,
            amountOriginal = Money.money(draft.amount), currencyCode = item.currencyCode,
            exchangeRate = null, valueBase = null, rateSource = RateSource.SAVED,
            rateLocked = isForeign && draft.lockRate, notes = draft.notes?.trim()?.ifEmpty { null },
            createdAt = existing?.createdAt ?: t, updatedAt = t, deletedAt = null,
        )
        val rebuilt = rebuildBalance(entity, base, lockedRate)
        if (isForeign && rebuilt.exchangeRate == null) return OpResult.Failure(OpError.MISSING_RATE)
        balanceDao.upsert(rebuilt)
        audit.log(
            AuditLogger.Type.BALANCE, rebuilt.id,
            if (existing == null) AuditLogger.Action.CREATE else AuditLogger.Action.UPDATE,
            "${item.name} • ${draft.monthKey}",
        )
        return OpResult.Success
    }

    /** يعيد حساب سعر الصرف والقيمة بالعملة الأساسية لرصيد. */
    private suspend fun rebuildBalance(b: MonthlyBalanceEntity, base: String, lockedRate: BigDecimal?): MonthlyBalanceEntity {
        val current = rateDao.byCode(b.currencyCode)?.let {
            CurrentRate(it.rateToBase, it.isManual, isFresh = now() - it.fetchedAt < FRESH_WINDOW_MS)
        }
        val effectiveLocked = b.rateLocked || lockedRate != null
        val resolved = CurrencyConverter.resolveRate(
            currency = b.currencyCode, baseCurrency = base, locked = effectiveLocked,
            lockedRate = lockedRate ?: if (b.rateLocked) b.exchangeRate else null, current = current,
        )
        val value = CurrencyConverter.toBase(b.amountOriginal, b.currencyCode, base, resolved?.rate)
        return b.copy(
            exchangeRate = resolved?.rate, valueBase = value,
            rateSource = resolved?.source ?: RateSource.SAVED,
            rateLocked = resolved?.source == RateSource.LOCKED,
        )
    }

    suspend fun softDeleteBalance(id: String) {
        val b = balanceDao.byId(id) ?: return
        balanceDao.upsert(b.copy(deletedAt = now(), updatedAt = now()))
        audit.log(AuditLogger.Type.BALANCE, id, AuditLogger.Action.DELETE, MonthKey(b.monthKey).toString())
    }

    suspend fun restoreBalance(id: String) {
        val b = balanceDao.byId(id) ?: return
        val m = monthDao.byId(b.monthId)
        if (m?.deletedAt != null) monthDao.upsert(m.copy(deletedAt = null, updatedAt = now()))
        balanceDao.upsert(b.copy(deletedAt = null, updatedAt = now()))
        audit.log(AuditLogger.Type.BALANCE, id, AuditLogger.Action.RESTORE, MonthKey(b.monthKey).toString())
    }

    suspend fun purgeBalance(id: String) {
        balanceDao.hardDelete(id)
        audit.log(AuditLogger.Type.BALANCE, id, AuditLogger.Action.PURGE, "")
    }

    /** تثبيت سعر عملة لشهر: يُحفظ داخل سجل الشهر ولا يتغير مع تحديث الأسعار. */
    suspend fun lockRate(key: MonthKey, currency: String, rate: BigDecimal?): OpResult = db.withTransaction {
        val month = monthDao.byKey(key.value) ?: return@withTransaction OpResult.Failure(OpError.MONTH_NOT_FOUND)
        val r = rate ?: rateDao.byCode(currency)?.rateToBase ?: return@withTransaction OpResult.Failure(OpError.MISSING_RATE)
        if (r.signum() <= 0) return@withTransaction OpResult.Failure(OpError.INVALID_AMOUNT)
        val t = now()
        snapshotDao.upsert(
            MonthlyCurrencySnapshotEntity(
                snapshotDao.get(month.id, currency)?.id ?: uuid(), month.id, month.monthKey, currency,
                Money.rate(r), if (rate != null) "manual" else "current", t, t,
            )
        )
        val base = appSettings.baseCurrency()
        for (b in balanceDao.forMonthAndCurrency(month.id, currency)) {
            balanceDao.upsert(rebuildBalance(b.copy(rateLocked = true, updatedAt = t), base, Money.rate(r)))
        }
        audit.log(AuditLogger.Type.RATE, month.id, AuditLogger.Action.LOCK, "$currency • $key")
        OpResult.Success
    }

    /** فك تثبيت السعر (بعد تأكيد المستخدم في الواجهة). */
    suspend fun unlockRate(key: MonthKey, currency: String): OpResult = db.withTransaction {
        val month = monthDao.byKey(key.value) ?: return@withTransaction OpResult.Failure(OpError.MONTH_NOT_FOUND)
        snapshotDao.delete(month.id, currency)
        val base = appSettings.baseCurrency()
        val t = now()
        for (b in balanceDao.forMonthAndCurrency(month.id, currency)) {
            balanceDao.upsert(rebuildBalance(b.copy(rateLocked = false, updatedAt = t), base, null))
        }
        audit.log(AuditLogger.Type.RATE, month.id, AuditLogger.Action.UNLOCK, "$currency • $key")
        OpResult.Success
    }

    /** بعد تحديث سعر عملة: إعادة حساب كل الأرصدة غير المثبتة لهذه العملة. الأرصدة المثبتة لا تتغير. */
    suspend fun recalcUnlocked(currency: String) = db.withTransaction {
        val base = appSettings.baseCurrency()
        for (b in balanceDao.unlockedForCurrency(currency)) {
            balanceDao.upsert(rebuildBalance(b, base, null))
        }
    }

    // ------------------------------------------------------------------
    // التحويلات الداخلية
    // ------------------------------------------------------------------

    /**
     * إضافة تحويل داخلي. يخفض رصيد المرسل ويزيد رصيد المستلم في شهر التحويل،
     * فلا يتغير إجمالي السيولة (إلا بقيمة الرسوم إذا كانت تُخصم).
     */
    suspend fun addTransfer(draft: TransferDraft, confirmOverdraft: Boolean): OpResult = db.withTransaction {
        if (draft.fromItemId == draft.toItemId) return@withTransaction OpResult.Failure(OpError.SAME_ITEM)
        if (draft.amount.signum() <= 0 || draft.fee.signum() < 0) return@withTransaction OpResult.Failure(OpError.INVALID_AMOUNT)
        val from = itemDao.byId(draft.fromItemId) ?: return@withTransaction OpResult.Failure(OpError.ITEM_NOT_FOUND)
        val to = itemDao.byId(draft.toItemId) ?: return@withTransaction OpResult.Failure(OpError.ITEM_NOT_FOUND)
        val key = MonthKey.of(draft.date.year, draft.date.monthValue)
        val deductFee = appSettings.deductTransferFees()
        val base = appSettings.baseCurrency()

        val received = if (from.currencyCode == to.currencyCode) draft.amount else {
            val fromRate = rateFor(from.currencyCode, base, key) ?: return@withTransaction OpResult.Failure(OpError.MISSING_RATE)
            val toRate = rateFor(to.currencyCode, base, key) ?: return@withTransaction OpResult.Failure(OpError.MISSING_RATE)
            CurrencyConverter.convert(draft.amount, fromRate, toRate)
        }
        val input = TransferInput(from.id, to.id, draft.amount, received, draft.fee, deductFee)

        if (draft.applyToBalances) {
            val month = monthDao.byKey(key.value)?.takeIf { it.deletedAt == null }
            val senderBalance = month?.let { balanceDao.byMonthAndItem(it.id, from.id) }?.takeIf { it.deletedAt == null }?.amountOriginal
            when (val v = TransferCalculator.validate(input, senderBalance)) {
                is TransferValidation.ExceedsBalance -> if (!confirmOverdraft) {
                    return@withTransaction OpResult.NeedsConfirmation(ConfirmReason.EXCEEDS_BALANCE)
                }
                TransferValidation.SameItem -> return@withTransaction OpResult.Failure(OpError.SAME_ITEM)
                TransferValidation.NonPositiveAmount, TransferValidation.NegativeFee -> return@withTransaction OpResult.Failure(OpError.INVALID_AMOUNT)
                TransferValidation.Ok -> Unit
            }
            applyTransferToBalances(input, key, reverse = false)
        }
        val t = now()
        val e = TransferEntity(
            id = uuid(), fromItemId = from.id, toItemId = to.id, amount = Money.money(draft.amount),
            currencyCode = from.currencyCode, receivedAmount = Money.money(received), fee = Money.money(draft.fee),
            deductFee = deductFee, transferDate = draft.date.toEpochDay(), monthKey = key.value,
            appliedToBalances = draft.applyToBalances, reference = draft.reference?.trim()?.ifEmpty { null },
            notes = draft.notes?.trim()?.ifEmpty { null }, createdAt = t, updatedAt = t,
        )
        transferDao.upsert(e)
        audit.log(AuditLogger.Type.TRANSFER, e.id, AuditLogger.Action.CREATE, "${from.name} → ${to.name}")
        OpResult.Success
    }

    private suspend fun rateFor(currency: String, base: String, key: MonthKey): BigDecimal? {
        if (currency.equals(base, true)) return BigDecimal.ONE
        val month = monthDao.byKey(key.value)
        month?.let { snapshotDao.get(it.id, currency)?.rate }?.let { return it }
        return rateDao.byCode(currency)?.rateToBase
    }

    private suspend fun applyTransferToBalances(input: TransferInput, key: MonthKey, reverse: Boolean) {
        val month = ensureMonth(key)
        val base = appSettings.baseCurrency()
        val t = now()
        val ids = listOf(input.fromItemId, input.toItemId)
        val current = ids.associateWith { id ->
            balanceDao.byMonthAndItem(month.id, id)?.takeIf { it.deletedAt == null }?.amountOriginal ?: BigDecimal.ZERO
        }
        val updated = if (!reverse) TransferCalculator.apply(current, input) else {
            // عكس التحويل: إعادة المبلغ والرسوم للمرسل وخصم المبلغ المستلم من المستلم
            val fee = if (input.deductFee) input.fee else BigDecimal.ZERO
            mapOf(
                input.fromItemId to Money.money(current.getValue(input.fromItemId) + input.amount + fee),
                input.toItemId to Money.money(current.getValue(input.toItemId) - input.receivedAmount),
            )
        }
        for (id in ids) {
            val item = itemDao.byId(id) ?: continue
            val existing = balanceDao.byMonthAndItem(month.id, id)
            val lockedRate = if (existing?.rateLocked == true) existing.exchangeRate else null
            val entity = MonthlyBalanceEntity(
                id = existing?.id ?: uuid(), monthId = month.id, monthKey = month.monthKey, itemId = id,
                amountOriginal = updated[id] ?: BigDecimal.ZERO, currencyCode = item.currencyCode,
                exchangeRate = existing?.exchangeRate, valueBase = null, rateSource = existing?.rateSource ?: RateSource.SAVED,
                rateLocked = existing?.rateLocked ?: false, notes = existing?.notes,
                createdAt = existing?.createdAt ?: t, updatedAt = t, deletedAt = null,
            )
            balanceDao.upsert(rebuildBalance(entity, base, lockedRate))
        }
    }

    /** حذف تحويل (Soft) مع عكس أثره على الأرصدة إذا كان مطبقًا. */
    suspend fun softDeleteTransfer(id: String) = db.withTransaction {
        val tr = transferDao.byId(id) ?: return@withTransaction
        if (tr.deletedAt != null) return@withTransaction
        if (tr.appliedToBalances) applyTransferToBalances(tr.toInput(), MonthKey(tr.monthKey), reverse = true)
        transferDao.upsert(tr.copy(deletedAt = now(), updatedAt = now()))
        audit.log(AuditLogger.Type.TRANSFER, id, AuditLogger.Action.DELETE, "")
    }

    suspend fun restoreTransfer(id: String) = db.withTransaction {
        val tr = transferDao.byId(id) ?: return@withTransaction
        if (tr.deletedAt == null) return@withTransaction
        if (tr.appliedToBalances) applyTransferToBalances(tr.toInput(), MonthKey(tr.monthKey), reverse = false)
        transferDao.upsert(tr.copy(deletedAt = null, updatedAt = now()))
        audit.log(AuditLogger.Type.TRANSFER, id, AuditLogger.Action.RESTORE, "")
    }

    suspend fun purgeTransfer(id: String) {
        transferDao.hardDelete(id)
        audit.log(AuditLogger.Type.TRANSFER, id, AuditLogger.Action.PURGE, "")
    }

    private fun TransferEntity.toInput() = TransferInput(fromItemId, toItemId, amount, receivedAmount, fee, deductFee)

    companion object {
        /** السعر يعتبر «تلقائي حديث» إذا جُلب خلال آخر 36 ساعة، وإلا «محفوظ». */
        const val FRESH_WINDOW_MS = 36L * 60 * 60 * 1000

        fun epochDayToLocal(day: Long): LocalDate = LocalDate.ofEpochDay(day)
        fun millisToLocal(ms: Long): LocalDate = java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
