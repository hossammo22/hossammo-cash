package com.hossammo.cash.domain.calc

import com.hossammo.cash.domain.model.DebtDirection
import com.hossammo.cash.domain.model.DebtStatus
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.domain.model.sumMoney
import java.math.BigDecimal
import java.time.LocalDate

data class DebtSnapshot(
    val id: String,
    val name: String,
    val direction: DebtDirection,
    /** null عندما لم يُدخل المستخدم المبلغ بعد. */
    val originalAmount: BigDecimal?,
    val payments: List<BigDecimal>,
    val dueDate: LocalDate?,
)

data class DebtFigures(
    val original: BigDecimal,
    val paid: BigDecimal,
    val remaining: BigDecimal,
    val overpaid: Boolean,
    val status: DebtStatus,
)

data class PaymentCheck(val exceedsOriginal: Boolean, val remainingAfter: BigDecimal)

data class DebtSummary(
    val owedToMeTotal: BigDecimal,
    val collected: BigDecimal,
    val remainingToMe: BigDecimal,
    val iOweTotal: BigDecimal,
    val paidByMe: BigDecimal,
    val remainingOnMe: BigDecimal,
    /** فرق معلوماتي فقط: المتبقي لي − المتبقي عليّ. لا يدخل في السيولة. */
    val netInformational: BigDecimal,
    val overdueCount: Int,
    val nearestDue: DebtSnapshot?,
)

/**
 * حسابات الديون. الديون منفصلة تمامًا عن السيولة ولا تدخل في أي إجمالي للسيولة.
 */
object DebtCalculator {

    fun totalPaid(payments: List<BigDecimal>): BigDecimal = Money.money(payments.sumMoney())

    /** المتبقي = المبلغ الأصلي − مجموع الدفعات. */
    fun remaining(original: BigDecimal?, payments: List<BigDecimal>): BigDecimal =
        Money.money((original ?: BigDecimal.ZERO) - totalPaid(payments))

    /** فحص دفعة جديدة قبل حفظها: هل تتجاوز المبلغ الأصلي؟ */
    fun checkPayment(original: BigDecimal?, existingPayments: List<BigDecimal>, newPayment: BigDecimal): PaymentCheck {
        val after = Money.money((original ?: BigDecimal.ZERO) - totalPaid(existingPayments) - newPayment)
        return PaymentCheck(exceedsOriginal = after.signum() < 0, remainingAfter = after)
    }

    fun status(original: BigDecimal?, payments: List<BigDecimal>, dueDate: LocalDate?, today: LocalDate): DebtStatus {
        val paid = totalPaid(payments)
        val orig = original ?: BigDecimal.ZERO
        if (orig.signum() > 0 && paid >= orig) return DebtStatus.COMPLETED
        if (dueDate != null && today.isAfter(dueDate)) return DebtStatus.OVERDUE
        if (paid.signum() > 0) return DebtStatus.PARTIALLY_PAID
        if (dueDate == null) return DebtStatus.NO_DUE_DATE
        return DebtStatus.OPEN
    }

    fun figures(d: DebtSnapshot, today: LocalDate): DebtFigures {
        val paid = totalPaid(d.payments)
        val rem = remaining(d.originalAmount, d.payments)
        return DebtFigures(
            original = Money.money(d.originalAmount ?: BigDecimal.ZERO),
            paid = paid,
            remaining = rem,
            overpaid = rem.signum() < 0,
            status = status(d.originalAmount, d.payments, d.dueDate, today),
        )
    }

    fun summary(debts: List<DebtSnapshot>, today: LocalDate): DebtSummary {
        fun sumOf(dir: DebtDirection, f: (DebtFigures) -> BigDecimal) =
            Money.money(debts.filter { it.direction == dir }.map { f(figures(it, today)) }.sumMoney())

        val owed = sumOf(DebtDirection.OWED_TO_ME) { it.original }
        val collected = sumOf(DebtDirection.OWED_TO_ME) { it.paid }
        val remToMe = sumOf(DebtDirection.OWED_TO_ME) { it.remaining.max(BigDecimal.ZERO) }
        val iOwe = sumOf(DebtDirection.I_OWE) { it.original }
        val paidByMe = sumOf(DebtDirection.I_OWE) { it.paid }
        val remOnMe = sumOf(DebtDirection.I_OWE) { it.remaining.max(BigDecimal.ZERO) }
        val open = debts.filter { figures(it, today).status != DebtStatus.COMPLETED }
        return DebtSummary(
            owedToMeTotal = owed,
            collected = collected,
            remainingToMe = Money.money(remToMe),
            iOweTotal = iOwe,
            paidByMe = paidByMe,
            remainingOnMe = Money.money(remOnMe),
            netInformational = Money.money(remToMe - remOnMe),
            overdueCount = open.count { figures(it, today).status == DebtStatus.OVERDUE },
            nearestDue = open.filter { it.dueDate != null && !it.dueDate.isBefore(today) }.minByOrNull { it.dueDate!! },
        )
    }
}
