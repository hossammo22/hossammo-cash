package com.hossammo.cash.domain.calc

import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.domain.model.RateSource
import java.math.BigDecimal

/** آخر سعر معروف لعملة مقابل العملة الأساسية. */
data class CurrentRate(
    val rate: BigDecimal,
    val isManual: Boolean,
    /** هل جاء من تحديث ناجح حديث (true) أم أنه آخر سعر محفوظ يُستخدم دون اتصال (false). */
    val isFresh: Boolean,
)

data class ResolvedRate(val rate: BigDecimal, val source: RateSource)

/**
 * تحويل العملات إلى العملة الأساسية (الجنيه المصري افتراضيًا).
 */
object CurrencyConverter {

    /**
     * يحدد سعر الصرف الواجب استخدامه لرصيد شهري.
     * الأولوية: السعر المثبت للشهر ← آخر سعر حالي (يدوي/تلقائي/محفوظ).
     * يعيد null إذا كانت العملة أجنبية ولا يوجد أي سعر.
     */
    fun resolveRate(
        currency: String,
        baseCurrency: String,
        locked: Boolean,
        lockedRate: BigDecimal?,
        current: CurrentRate?,
    ): ResolvedRate? {
        if (currency.equals(baseCurrency, ignoreCase = true)) return ResolvedRate(BigDecimal.ONE, RateSource.BASE)
        if (locked && lockedRate != null && lockedRate.signum() > 0) {
            return ResolvedRate(Money.rate(lockedRate), RateSource.LOCKED)
        }
        if (current == null || current.rate.signum() <= 0) return null
        val source = when {
            current.isManual -> RateSource.MANUAL
            current.isFresh -> RateSource.AUTO
            else -> RateSource.SAVED
        }
        return ResolvedRate(Money.rate(current.rate), source)
    }

    /** القيمة بالعملة الأساسية = الرصيد × سعر الصرف (أو الرصيد نفسه للعملة الأساسية). */
    fun toBase(amount: BigDecimal, currency: String, baseCurrency: String, rate: BigDecimal?): BigDecimal? {
        if (currency.equals(baseCurrency, ignoreCase = true)) return Money.money(amount)
        if (rate == null || rate.signum() <= 0) return null
        return Money.money(amount.multiply(rate))
    }

    /** تحويل مبلغ من عملة إلى أخرى عبر العملة الأساسية. */
    fun convert(amount: BigDecimal, fromRateToBase: BigDecimal, toRateToBase: BigDecimal): BigDecimal {
        require(toRateToBase.signum() > 0) { "Target rate must be positive" }
        return Money.money(amount.multiply(fromRateToBase).divide(toRateToBase, 10, Money.ROUNDING))
    }
}
