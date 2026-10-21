package com.hossammo.cash.data.model

import com.hossammo.cash.data.db.entity.CurrencyRateEntity
import com.hossammo.cash.data.db.entity.DebtEntity
import com.hossammo.cash.data.db.entity.DebtPaymentEntity
import com.hossammo.cash.data.db.entity.LiquidityItemEntity
import com.hossammo.cash.data.db.entity.LocationEntity
import com.hossammo.cash.data.db.entity.MonthlyBalanceEntity
import com.hossammo.cash.data.db.entity.MonthlyCurrencySnapshotEntity
import com.hossammo.cash.data.db.entity.SavingsCategoryEntity
import com.hossammo.cash.data.db.entity.SavingsMonthEntity
import com.hossammo.cash.data.db.entity.TransferEntity

/** كل بيانات السيولة الخام اللازمة للعرض والتحليل. */
data class LiquidityData(
    val baseCurrency: String,
    val categories: List<SavingsCategoryEntity>,
    val locations: List<LocationEntity>,
    val items: List<LiquidityItemEntity>,
    val months: List<SavingsMonthEntity>,
    val balances: List<MonthlyBalanceEntity>,
    val transfers: List<TransferEntity>,
    val rates: List<CurrencyRateEntity>,
    val snapshots: List<MonthlyCurrencySnapshotEntity>,
) {
    val categoryById by lazy { categories.associateBy { it.id } }
    val locationById by lazy { locations.associateBy { it.id } }
    val itemById by lazy { items.associateBy { it.id } }
    val rateByCode by lazy { rates.associateBy { it.currencyCode } }

    companion object {
        val EMPTY = LiquidityData("EGP", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

data class DebtWithPayments(val debt: DebtEntity, val payments: List<DebtPaymentEntity>)

/** نتيجة عملية قد تحتاج تأكيدًا من المستخدم. */
sealed interface OpResult {
    data object Success : OpResult
    data class NeedsConfirmation(val reason: ConfirmReason) : OpResult
    data class Failure(val error: OpError) : OpResult
}

enum class ConfirmReason { DUPLICATE_BALANCE, EXCEEDS_BALANCE, PAYMENT_EXCEEDS_ORIGINAL, MONTH_DELETED_EXISTS }

enum class OpError {
    INVALID_AMOUNT, SAME_ITEM, MONTH_EXISTS, MONTH_NOT_FOUND, ITEM_NOT_FOUND, MISSING_RATE,
    CATEGORY_NOT_EMPTY, NAME_REQUIRED, NO_PREVIOUS_MONTH, UNKNOWN
}
