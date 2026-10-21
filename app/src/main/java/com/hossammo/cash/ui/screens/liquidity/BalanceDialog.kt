package com.hossammo.cash.ui.screens.liquidity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hossammo.cash.R
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.ui.components.AmountField
import com.hossammo.cash.ui.components.AppTextField
import com.hossammo.cash.ui.components.DropdownField
import com.hossammo.cash.ui.components.fmt
import java.math.BigDecimal

data class BalanceInput(
    val month: MonthKey,
    val amount: BigDecimal,
    val lockRate: Boolean,
    val customRate: BigDecimal?,
    val notes: String?,
)

/**
 * نافذة إدخال/تعديل رصيد شهري لعنصر. تدعم اختيار الشهر، وتثبيت سعر الصرف للعملات الأجنبية.
 */
@Composable
fun BalanceDialog(
    itemName: String,
    currency: String,
    baseCurrency: String,
    months: List<MonthKey>,
    initialMonth: MonthKey,
    initialAmount: BigDecimal?,
    initialLocked: Boolean,
    initialRate: BigDecimal?,
    currentRate: BigDecimal?,
    initialNotes: String?,
    allowMonthChange: Boolean,
    onDismiss: () -> Unit,
    onSave: (BalanceInput) -> Unit,
) {
    val f = fmt()
    var month by remember { mutableStateOf(initialMonth) }
    var amount by remember { mutableStateOf(initialAmount?.stripTrailingZeros()?.toPlainString() ?: "") }
    var locked by remember { mutableStateOf(initialLocked) }
    var rate by remember { mutableStateOf(if (initialLocked) initialRate?.stripTrailingZeros()?.toPlainString() ?: "" else "") }
    var notes by remember { mutableStateOf(initialNotes ?: "") }
    var error by remember { mutableStateOf<Int?>(null) }
    val foreign = !currency.equals(baseCurrency, true)
    val monthOptions = remember(months, initialMonth) {
        // يسمح باختيار شهر موجود أو الشهر الحالي/التالي لإضافة شهر جديد
        val now = java.time.LocalDate.now()
        (months + initialMonth + MonthKey.of(now.year, now.monthValue) + (months.maxOrNull()?.next() ?: initialMonth)).distinct().sortedDescending()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(itemName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (allowMonthChange) {
                    DropdownField(stringResource(R.string.month), monthOptions, month, { f.month(it) }, { month = it })
                } else {
                    Text(f.month(month), style = MaterialTheme.typography.titleSmall)
                }
                AmountField(amount, { amount = it; error = null }, stringResource(R.string.balance), suffix = currency, isError = error == R.string.msg_invalid_amount)
                if (foreign) {
                    Text(stringResource(R.string.current_rate_value, currency, f.rate(currentRate)), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(!locked, { locked = false }); Text(stringResource(R.string.use_current_rate)) }
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(locked, { locked = true }); Text(stringResource(R.string.lock_month_rate)) }
                    if (locked) AmountField(rate, { rate = it; error = null }, stringResource(R.string.month_rate_optional), suffix = baseCurrency, isError = error == R.string.msg_invalid_rate)
                    val preview = Money.parseOrNull(amount)?.let { a ->
                        val r = if (locked) Money.parseOrNull(rate) ?: initialRate ?: currentRate else currentRate
                        r?.let { a.multiply(it) }
                    }
                    Text(stringResource(R.string.value_preview, f.money(preview, baseCurrency)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                AppTextField(notes, { notes = it }, stringResource(R.string.notes), singleLine = false)
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = Money.parseOrNull(amount)
                val r = if (locked && rate.isNotBlank()) Money.parseOrNull(rate) else null
                when {
                    a == null || a.signum() < 0 -> error = R.string.msg_invalid_amount
                    locked && rate.isNotBlank() && (r == null || r.signum() <= 0) -> error = R.string.msg_invalid_rate
                    else -> onSave(BalanceInput(month, a, foreign && locked, r, notes.ifBlank { null }))
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

