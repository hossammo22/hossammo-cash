package com.hossammo.cash.domain.calc

import com.hossammo.cash.domain.model.Money
import java.math.BigDecimal

sealed interface TransferValidation {
    data object Ok : TransferValidation
    data object SameItem : TransferValidation
    data object NonPositiveAmount : TransferValidation
    data object NegativeFee : TransferValidation
    /** المبلغ يتجاوز رصيد المرسل؛ يُسمح فقط بعد تأكيد المستخدم. */
    data class ExceedsBalance(val available: BigDecimal, val required: BigDecimal) : TransferValidation
}

data class TransferInput(
    val fromItemId: String,
    val toItemId: String,
    /** المبلغ بعملة العنصر المرسل. */
    val amount: BigDecimal,
    /** المبلغ الذي يصل للمستلم بعملته (يساوي amount إذا كانت العملة واحدة). */
    val receivedAmount: BigDecimal = amount,
    val fee: BigDecimal = BigDecimal.ZERO,
    /** هل تُخصم الرسوم من رصيد المرسل (وبالتالي من إجمالي السيولة)؟ */
    val deductFee: Boolean = true,
)

/**
 * التحويلات الداخلية: تنقل المال بين عنصرين دون أن تُحتسب نموًا حقيقيًا.
 */
object TransferCalculator {

    fun validate(input: TransferInput, senderBalance: BigDecimal?): TransferValidation {
        if (input.fromItemId == input.toItemId) return TransferValidation.SameItem
        if (input.amount.signum() <= 0 || input.receivedAmount.signum() <= 0) return TransferValidation.NonPositiveAmount
        if (input.fee.signum() < 0) return TransferValidation.NegativeFee
        val required = input.amount + if (input.deductFee) input.fee else BigDecimal.ZERO
        val available = senderBalance ?: BigDecimal.ZERO
        if (required > available) return TransferValidation.ExceedsBalance(Money.money(available), Money.money(required))
        return TransferValidation.Ok
    }

    /** يطبق التحويل على أرصدة العناصر (بعملة كل عنصر) ويعيد الأرصدة الجديدة. */
    fun apply(balances: Map<String, BigDecimal>, input: TransferInput): Map<String, BigDecimal> {
        require(input.fromItemId != input.toItemId) { "Cannot transfer to the same item" }
        val result = balances.toMutableMap()
        val fee = if (input.deductFee) input.fee else BigDecimal.ZERO
        result[input.fromItemId] = Money.money((result[input.fromItemId] ?: BigDecimal.ZERO) - input.amount - fee)
        result[input.toItemId] = Money.money((result[input.toItemId] ?: BigDecimal.ZERO) + input.receivedAmount)
        return result
    }

    /** صافي التحويلات لعنصر: الوارد − الصادر − الرسوم المخصومة منه. */
    fun netForItem(itemId: String, transfers: List<TransferInput>): BigDecimal {
        var net = BigDecimal.ZERO
        for (t in transfers) {
            if (t.toItemId == itemId) net += t.receivedAmount
            if (t.fromItemId == itemId) net -= t.amount + if (t.deductFee) t.fee else BigDecimal.ZERO
        }
        return Money.money(net)
    }

    /** أثر التحويلات على إجمالي السيولة (بالعملة الأساسية) = − الرسوم المخصومة فقط. */
    fun totalImpactBase(transfersFeesBase: List<Pair<BigDecimal, Boolean>>): BigDecimal =
        Money.money(transfersFeesBase.filter { it.second }.fold(BigDecimal.ZERO) { a, p -> a - p.first })
}
