package com.hossammo.cash.domain.calc

import com.hossammo.cash.domain.model.LiquidityLevel
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.domain.model.TrendStatus
import com.hossammo.cash.domain.model.sumMoney
import java.math.BigDecimal

/** قيمة عنصر واحد في شهر واحد بعد التحويل للعملة الأساسية. */
data class ItemMonthValue(
    val itemId: String,
    val valueBase: BigDecimal,
    val includeInTotal: Boolean = true,
    val liquidityLevel: LiquidityLevel = LiquidityLevel.UNSPECIFIED,
)

/** بيانات شهر كامل. */
data class MonthData(val month: MonthKey, val values: List<ItemMonthValue>)

/** نتيجة مقارنة قيمة حالية بقيمة سابقة. */
data class ChangeResult(
    val current: BigDecimal,
    val previous: BigDecimal?,
    /** null عندما لا توجد قيمة سابقة. */
    val change: BigDecimal?,
    /** null عندما تكون المقارنة غير متاحة (لا يوجد سابق أو السابق صفر). */
    val percent: BigDecimal?,
    val status: TrendStatus,
) {
    val isComparable: Boolean get() = percent != null
}

data class ItemComparison(
    val itemId: String,
    val change: ChangeResult,
    /** نسبة العنصر من إجمالي السيولة، null إذا كان الإجمالي صفرًا أو العنصر غير محتسب. */
    val shareOfTotal: BigDecimal?,
    val includeInTotal: Boolean,
)

data class MonthTotal(val month: MonthKey, val total: BigDecimal, val change: ChangeResult)

data class Share(val key: String, val value: BigDecimal, val percent: BigDecimal?)

data class PeriodStats(
    val firstMonth: MonthTotal?,
    val lastMonth: MonthTotal?,
    val highest: MonthTotal?,
    val lowest: MonthTotal?,
    val average: BigDecimal?,
    /** الشهر صاحب أكبر زيادة موجبة. */
    val bestMonth: MonthTotal?,
    /** الشهر صاحب أكبر تراجع. */
    val worstMonth: MonthTotal?,
    val changeSinceFirst: ChangeResult?,
)

/**
 * طبقة الحسابات المالية الأساسية. كل الدوال نقية (Pure) وقابلة للاختبار.
 */
object FinanceCalculator {

    /** يزيل التكرار لنفس العنصر حتى لا يُحتسب مرتين (يُحتفظ بأول قيمة). */
    fun distinctItems(values: List<ItemMonthValue>): List<ItemMonthValue> = values.distinctBy { it.itemId }

    /** إجمالي سيولة الشهر = مجموع القيم بالجنيه للعناصر التي تدخل ضمن الإجمالي. */
    fun monthTotal(values: List<ItemMonthValue>): BigDecimal =
        Money.money(distinctItems(values).filter { it.includeInTotal }.map { it.valueBase }.sumMoney())

    fun monthTotal(month: MonthData): BigDecimal = monthTotal(month.values)

    /** التغير ونسبة التغير والحالة. */
    fun change(current: BigDecimal, previous: BigDecimal?): ChangeResult {
        val cur = Money.money(current)
        if (previous == null) {
            return ChangeResult(cur, null, null, null, TrendStatus.NEW)
        }
        val prev = Money.money(previous)
        val diff = Money.money(cur - prev)
        val percent = if (prev.signum() > 0) {
            Money.percent(diff.multiply(Money.HUNDRED).divide(prev, 10, Money.ROUNDING))
        } else null
        val status = when (diff.signum()) {
            1 -> TrendStatus.INCREASED
            -1 -> TrendStatus.DECREASED
            else -> TrendStatus.STABLE
        }
        return ChangeResult(cur, prev, diff, percent, status)
    }

    /** نسبة القيمة من الإجمالي. */
    fun shareOf(value: BigDecimal, total: BigDecimal): BigDecimal? {
        if (total.signum() <= 0) return null
        return Money.percent(value.multiply(Money.HUNDRED).divide(total, 10, Money.ROUNDING))
    }

    /** مقارنة كل عنصر في الشهر الحالي بقيمته في الشهر السابق. */
    fun compareItems(current: MonthData, previous: MonthData?): List<ItemComparison> {
        val total = monthTotal(current)
        val prevMap = previous?.let { p -> distinctItems(p.values).associateBy { it.itemId } } ?: emptyMap()
        return distinctItems(current.values).map { v ->
            val prev = prevMap[v.itemId]?.valueBase
            ItemComparison(
                itemId = v.itemId,
                change = change(v.valueBase, prev),
                shareOfTotal = if (v.includeInTotal) shareOf(v.valueBase, total) else null,
                includeInTotal = v.includeInTotal,
            )
        }
    }

    /** سلسلة إجماليات الأشهر مرتبة زمنيًا مع التغير عن الشهر السابق المسجل. */
    fun totalsSeries(months: List<MonthData>): List<MonthTotal> {
        val sorted = months.sortedBy { it.month }
        var prev: BigDecimal? = null
        return sorted.map { m ->
            val t = monthTotal(m)
            val mt = MonthTotal(m.month, t, change(t, prev))
            prev = t
            mt
        }
    }

    fun average(values: List<BigDecimal>): BigDecimal? {
        if (values.isEmpty()) return null
        return Money.money(values.sumMoney().divide(BigDecimal(values.size), 10, Money.ROUNDING))
    }

    fun periodStats(series: List<MonthTotal>): PeriodStats {
        if (series.isEmpty()) return PeriodStats(null, null, null, null, null, null, null, null)
        val first = series.first()
        val last = series.last()
        val withChange = series.filter { it.change.change != null }
        return PeriodStats(
            firstMonth = first,
            lastMonth = last,
            highest = series.maxByOrNull { it.total },
            lowest = series.minByOrNull { it.total },
            average = average(series.map { it.total }),
            bestMonth = withChange.filter { it.change.change!!.signum() > 0 }.maxByOrNull { it.change.change!! },
            worstMonth = withChange.filter { it.change.change!!.signum() < 0 }.minByOrNull { it.change.change!! },
            changeSinceFirst = if (series.size >= 2) change(last.total, first.total) else null,
        )
    }

    /** العنصر صاحب أكبر زيادة (قيمةً). */
    fun biggestIncrease(items: List<ItemComparison>): ItemComparison? =
        items.filter { (it.change.change?.signum() ?: 0) > 0 }.maxByOrNull { it.change.change!! }

    /** العنصر صاحب أكبر انخفاض (قيمةً). */
    fun biggestDecrease(items: List<ItemComparison>): ItemComparison? =
        items.filter { (it.change.change?.signum() ?: 0) < 0 }.minByOrNull { it.change.change!! }

    /** العنصر صاحب أكبر نسبة من الإجمالي. */
    fun largestShare(items: List<ItemComparison>): ItemComparison? =
        items.filter { it.includeInTotal && it.shareOfTotal != null }.maxByOrNull { it.change.current }

    /** مجموع السيولة حسب درجة السيولة. */
    fun totalByLevel(values: List<ItemMonthValue>, level: LiquidityLevel): BigDecimal =
        Money.money(distinctItems(values).filter { it.includeInTotal && it.liquidityLevel == level }.map { it.valueBase }.sumMoney())

    /** توزيع السيولة حسب مفتاح (فئة/موقع/عملة/درجة سيولة...). */
    fun <K> distribution(values: List<ItemMonthValue>, keyOf: (ItemMonthValue) -> K): List<Pair<K, Share>> {
        val included = distinctItems(values).filter { it.includeInTotal }
        val total = monthTotal(included)
        return included.groupBy(keyOf).map { (k, list) ->
            val sum = Money.money(list.map { it.valueBase }.sumMoney())
            k to Share(k.toString(), sum, shareOf(sum, total))
        }.sortedByDescending { it.second.value }
    }

    /** مقارنة بين أي شهرين (أو فترتين ممثلتين بشهر نهاية كل منهما). */
    fun compareMonths(a: MonthData, b: MonthData): Pair<ChangeResult, List<ItemComparison>> {
        val totalChange = change(monthTotal(b), monthTotal(a))
        return totalChange to compareItems(b, a)
    }

    /** التغير العضوي للعنصر بعد استبعاد صافي التحويلات الداخلية الواردة إليه. */
    fun organicChange(change: BigDecimal, netTransfersIn: BigDecimal): BigDecimal = Money.money(change - netTransfersIn)
}
