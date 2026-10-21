package com.hossammo.cash.ui.screens.variable

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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.analysis.ItemRow
import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.db.entity.CurrencyRateEntity
import com.hossammo.cash.data.db.entity.MonthlyBalanceEntity
import com.hossammo.cash.data.repo.CurrencyRepository
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.data.repo.RefreshResult
import com.hossammo.cash.data.settings.UserPreferencesRepository
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.ui.SessionState
import com.hossammo.cash.ui.components.AmountField
import com.hossammo.cash.ui.components.ChipsRow
import com.hossammo.cash.ui.components.ConfirmDialog
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.ItemAvatar
import com.hossammo.cash.ui.components.LabeledValue
import com.hossammo.cash.ui.components.MonthSelector
import com.hossammo.cash.ui.components.SearchField
import com.hossammo.cash.ui.components.SectionHeader
import com.hossammo.cash.ui.components.SkeletonBox
import com.hossammo.cash.ui.components.TrendBadge
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages
import com.hossammo.cash.ui.screens.liquidity.BalanceDialog
import com.hossammo.cash.ui.screens.liquidity.BalanceSavingViewModel
import com.hossammo.cash.ui.theme.LocalFinanceColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class VariableRecord(val row: ItemRow, val month: MonthKey, val balance: MonthlyBalanceEntity?)

data class VariableState(
    val loading: Boolean = true,
    val base: String = "EGP",
    val months: List<MonthKey> = emptyList(),
    val selected: MonthKey? = null,
    val allMonths: Boolean = false,
    val records: List<VariableRecord> = emptyList(),
    val rates: List<CurrencyRateEntity> = emptyList(),
    val trackedCurrencies: List<String> = emptyList(),
    val refreshing: Boolean = false,
    val lastRefresh: Long = 0,
    val lastRefreshOk: Boolean = false,
    val lastError: String? = null,
    val total: BigDecimal = BigDecimal.ZERO,
    val query: String = "",
)

@HiltViewModel
class VariableViewModel @Inject constructor(
    repo: LiquidityRepository,
    private val currency: CurrencyRepository,
    prefs: UserPreferencesRepository,
    private val session: SessionState,
) : BalanceSavingViewModel(repo) {
    private val refreshing = MutableStateFlow(false)
    private val allMonths = MutableStateFlow(false)
    private val query = MutableStateFlow("")

    val state: StateFlow<VariableState> = combine(repo.observeData(), session.selectedMonth, prefs.prefs, combine(refreshing, allMonths, query) { a, b, c -> Triple(a, b, c) }) { data, _, p, flags ->
        val (isRefreshing, all, q) = flags
        val a = LiquidityAnalyzer(data)
        val selected = session.resolve(a.monthKeys)
        val varItems = data.items.filter { it.isVariable && (q.isBlank() || it.name.contains(q, true)) }.map { it.id }.toSet()
        val months = if (all) a.monthKeys.reversed() else listOfNotNull(selected)
        val records = months.flatMap { m ->
            a.itemRows(m).filter { it.item.id in varItems && (it.balance != null || m == selected) }.map { VariableRecord(it, m, it.balance) }
        }
        val total = selected?.let { m -> a.itemRows(m).filter { it.item.id in varItems && it.item.includeInTotal }.mapNotNull { it.valueBase }.fold(BigDecimal.ZERO) { x, y -> x + y } } ?: BigDecimal.ZERO
        val tracked = (CurrencyRepository.DEFAULT_CURRENCIES + data.items.map { it.currencyCode } + data.rates.map { it.currencyCode }).filter { it != data.baseCurrency }.distinct().sorted()
        VariableState(
            loading = false, base = data.baseCurrency, months = a.monthKeys, selected = selected, allMonths = all, records = records,
            rates = data.rates, trackedCurrencies = tracked, refreshing = isRefreshing, lastRefresh = p.lastRateRefreshAt,
            lastRefreshOk = p.lastRateRefreshOk, lastError = p.lastRateError, total = Money.money(total), query = q,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VariableState())

    fun selectMonth(k: MonthKey) = session.selectMonth(k)
    fun setAllMonths(v: Boolean) { allMonths.value = v }
    fun setQuery(q: String) { query.value = q }

    fun refreshRates() = viewModelScope.launch {
        refreshing.value = true
        when (val r = currency.refresh()) {
            is RefreshResult.Success -> message(R.string.msg_rates_updated, false, null, r.updated)
            is RefreshResult.Failed -> message(if (r.kind == RefreshResult.Kind.NETWORK) R.string.msg_rates_offline else R.string.msg_rates_failed, true)
        }
        refreshing.value = false
    }

    fun setManualRate(code: String, rate: BigDecimal) = viewModelScope.launch {
        if (currency.setManualRate(code, rate)) message(R.string.msg_manual_rate_saved) else message(R.string.msg_invalid_rate, true)
    }
}

@Composable
fun VariableSavingsScreen(onOpenItem: (String) -> Unit, onOpenCurrencySettings: () -> Unit, vm: VariableViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val pending by vm.pendingDuplicate.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val f = fmt()
    var editRecord by remember { mutableStateOf<VariableRecord?>(null) }
    var manualFor by remember { mutableStateOf<String?>(null) }
    var unlock by remember { mutableStateOf<Pair<MonthKey, String>?>(null) }
    AppScaffold(
        title = stringResource(R.string.nav_variable), snackbar = snackbar,
        actions = { IconButton(onClick = onOpenCurrencySettings) { Icon(Icons.Filled.Settings, stringResource(R.string.currency_settings)) } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { MonthSelector(s.months, s.selected, vm::selectMonth) }
                FinanceCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.variable_total), style = MaterialTheme.typography.labelLarge)
                    Text(f.money(s.total, s.base), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.variable_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SectionHeader(stringResource(R.string.exchange_rates)) {
                    TextButton(onClick = vm::refreshRates, enabled = !s.refreshing) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.refresh_rates_now)) }
                }
                FinanceCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.last_rates_update, f.dateTime(s.lastRefresh)), style = MaterialTheme.typography.bodySmall)
                    if (!s.lastRefreshOk && s.lastError != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 4.dp))
                            Text(stringResource(R.string.using_saved_rates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    s.trackedCurrencies.forEach { code ->
                        val r = s.rates.firstOrNull { it.currencyCode == code }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("$code / ${s.base}", style = MaterialTheme.typography.titleSmall)
                                if (s.refreshing) SkeletonBox(Modifier.width(120.dp).height(14.dp))
                                else Text(
                                    if (r == null) stringResource(R.string.no_rate_yet)
                                    else "${f.rate(r.rateToBase)} • ${if (r.isManual) stringResource(R.string.rate_src_manual) else sourceName(r.source)} • ${f.dateTime(r.fetchedAt)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { manualFor = code }) { Text(stringResource(R.string.manual_rate)) }
                        }
                    }
                }
            }
            item {
                SectionHeader(stringResource(R.string.variable_records))
                SearchField(s.query, vm::setQuery)
                ChipsRow(listOf(false, true), s.allMonths, { stringResource(if (it) R.string.all_months else R.string.selected_month) }, vm::setAllMonths, Modifier.padding(top = 6.dp))
            }
            if (s.records.isEmpty()) item { EmptyState(stringResource(R.string.no_variable_title), stringResource(R.string.no_variable_message)) }
            items(s.records, key = { "${it.month.value}_${it.row.item.id}" }) { rec ->
                val r = rec.row
                val b = rec.balance
                FinanceCard(Modifier.fillMaxWidth(), onClick = { onOpenItem(r.item.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemAvatar(r.item.icon, r.item.colorHex, size = 34)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.item.name, style = MaterialTheme.typography.titleSmall)
                            Text(listOfNotNull(f.month(rec.month), r.category?.name, r.location?.name, r.item.currencyCode).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (b != null && r.item.currencyCode != s.base) {
                            if (b.rateLocked) IconButton(onClick = { unlock = rec.month to r.item.currencyCode }) { Icon(Icons.Filled.Lock, stringResource(R.string.unlock_rate), tint = LocalFinanceColors.current.gold) }
                            else IconButton(onClick = { vm.lockRate(rec.month, r.item.currencyCode, null) }) { Icon(Icons.Filled.LockOpen, stringResource(R.string.lock_rate)) }
                        }
                        IconButton(onClick = { editRecord = rec }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                    }
                    if (b == null) {
                        Text(stringResource(R.string.no_balance_this_month), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        LabeledValue(stringResource(R.string.original_value), f.money(b.amountOriginal, b.currencyCode))
                        if (b.currencyCode != s.base) LabeledValue(stringResource(R.string.rate_used), f.rate(b.exchangeRate))
                        LabeledValue(stringResource(R.string.value_in_base), if (b.valueBase == null) stringResource(R.string.missing_rate) else f.money(b.valueBase, s.base))
                        LabeledValue(stringResource(R.string.col_rate_type), f.rateSource(b.rateSource))
                        if (rec.month == s.selected) r.comparison?.let { c -> TrendBadge(c.change.status, c.change.change, c.change.percent) }
                        b.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(stringResource(R.string.last_modified, f.dateTime(b.updatedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
    editRecord?.let { rec ->
        val item = rec.row.item
        val b = rec.balance
        BalanceDialog(
            itemName = item.name, currency = item.currencyCode, baseCurrency = s.base, months = s.months, initialMonth = rec.month,
            initialAmount = b?.amountOriginal, initialLocked = b?.rateLocked ?: false, initialRate = b?.exchangeRate,
            currentRate = s.rates.firstOrNull { it.currencyCode == item.currencyCode }?.rateToBase, initialNotes = b?.notes, allowMonthChange = false,
            onDismiss = { editRecord = null },
            onSave = { input -> vm.saveBalance(item.id, input, isEdit = true) { editRecord = null } },
        )
    }
    pending?.let { (id, input) ->
        ConfirmDialog(stringResource(R.string.duplicate_balance_title), stringResource(R.string.duplicate_balance_message), confirmText = stringResource(R.string.overwrite),
            onConfirm = { vm.saveBalance(id, input, isEdit = false, confirm = true) { editRecord = null } }, onDismiss = { vm.pendingDuplicate.value = null })
    }
    manualFor?.let { code -> ManualRateDialog(code, s.base, onDismiss = { manualFor = null }) { rate -> vm.setManualRate(code, rate); manualFor = null } }
    unlock?.let { (m, code) ->
        ConfirmDialog(stringResource(R.string.unlock_rate_title), stringResource(R.string.unlock_rate_message), confirmText = stringResource(R.string.unlock_rate),
            onConfirm = { vm.unlockRate(m, code); unlock = null }, onDismiss = { unlock = null })
    }
}

@Composable
fun sourceName(id: String): String = stringResource(
    when (id) {
        "open_er_api" -> R.string.provider_open_er
        "fawaz_currency_api" -> R.string.provider_fawaz
        "manual" -> R.string.rate_src_manual
        else -> R.string.rate_src_saved
    }
)

@Composable
fun ManualRateDialog(code: String, base: String, onDismiss: () -> Unit, onSave: (BigDecimal) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = Money.parseOrNull(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_rate_title, code)) },
        text = {
            Column {
                Text(stringResource(R.string.manual_rate_hint, code, base), style = MaterialTheme.typography.bodySmall)
                AmountField(text, { text = it }, stringResource(R.string.rate), suffix = base, isError = text.isNotBlank() && (parsed == null || parsed.signum() <= 0))
            }
        },
        confirmButton = { Button(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed.signum() > 0) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
