package com.hossammo.cash.domain

import com.hossammo.cash.domain.calc.DebtCalculator
import com.hossammo.cash.domain.calc.DebtSnapshot
import com.hossammo.cash.domain.calc.FinanceCalculator
import com.hossammo.cash.domain.calc.ItemMonthValue
import com.hossammo.cash.domain.calc.TransferCalculator
import com.hossammo.cash.domain.calc.TransferInput
import com.hossammo.cash.domain.calc.TransferValidation
import com.hossammo.cash.domain.model.DebtDirection
import com.hossammo.cash.domain.model.DebtStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransferAndDebtTest {
    private fun bd(v: String) = BigDecimal(v)

    private val octBalances = mapOf(
        "bank" to bd("210000"), "cash" to bd("55000"), "gold" to bd("305000"),
        "stocks" to bd("150000"), "farm" to bd("100000"), "factory" to bd("80000"), "usd" to bd("50000"),
    )

    private fun total(b: Map<String, BigDecimal>) =
        FinanceCalculator.monthTotal(b.map { ItemMonthValue(it.key, it.value) })

    @Test
    fun transfer_moves_money_without_changing_total() {
        val before = total(octBalances)
        assertEquals(bd("950000.00"), before)
        val t = TransferInput("bank", "cash", bd("20000"))
        assertEquals(TransferValidation.Ok, TransferCalculator.validate(t, octBalances["bank"]))
        val after = TransferCalculator.apply(octBalances, t)
        assertEquals(bd("190000.00"), after["bank"])
        assertEquals(bd("75000.00"), after["cash"])
        assertEquals(before, total(after))
        assertEquals(bd("-20000.00"), TransferCalculator.netForItem("bank", listOf(t)))
        assertEquals(bd("20000.00"), TransferCalculator.netForItem("cash", listOf(t)))
        // التغير العضوي للبنك بعد استبعاد التحويل = صفر
        assertEquals(bd("0.00"), FinanceCalculator.organicChange(bd("-20000"), bd("-20000")))
    }

    @Test
    fun transfer_fee_reduces_total_only_when_deducted() {
        val withFee = TransferCalculator.apply(octBalances, TransferInput("bank", "cash", bd("20000"), fee = bd("50")))
        assertEquals(bd("949950.00"), total(withFee))
        val notDeducted = TransferCalculator.apply(octBalances, TransferInput("bank", "cash", bd("20000"), fee = bd("50"), deductFee = false))
        assertEquals(bd("950000.00"), total(notDeducted))
    }

    @Test
    fun transfer_to_same_item_is_rejected() {
        assertEquals(TransferValidation.SameItem, TransferCalculator.validate(TransferInput("bank", "bank", bd("1")), bd("10")))
    }

    @Test
    fun transfer_exceeding_balance_is_flagged() {
        val v = TransferCalculator.validate(TransferInput("cash", "bank", bd("60000")), bd("55000"))
        assertTrue(v is TransferValidation.ExceedsBalance)
    }

    @Test
    fun payments_sum_and_remaining() {
        val payments = listOf(bd("1000"), bd("2500.50"), bd("499.50"))
        assertEquals(bd("4000.00"), DebtCalculator.totalPaid(payments))
        assertEquals(bd("6000.00"), DebtCalculator.remaining(bd("10000"), payments))
    }

    @Test
    fun overpayment_is_detected() {
        val check = DebtCalculator.checkPayment(bd("10000"), listOf(bd("9000")), bd("1500"))
        assertTrue(check.exceedsOriginal)
        assertEquals(bd("-500.00"), check.remainingAfter)
        assertFalse(DebtCalculator.checkPayment(bd("10000"), listOf(bd("9000")), bd("1000")).exceedsOriginal)
    }

    @Test
    fun debt_statuses() {
        val today = LocalDate.of(2026, 10, 15)
        assertEquals(DebtStatus.COMPLETED, DebtCalculator.status(bd("100"), listOf(bd("100")), null, today))
        assertEquals(DebtStatus.OVERDUE, DebtCalculator.status(bd("100"), listOf(bd("10")), today.minusDays(1), today))
        assertEquals(DebtStatus.PARTIALLY_PAID, DebtCalculator.status(bd("100"), listOf(bd("10")), today.plusDays(5), today))
        assertEquals(DebtStatus.NO_DUE_DATE, DebtCalculator.status(bd("100"), emptyList(), null, today))
        assertEquals(DebtStatus.OPEN, DebtCalculator.status(bd("100"), emptyList(), today.plusDays(3), today))
        // دين بلا مبلغ بعد (القيم الافتراضية فارغة) لا يسبب خطأ
        assertEquals(DebtStatus.NO_DUE_DATE, DebtCalculator.status(null, emptyList(), null, today))
    }

    @Test
    fun debts_are_excluded_from_liquidity_total() {
        val today = LocalDate.of(2026, 10, 15)
        val debts = listOf(DebtSnapshot("d1", "قسط", DebtDirection.I_OWE, bd("1000000"), emptyList(), null))
        val summary = DebtCalculator.summary(debts, today)
        assertEquals(bd("1000000.00"), summary.remainingOnMe)
        // إجمالي السيولة يُحسب من عناصر السيولة فقط ويظل 950,000
        assertEquals(bd("950000.00"), total(octBalances))
    }

    @Test
    fun debt_summary() {
        val today = LocalDate.of(2026, 10, 15)
        val debts = listOf(
            DebtSnapshot("a", "a", DebtDirection.OWED_TO_ME, bd("5000"), listOf(bd("1000")), today.plusDays(10)),
            DebtSnapshot("b", "b", DebtDirection.OWED_TO_ME, bd("2000"), emptyList(), today.minusDays(2)),
            DebtSnapshot("c", "c", DebtDirection.I_OWE, bd("3000"), listOf(bd("3000")), null),
            DebtSnapshot("d", "d", DebtDirection.I_OWE, null, emptyList(), null),
        )
        val s = DebtCalculator.summary(debts, today)
        assertEquals(bd("7000.00"), s.owedToMeTotal)
        assertEquals(bd("1000.00"), s.collected)
        assertEquals(bd("6000.00"), s.remainingToMe)
        assertEquals(bd("3000.00"), s.iOweTotal)
        assertEquals(bd("3000.00"), s.paidByMe)
        assertEquals(bd("0.00"), s.remainingOnMe)
        assertEquals(bd("6000.00"), s.netInformational)
        assertEquals(1, s.overdueCount)
        assertEquals("a", s.nearestDue?.id)
    }
}
