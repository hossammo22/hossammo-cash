package com.hossammo.cash.ui.screens.debts

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.model.DebtWithPayments
import com.hossammo.cash.data.repo.DebtRepository
import com.hossammo.cash.domain.calc.DebtCalculator
import com.hossammo.cash.domain.calc.DebtFigures
import com.hossammo.cash.domain.calc.DebtSnapshot
import com.hossammo.cash.domain.calc.DebtSummary
import com.hossammo.cash.domain.model.DebtDirection
import com.hossammo.cash.domain.model.DebtStatus
import com.hossammo.cash.ui.charts.HorizontalBars
import com.hossammo.cash.ui.components.ChipsRow
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.LabeledValue
import com.hossammo.cash.ui.components.SearchField
import com.hossammo.cash.ui.components.SectionHeader
import com.hossammo.cash.ui.components.StatCard
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages
import com.hossammo.cash.ui.screens.MessagesViewModel
import com.hossammo.cash.ui.theme.LocalFinanceColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

enum class DebtFilter { ALL, OPEN, OVERDUE, COMPLETED, NO_AMOUNT }
enum class DebtSort { ORDER, NAME, REMAINING, DUE }

data class DebtRow(val data: DebtWithPayments, val snapshot: DebtSnapshot, val figures: DebtFigures)

data class DebtsState(
    val loading: Boolean = true,
    val tab: DebtDirection = DebtDirection.OWED_TO_ME,
    val rows: List<DebtRow> = emptyList(),
    val summary: DebtSummary? = null,
    val query: String = "",
    val filter: DebtFilter = DebtFilter.ALL,
    val sort: DebtSort = DebtSort.ORDER,
)

fun DebtWithPayments.toSnapshot() = DebtSnapshot(
    id = debt.id, name = debt.name, direction = debt.direction, originalAmount = debt.originalAmount,
    payments = payments.map { it.amount }, dueDate = debt.dueDate?.let { LocalDate.ofEpochDay(it) },
)

@HiltViewModel
class DebtsViewModel @Inject constructor(repo: DebtRepository) : MessagesViewModel() {
    private val tab = MutableStateFlow(DebtDirection.OWED_TO_ME)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(DebtFilter.ALL)
    private val sort = MutableStateFlow(DebtSort.ORDER)

    val state: StateFlow<DebtsState> = combine(repo.observeDebts(), tab, query, filter, sort) { debts, t, q, fl, so ->
        val today = LocalDate.now()
        val all = debts.map { d -> val s = d.toSnapshot(); DebtRow(d, s, DebtCalculator.figures(s, today)) }
        val rows = all.asSequence()
            .filter { it.data.debt.direction == t }
            .filter { q.isBlank() || it.data.debt.name.contains(q, true) || (it.data.debt.notes ?: "").contains(q, true) }
            .filter {
                when (fl) {
                    DebtFilter.ALL -> true
                    DebtFilter.OPEN -> it.figures.status != DebtStatus.COMPLETED
                    DebtFilter.OVERDUE -> it.figures.status == DebtStatus.OVERDUE
                    DebtFilter.COMPLETED -> it.figures.status == DebtStatus.COMPLETED
                    DebtFilter.NO_AMOUNT -> it.data.debt.originalAmount == null
                }
            }
            .let { seq ->
                when (so) {
                    DebtSort.ORDER -> seq.sortedBy { it.data.debt.sortOrder }
                    DebtSort.NAME -> seq.sortedBy { it.data.debt.name }
                    DebtSort.REMAINING -> seq.sortedByDescending { it.figures.remaining }
                    DebtSort.DUE -> seq.sortedWith(compareBy(nullsLast()) { it.data.debt.dueDate })
                }
            }.toList()
        DebtsState(false, t, rows, DebtCalculator.summary(all.map { it.snapshot }, today), q, fl, so)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsState())

    fun setTab(d: DebtDirection) { tab.value = d }
    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: DebtFilter) { filter.value = f }
    fun setSort(s: DebtSort) { sort.value = s }
}

@Composable
fun debtStatusColor(s: DebtStatus): Color {
    val fc = LocalFinanceColors.current
    return when (s) {
        DebtStatus.COMPLETED -> fc.increase
        DebtStatus.OVERDUE -> fc.decrease
        DebtStatus.PARTIALLY_PAID -> fc.gold
        DebtStatus.OPEN -> MaterialTheme.colorScheme.primary
        DebtStatus.NO_DUE_DATE -> fc.stable
    }
}

@Composable
fun debtFilterLabel(f: DebtFilter) = stringResource(
    when (f) {
        DebtFilter.ALL -> R.string.filter_all
        DebtFilter.OPEN -> R.string.debt_filter_open
        DebtFilter.OVERDUE -> R.string.debt_status_overdue
        DebtFilter.COMPLETED -> R.string.debt_status_completed
        DebtFilter.NO_AMOUNT -> R.string.debt_filter_no_amount
    }
)

@Composable
fun debtSortLabel(s: DebtSort) = stringResource(
    when (s) {
        DebtSort.ORDER -> R.string.sort_custom
        DebtSort.NAME -> R.string.sort_name
        DebtSort.REMAINING -> R.string.sort_remaining
        DebtSort.DUE -> R.string.sort_due
    }
)

@Composable
fun DebtsScreen(onAdd: (String) -> Unit, onOpen: (String) -> Unit, vm: DebtsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val f = fmt()
    val fc = LocalFinanceColors.current
    AppScaffold(
        title = stringResource(R.string.debts_title), snackbar = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onAdd(s.tab.name) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_debt)) })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FinanceCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.debts_separate_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            s.summary?.let { sum ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard(stringResource(R.string.remaining_to_me), f.money(sum.remainingToMe), Modifier.weight(1f),
                            subtitle = stringResource(R.string.collected_of, f.money(sum.collected), f.money(sum.owedToMeTotal)),
                            icon = Icons.Filled.ArrowDownward, accent = fc.increase)
                        StatCard(stringResource(R.string.remaining_on_me), f.money(sum.remainingOnMe), Modifier.weight(1f),
                            subtitle = stringResource(R.string.paid_of, f.money(sum.paidByMe), f.money(sum.iOweTotal)),
                            icon = Icons.Filled.ArrowUpward, accent = fc.decrease)
                    }
                }
                item {
                    FinanceCard(Modifier.fillMaxWidth()) {
                        LabeledValue(stringResource(R.string.net_informational), f.signedMoney(sum.netInformational))
                        Text(stringResource(R.string.net_informational_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LabeledValue(stringResource(R.string.overdue_count), f.integer(sum.overdueCount), valueColor = if (sum.overdueCount > 0) fc.decrease else Color.Unspecified)
                        LabeledValue(
                            stringResource(R.string.nearest_due),
                            sum.nearestDue?.let { "${it.name} • ${f.date(it.dueDate)}" } ?: stringResource(R.string.value_empty),
                        )
                        Spacer(Modifier.height(8.dp))
                        HorizontalBars(
                            rows = listOf(
                                Triple(stringResource(R.string.remaining_to_me), sum.remainingToMe.toFloat(), fc.increase),
                                Triple(stringResource(R.string.remaining_on_me), sum.remainingOnMe.toFloat(), fc.decrease),
                                Triple(stringResource(R.string.collected), sum.collected.toFloat(), fc.gold),
                                Triple(stringResource(R.string.paid_by_me), sum.paidByMe.toFloat(), MaterialTheme.colorScheme.primary),
                            ),
                            valueText = { i ->
                                f.money(listOf(sum.remainingToMe, sum.remainingOnMe, sum.collected, sum.paidByMe)[i])
                            },
                        )
                    }
                }
            }
            item {
                TabRow(selectedTabIndex = if (s.tab == DebtDirection.OWED_TO_ME) 0 else 1) {
                    Tab(selected = s.tab == DebtDirection.OWED_TO_ME, onClick = { vm.setTab(DebtDirection.OWED_TO_ME) }, text = { Text(stringResource(R.string.debts_owed_to_me)) })
                    Tab(selected = s.tab == DebtDirection.I_OWE, onClick = { vm.setTab(DebtDirection.I_OWE) }, text = { Text(stringResource(R.string.debts_i_owe)) })
                }
            }
            item { SearchField(s.query, vm::setQuery) }
            item { ChipsRow(DebtFilter.entries, s.filter, { debtFilterLabel(it) }, vm::setFilter) }
            item { ChipsRow(DebtSort.entries, s.sort, { debtSortLabel(it) }, vm::setSort) }
            if (!s.loading && s.rows.isEmpty()) {
                item {
                    EmptyState(stringResource(R.string.no_debts_title), stringResource(R.string.no_debts_message), icon = Icons.Filled.Handshake)
                }
            }
            items(s.rows, key = { it.data.debt.id }) { row -> DebtCard(row) { onOpen(row.data.debt.id) } }
        }
    }
}

@Composable
fun DebtCard(row: DebtRow, onClick: () -> Unit) {
    val f = fmt()
    val d = row.data.debt
    val fig = row.figures
    val color = debtStatusColor(fig.status)
    FinanceCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(f.debtStatus(fig.status), color = color, style = MaterialTheme.typography.labelMedium)
            if (fig.status == DebtStatus.OVERDUE) Icon(Icons.Filled.Warning, null, tint = color, modifier = Modifier.padding(start = 4.dp))
        }
        if (d.originalAmount == null) {
            Text(stringResource(R.string.amount_not_entered), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        } else {
            LabeledValue(stringResource(R.string.original_amount), f.money(fig.original, d.currencyCode))
            LabeledValue(stringResource(if (d.direction == DebtDirection.OWED_TO_ME) R.string.collected else R.string.paid_by_me), f.money(fig.paid, d.currencyCode))
            LabeledValue(stringResource(R.string.remaining), f.money(fig.remaining, d.currencyCode), valueColor = if (fig.overpaid) LocalFinanceColors.current.decrease else Color.Unspecified)
            if (fig.original.signum() > 0) {
                val progress = (fig.paid.toFloat() / fig.original.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = color)
            }
        }
        d.dueDate?.let { LabeledValue(stringResource(R.string.due_date), f.dateFromEpochDay(it)) }
        if (fig.overpaid) Text(stringResource(R.string.overpaid_note), color = LocalFinanceColors.current.decrease, style = MaterialTheme.typography.labelSmall)
    }
}
