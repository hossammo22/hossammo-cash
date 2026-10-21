package com.hossammo.cash.domain

import com.hossammo.cash.domain.calc.CurrencyConverter
import com.hossammo.cash.domain.calc.FinanceCalculator
import com.hossammo.cash.domain.calc.ItemMonthValue
import com.hossammo.cash.domain.calc.MonthData
import com.hossammo.cash.domain.model.LiquidityLevel
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.domain.model.TrendStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * سيناريو التحقق الإلزامي (بيانات اختبار فقط وليست بيانات المستخدم).
 */
class FinanceCalculatorTest {

    private fun bd(v: String) = BigDecimal(v)
    private fun egp(id: String, v: String, level: LiquidityLevel = LiquidityLevel.UNSPECIFIED) =
        ItemMonthValue(id, bd(v), true, level)

    private val sep = MonthKey.of(2026, 9)
    private val oct = MonthKey.of(2026, 10)

    private fun septemberData(): MonthData {
        val usd = CurrencyConverter.toBase(bd("1000"), "USD", "EGP", bd("51"))!!
        return MonthData(
            sep, listOf(
                egp("bank", "200000", LiquidityLevel.IMMEDIATE),
                egp("cash", "50000", LiquidityLevel.IMMEDIATE),
                egp("gold", "300000", LiquidityLevel.NEEDS_LIQUIDATION),
                egp("stocks", "150000", LiquidityLevel.WITHIN_DAYS),
                egp("farm", "100000"),
                egp("factory", "80000"),
                ItemMonthValue("usd", usd, true, LiquidityLevel.IMMEDIATE),
            )
        )
    }

    private fun octoberData(): MonthData {
        val usd = CurrencyConverter.toBase(bd("1000"), "USD", "EGP", bd("50"))!!
        return MonthData(
            oct, listOf(
                egp("bank", "210000", LiquidityLevel.IMMEDIATE),
                egp("cash", "55000", LiquidityLevel.IMMEDIATE),
                egp("gold", "305000", LiquidityLevel.NEEDS_LIQUIDATION),
                egp("stocks", "150000", LiquidityLevel.WITHIN_DAYS),
                egp("farm", "100000"),
                egp("factory", "80000"),
                ItemMonthValue("usd", usd, true, LiquidityLevel.IMMEDIATE),
            )
        )
    }

    @Test
    fun september_total_is_931000() {
        assertEquals(bd("931000.00"), FinanceCalculator.monthTotal(septemberData()))
    }

    @Test
    fun october_total_is_950000_and_change_is_19000() {
        val sepTotal = FinanceCalculator.monthTotal(septemberData())
        val octTotal = FinanceCalculator.monthTotal(octoberData())
        assertEquals(bd("950000.00"), octTotal)
        val change = FinanceCalculator.change(octTotal, sepTotal)
        assertEquals(bd("19000.00"), change.change)
        assertEquals(TrendStatus.INCREASED, change.status)
        assertEquals(bd("2.0408"), change.percent)
    }

    @Test
    fun excluded_item_is_not_counted() {
        val data = listOf(egp("a", "100"), ItemMonthValue("b", bd("50"), includeInTotal = false))
        assertEquals(bd("100.00"), FinanceCalculator.monthTotal(data))
    }

    @Test
    fun same_item_is_never_counted_twice() {
        val data = listOf(egp("a", "100"), egp("a", "100"), egp("b", "5"))
        assertEquals(bd("105.00"), FinanceCalculator.monthTotal(data))
    }

    @Test
    fun decrease_and_stable_statuses() {
        val dec = FinanceCalculator.change(bd("80"), bd("100"))
        assertEquals(TrendStatus.DECREASED, dec.status)
        assertEquals(bd("-20.00"), dec.change)
        assertEquals(bd("-20.0000"), dec.percent)
        val st = FinanceCalculator.change(bd("100"), bd("100"))
        assertEquals(TrendStatus.STABLE, st.status)
        assertEquals(bd("0.00"), st.change)
    }

    @Test
    fun no_previous_month_gives_new_status_and_no_percent() {
        val r = FinanceCalculator.change(bd("100"), null)
        assertEquals(TrendStatus.NEW, r.status)
        assertNull(r.change)
        assertNull(r.percent)
    }

    @Test
    fun previous_zero_gives_unavailable_percent() {
        val r = FinanceCalculator.change(bd("100"), BigDecimal.ZERO)
        assertEquals(TrendStatus.INCREASED, r.status)
        assertEquals(bd("100.00"), r.change)
        assertNull("لا تعرض نسبة صفرية مضللة", r.percent)
    }

    @Test
    fun item_comparisons_and_shares() {
        val items = FinanceCalculator.compareItems(octoberData(), septemberData())
        val bank = items.first { it.itemId == "bank" }
        assertEquals(bd("10000.00"), bank.change.change)
        assertEquals(bd("5.0000"), bank.change.percent)
        val usd = items.first { it.itemId == "usd" }
        assertEquals(TrendStatus.DECREASED, usd.change.status)
        assertEquals(bd("-1000.00"), usd.change.change)
        val stocks = items.first { it.itemId == "stocks" }
        assertEquals(TrendStatus.STABLE, stocks.change.status)
        val gold = items.first { it.itemId == "gold" }
        // 305000 / 950000 = 32.1053%
        assertEquals(bd("32.1053"), gold.shareOfTotal)
        assertEquals("bank", FinanceCalculator.biggestIncrease(items)?.itemId)
        assertEquals("usd", FinanceCalculator.biggestDecrease(items)?.itemId)
        assertEquals("gold", FinanceCalculator.largestShare(items)?.itemId)
    }

    @Test
    fun new_item_in_current_month() {
        val cur = MonthData(oct, listOf(egp("a", "10"), egp("new", "5")))
        val prev = MonthData(sep, listOf(egp("a", "10")))
        val items = FinanceCalculator.compareItems(cur, prev)
        assertEquals(TrendStatus.NEW, items.first { it.itemId == "new" }.change.status)
    }

    @Test
    fun series_and_period_stats() {
        val nov = MonthData(MonthKey.of(2026, 11), listOf(egp("bank", "900000")))
        val series = FinanceCalculator.totalsSeries(listOf(octoberData(), nov, septemberData()))
        assertEquals(listOf(sep, oct, MonthKey.of(2026, 11)), series.map { it.month })
        val stats = FinanceCalculator.periodStats(series)
        assertEquals(bd("950000.00"), stats.highest!!.total)
        assertEquals(bd("900000.00"), stats.lowest!!.total)
        assertEquals(oct, stats.bestMonth!!.month)
        assertEquals(MonthKey.of(2026, 11), stats.worstMonth!!.month)
        assertEquals(bd("927000.00"), stats.average)
        assertEquals(bd("-31000.00"), stats.changeSinceFirst!!.change)
    }

    @Test
    fun liquidity_levels_totals() {
        val v = octoberData().values
        assertEquals(bd("315000.00"), FinanceCalculator.totalByLevel(v, LiquidityLevel.IMMEDIATE))
        assertEquals(bd("305000.00"), FinanceCalculator.totalByLevel(v, LiquidityLevel.NEEDS_LIQUIDATION))
    }

    @Test
    fun distribution_by_key() {
        val dist = FinanceCalculator.distribution(octoberData().values) { it.liquidityLevel }
        val total = dist.fold(BigDecimal.ZERO) { a, p -> a + p.second.value }
        assertEquals(bd("950000.00"), total)
        assertNotNull(dist.first().second.percent)
    }

    @Test
    fun compare_any_two_months() {
        val (total, items) = FinanceCalculator.compareMonths(septemberData(), octoberData())
        assertEquals(bd("19000.00"), total.change)
        assertEquals(7, items.size)
    }

    @Test
    fun month_key_navigation() {
        assertEquals(MonthKey.of(2025, 12), MonthKey.of(2026, 1).previous())
        assertEquals(MonthKey.of(2027, 1), MonthKey.of(2026, 12).next())
        assertEquals(13, MonthKey.of(2025, 9).monthsUntil(MonthKey.of(2026, 10)))
    }
}
