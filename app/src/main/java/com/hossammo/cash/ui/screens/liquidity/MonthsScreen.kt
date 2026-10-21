package com.hossammo.cash.ui.screens.liquidity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.model.ConfirmReason
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.domain.calc.MonthTotal
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.ui.SessionState
import com.hossammo.cash.ui.components.ConfirmDialog
import com.hossammo.cash.ui.components.DropdownField
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
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
import java.time.LocalDate
import javax.inject.Inject

data class MonthsUiState(val loading: Boolean = true, val base: String = "EGP", val series: List<MonthTotal> = emptyList(), val counts: Map<MonthKey, Int> = emptyMap(), val query: String = "", val ascending: Boolean = false)

@HiltViewModel
class MonthsViewModel @Inject constructor(private val repo: LiquidityRepository, private val session: SessionState) : MessagesViewModel() {
    private val query = MutableStateFlow("")
    private val ascending = MutableStateFlow(false)
    val restoreCandidate = MutableStateFlow<Pair<MonthKey, Boolean>?>(null)

    val state: StateFlow<MonthsUiState> = combine(repo.observeData(), query, ascending) { data, q, asc ->
        val a = LiquidityAnalyzer(data)
        val series = a.series().let { if (asc) it else it.reversed() }
        MonthsUiState(false, data.baseCurrency, series, a.monthKeys.associateWith { a.balancesFor(it).size }, q, asc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthsUiState())

    fun setQuery(q: String) { query.value = q }
    fun toggleSort() { ascending.value = !ascending.value }

    fun create(key: MonthKey, copyPrevious: Boolean, restore: Boolean = false, onCreated: (MonthKey) -> Unit) = viewModelScope.launch {
        when (val r = repo.createMonth(key, copyPrevious, restoreIfDeleted = restore)) {
            OpResult.Success -> { message(R.string.msg_month_created); session.selectMonth(key); restoreCandidate.value = null; onCreated(key) }
            is OpResult.NeedsConfirmation -> if (r.reason == ConfirmReason.MONTH_DELETED_EXISTS) restoreCandidate.value = key to copyPrevious
            is OpResult.Failure -> message(errorText(r.error), true)
        }
    }

    fun copyPrevious(key: MonthKey) = viewModelScope.launch {
        when (val r = repo.copyPreviousInto(key)) {
            OpResult.Success -> message(R.string.msg_copied_previous)
            is OpResult.Failure -> message(errorText(r.error), true)
            else -> Unit
        }
    }

    fun delete(key: MonthKey) = viewModelScope.launch {
        repo.softDeleteMonth(key)
        message(R.string.msg_month_deleted, undo = { viewModelScope.launch { repo.createMonth(key, false, restoreIfDeleted = true) } })
    }
}

@Composable
fun MonthsScreen(onBack: () -> Unit, onOpenMonth: (Int) -> Unit, onCompare: () -> Unit, vm: MonthsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val restore by vm.restoreCandidate.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val f = fmt()
    var addDialog by remember { mutableStateOf(false) }
    var deleteKey by remember { mutableStateOf<MonthKey?>(null) }
    AppScaffold(
        title = stringResource(R.string.months_title), onBack = onBack, snackbar = snackbar,
        actions = { IconButton(onClick = onCompare) { Icon(Icons.AutoMirrored.Filled.CompareArrows, stringResource(R.string.compare_months)) } },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { addDialog = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_month)) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchField(state.query, vm::setQuery, Modifier.weight(1f))
                TextButton(onClick = vm::toggleSort) { Text(stringResource(if (state.ascending) R.string.sort_oldest else R.string.sort_newest)) }
            }
            val list = state.series.filter { state.query.isBlank() || f.month(it.month).contains(state.query.trim()) || it.month.toString().contains(state.query.trim()) }
            if (!state.loading && state.series.isEmpty()) {
                EmptyState(stringResource(R.string.no_months_title), stringResource(R.string.first_month_guide), icon = Icons.Filled.CalendarMonth)
            }
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.month.value }) { m ->
                    FinanceCard(Modifier.fillMaxWidth(), onClick = { onOpenMonth(m.month.value) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.month(m.month), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.records_count, f.integer(state.counts[m.month] ?: 0)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onOpenMonth(m.month.value) }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit_balances)) }
                            IconButton(onClick = { vm.copyPrevious(m.month) }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy_previous_month)) }
                            IconButton(onClick = { deleteKey = m.month }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                        }
                        Text(f.money(m.total, state.base), style = MaterialTheme.typography.titleLarge)
                        TrendBadge(m.change.status, m.change.change, m.change.percent)
                    }
                }
            }
        }
    }
    if (addDialog) {
        AddMonthDialog(existing = state.series.map { it.month }, onDismiss = { addDialog = false }) { key, copy ->
            vm.create(key, copy) { addDialog = false; onOpenMonth(it.value) }
        }
    }
    restore?.let { (key, copy) ->
        ConfirmDialog(stringResource(R.string.month_in_trash_title), stringResource(R.string.month_in_trash_message), confirmText = stringResource(R.string.restore),
            onConfirm = { vm.create(key, copy, restore = true) { addDialog = false; onOpenMonth(it.value) } }, onDismiss = { vm.restoreCandidate.value = null })
    }
    deleteKey?.let { k ->
        ConfirmDialog(stringResource(R.string.delete_month_title), stringResource(R.string.delete_month_message, f.month(k)), confirmText = stringResource(R.string.delete), destructive = true,
            onConfirm = { vm.delete(k); deleteKey = null }, onDismiss = { deleteKey = null })
    }
}

@Composable
fun AddMonthDialog(existing: List<MonthKey>, onDismiss: () -> Unit, onCreate: (MonthKey, Boolean) -> Unit) {
    val f = fmt()
    val now = LocalDate.now()
    val suggested = existing.maxOrNull()?.next() ?: MonthKey.of(now.year, now.monthValue)
    var year by remember { mutableStateOf(suggested.year) }
    var month by remember { mutableStateOf(suggested.month) }
    var copy by remember { mutableStateOf(existing.isNotEmpty()) }
    val exists = MonthKey.of(year, month) in existing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_month)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField(stringResource(R.string.month), (1..12).toList(), month, { f.monthName(it) }, { month = it }, Modifier.weight(1f))
                    DropdownField(stringResource(R.string.year), ((now.year - 15)..(now.year + 2)).toList().reversed(), year, { with(f) { it.toString().localizeDigits() } }, { year = it }, Modifier.weight(1f))
                }
                if (exists) Text(stringResource(R.string.msg_month_exists), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(copy, { copy = true }, enabled = existing.isNotEmpty()); Text(stringResource(R.string.copy_previous_balances)) }
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(!copy, { copy = false }); Text(stringResource(R.string.empty_month)) }
                Text(stringResource(R.string.first_month_guide_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(MonthKey.of(year, month), copy) }, enabled = !exists) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
