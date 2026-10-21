package com.hossammo.cash.ui.screens.liquidity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.analysis.ItemRow
import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.data.repo.BalanceDraft
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.domain.calc.CurrencyConverter
import com.hossammo.cash.domain.calc.FinanceCalculator
import com.hossammo.cash.domain.calc.ItemMonthValue
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.ui.SessionState
import com.hossammo.cash.ui.components.AmountField
import com.hossammo.cash.ui.components.ConfirmDialog
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.ItemAvatar
import com.hossammo.cash.ui.components.SearchField
import com.hossammo.cash.ui.components.TrendBadge
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages
import com.hossammo.cash.ui.screens.MessagesViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class MonthEntryState(
    val loading: Boolean = true,
    val month: MonthKey? = null,
    val exists: Boolean = false,
    val base: String = "EGP",
    val rows: List<ItemRow> = emptyList(),
    val edits: Map<String, String> = emptyMap(),
    val rates: Map<String, BigDecimal> = emptyMap(),
    val previousTotal: BigDecimal? = null,
    val query: String = "",
) {
    fun textFor(r: ItemRow): String = edits[r.item.id] ?: r.balance?.amountOriginal?.stripTrailingZeros()?.toPlainString() ?: ""
    val dirty: Boolean get() = edits.isNotEmpty()

    /** معاينة الإجمالي بعد التعديلات (تستخدم طبقة الحسابات). */
    fun previewTotal(): BigDecimal = FinanceCalculator.monthTotal(rows.mapNotNull { r ->
        val amount = Money.parseOrNull(textFor(r)) ?: return@mapNotNull null
        val rate = if (r.balance?.rateLocked == true) r.balance.exchangeRate else rates[r.item.currencyCode]
        val v = CurrencyConverter.toBase(amount, r.item.currencyCode, base, rate) ?: return@mapNotNull null
        ItemMonthValue(r.item.id, v, r.item.includeInTotal)
    })
}

@HiltViewModel
class MonthEntryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: LiquidityRepository,
    private val session: SessionState,
) : MessagesViewModel() {
    val month = MonthKey(checkNotNull(savedState.get<Int>("key")))
    private val edits = MutableStateFlow<Map<String, String>>(emptyMap())
    private val query = MutableStateFlow("")

    init { session.selectMonth(month) }

    val state: StateFlow<MonthEntryState> = combine(repo.observeData(), edits, query) { data, e, q ->
        val a = LiquidityAnalyzer(data)
        val rows = a.itemRows(month).filter { (!it.item.isArchived || it.balance != null) && (q.isBlank() || it.item.name.contains(q.trim(), true)) }
        MonthEntryState(
            loading = false, month = month, exists = month in a.monthKeys, base = data.baseCurrency, rows = rows, edits = e,
            rates = data.rates.associate { it.currencyCode to it.rateToBase }, previousTotal = a.previousKey(month)?.let { a.total(it) }, query = q,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthEntryState())

    fun edit(itemId: String, text: String) { edits.value = edits.value + (itemId to text) }
    fun setQuery(q: String) { query.value = q }

    fun copyPrevious() = viewModelScope.launch {
        if (!state.value.exists) repo.createMonth(month, copyPrevious = true)
        else when (val r = repo.copyPreviousInto(month)) {
            OpResult.Success -> message(R.string.msg_copied_previous)
            is OpResult.Failure -> message(errorText(r.error), true)
            else -> Unit
        }
    }

    /** حفظ كل التعديلات دفعة واحدة. الحقول الفارغة لا تُنشئ سجلات. */
    fun saveAll(onDone: () -> Unit = {}) = viewModelScope.launch {
        val s = state.value
        var saved = 0
        var failed = 0
        for ((itemId, text) in edits.value) {
            val row = s.rows.firstOrNull { it.item.id == itemId } ?: continue
            if (text.isBlank()) {
                row.balance?.let { repo.softDeleteBalance(it.id); saved++ }
                continue
            }
            val amount = Money.parseOrNull(text)
            if (amount == null || amount.signum() < 0) { failed++; continue }
            val r = repo.saveBalance(
                BalanceDraft(month, itemId, amount, lockRate = row.balance?.rateLocked == true, customRate = if (row.balance?.rateLocked == true) row.balance.exchangeRate else null, notes = row.balance?.notes),
                confirmOverwrite = true, isEdit = true,
            )
            if (r == OpResult.Success) saved++ else failed++
        }
        edits.value = emptyMap()
        if (failed > 0) message(R.string.msg_some_failed, true, null, failed) else message(R.string.msg_balances_saved_count, false, null, saved)
        onDone()
    }

    fun discard() { edits.value = emptyMap() }
}

@Composable
fun MonthEntryScreen(onBack: () -> Unit, vm: MonthEntryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val f = fmt()
    var confirmLeave by remember { mutableStateOf(false) }
    BackHandler(enabled = state.dirty) { confirmLeave = true }
    AppScaffold(
        title = stringResource(R.string.month_balances_title, f.month(vm.month)),
        onBack = { if (state.dirty) confirmLeave = true else onBack() },
        snackbar = snackbar,
        actions = { IconButton(onClick = vm::copyPrevious) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy_previous_month)) } },
        floatingActionButton = {
            if (state.dirty) ExtendedFloatingActionButton(onClick = { vm.saveAll() }, icon = { Icon(Icons.Filled.Save, null) }, text = { Text(stringResource(R.string.save_all)) })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FinanceCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.preview_total), style = MaterialTheme.typography.labelLarge)
                val preview = state.previewTotal()
                Text(f.money(preview, state.base), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val c = FinanceCalculator.change(preview, state.previousTotal)
                TrendBadge(c.status, c.change, c.percent)
                if (!state.exists) Text(stringResource(R.string.month_will_be_created), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Text(stringResource(R.string.empty_fields_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SearchField(state.query, vm::setQuery, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.rows, key = { it.item.id }) { r ->
                    FinanceCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ItemAvatar(r.item.icon, r.item.colorHex, size = 32)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.item.name, style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.previous_value, f.money(r.previousBalance?.amountOriginal, r.item.currencyCode)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (r.balance?.rateLocked == true) Icon(Icons.Filled.Lock, stringResource(R.string.rate_locked), tint = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(Modifier.height(4.dp))
                        AmountField(state.textFor(r), { vm.edit(r.item.id, it) }, stringResource(R.string.balance), suffix = r.item.currencyCode)
                        if (r.item.currencyCode != state.base) {
                            val rate = if (r.balance?.rateLocked == true) r.balance.exchangeRate else state.rates[r.item.currencyCode]
                            Text(
                                if (rate == null) stringResource(R.string.no_rate_hint) else stringResource(R.string.rate_info, f.rate(rate), if (r.balance?.rateLocked == true) stringResource(R.string.rate_src_locked) else stringResource(R.string.rate_src_current)),
                                style = MaterialTheme.typography.labelSmall, color = if (rate == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirmLeave) {
        ConfirmDialog(stringResource(R.string.unsaved_title), stringResource(R.string.unsaved_message_save), confirmText = stringResource(R.string.save), dismissText = stringResource(R.string.discard),
            onConfirm = { confirmLeave = false; vm.saveAll(onBack) }, onDismiss = { confirmLeave = false; vm.discard(); onBack() })
    }
}
