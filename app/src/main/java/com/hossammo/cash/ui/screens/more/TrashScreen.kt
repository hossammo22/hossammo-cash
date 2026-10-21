package com.hossammo.cash.ui.screens.more

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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import com.hossammo.cash.data.repo.DebtRepository
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.ui.components.ConfirmDialog
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages
import com.hossammo.cash.ui.screens.MessagesViewModel
import com.hossammo.cash.util.AppFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

enum class TrashKind(val title: Int) {
    ITEMS(R.string.trash_items), MONTHS(R.string.trash_months), BALANCES(R.string.trash_balances),
    TRANSFERS(R.string.trash_transfers), DEBTS(R.string.trash_debts), PAYMENTS(R.string.trash_payments),
}

/** عنصر في سلة المحذوفات. label/detail تُبنى عند العرض حسب نوع العنصر. */
data class TrashEntry(
    val kind: TrashKind, val id: String, val deletedAt: Long,
    val name: String? = null, val month: MonthKey? = null, val amount: BigDecimal? = null, val currency: String? = null,
    val secondName: String? = null,
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val liquidity: LiquidityRepository,
    private val debts: DebtRepository,
) : MessagesViewModel() {

    private val liquidityTrash = combine(
        liquidity.observeData(), liquidity.observeDeletedItems(), liquidity.observeDeletedMonths(),
        liquidity.observeDeletedBalances(), liquidity.observeDeletedTransfers(),
    ) { data, items, months, balances, transfers ->
        val names = (data.items + items).associate { it.id to it.name }
        items.map { TrashEntry(TrashKind.ITEMS, it.id, it.deletedAt ?: 0, name = it.name) } +
            months.map { TrashEntry(TrashKind.MONTHS, it.id, it.deletedAt ?: 0, month = MonthKey(it.monthKey)) } +
            balances.map { TrashEntry(TrashKind.BALANCES, it.id, it.deletedAt ?: 0, name = names[it.itemId], month = MonthKey(it.monthKey), amount = it.amountOriginal, currency = it.currencyCode) } +
            transfers.map { TrashEntry(TrashKind.TRANSFERS, it.id, it.deletedAt ?: 0, name = names[it.fromItemId], secondName = names[it.toItemId], amount = it.amount, currency = it.currencyCode) }
    }

    private val debtTrash = combine(debts.observeDebts(), debts.observeDeletedDebts(), debts.observeDeletedPayments()) { active, deletedDebts, deletedPayments ->
        val names = (active.map { it.debt } + deletedDebts).associate { it.id to it.name }
        deletedDebts.map { TrashEntry(TrashKind.DEBTS, it.id, it.deletedAt ?: 0, name = it.name, amount = it.originalAmount, currency = it.currencyCode) } +
            deletedPayments.map { TrashEntry(TrashKind.PAYMENTS, it.id, it.deletedAt ?: 0, name = names[it.debtId], amount = it.amount) }
    }

    val entries: StateFlow<List<TrashEntry>> = combine(liquidityTrash, debtTrash) { a, b -> (a + b).sortedByDescending { it.deletedAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(e: TrashEntry) = viewModelScope.launch {
        when (e.kind) {
            TrashKind.ITEMS -> liquidity.restoreItem(e.id)
            TrashKind.MONTHS -> liquidity.restoreMonth(e.id)
            TrashKind.BALANCES -> liquidity.restoreBalance(e.id)
            TrashKind.TRANSFERS -> liquidity.restoreTransfer(e.id)
            TrashKind.DEBTS -> debts.restoreDebt(e.id)
            TrashKind.PAYMENTS -> debts.restorePayment(e.id)
        }
        message(R.string.msg_restored)
    }

    fun purge(e: TrashEntry) = viewModelScope.launch {
        when (e.kind) {
            TrashKind.ITEMS -> liquidity.purgeItem(e.id)
            TrashKind.MONTHS -> liquidity.purgeMonth(e.id)
            TrashKind.BALANCES -> liquidity.purgeBalance(e.id)
            TrashKind.TRANSFERS -> liquidity.purgeTransfer(e.id)
            TrashKind.DEBTS -> debts.purgeDebt(e.id)
            TrashKind.PAYMENTS -> debts.purgePayment(e.id)
        }
        message(R.string.msg_purged)
    }
}

@Composable
fun TrashScreen(onBack: () -> Unit, vm: TrashViewModel = hiltViewModel()) {
    val all by vm.entries.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val f = fmt()
    var tab by remember { mutableStateOf(TrashKind.ITEMS) }
    var purge by remember { mutableStateOf<TrashEntry?>(null) }
    AppScaffold(stringResource(R.string.trash_title), onBack = onBack, snackbar = snackbar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp) {
                TrashKind.entries.forEach { k ->
                    val count = all.count { it.kind == k }
                    Tab(selected = tab == k, onClick = { tab = k }, text = { Text(stringResource(k.title) + if (count > 0) " (${f.integer(count)})" else "") })
                }
            }
            val list = all.filter { it.kind == tab }
            if (list.isEmpty()) EmptyState(stringResource(R.string.trash_empty), stringResource(R.string.trash_empty_message), icon = Icons.Filled.DeleteSweep)
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.kind.name + it.id }) { e ->
                    FinanceCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(trashTitle(e, f), style = MaterialTheme.typography.titleSmall)
                                trashDetail(e, f)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Text(stringResource(R.string.deleted_at, f.dateTime(e.deletedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.restore(e) }) { Icon(Icons.Filled.RestoreFromTrash, stringResource(R.string.restore)) }
                            IconButton(onClick = { purge = e }) { Icon(Icons.Filled.DeleteForever, stringResource(R.string.purge), tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    purge?.let { e ->
        ConfirmDialog(stringResource(R.string.purge_title), stringResource(R.string.purge_message), confirmText = stringResource(R.string.purge), destructive = true,
            onConfirm = { vm.purge(e); purge = null }, onDismiss = { purge = null })
    }
}

@Composable
private fun trashTitle(e: TrashEntry, f: AppFormatter): String = when (e.kind) {
    TrashKind.MONTHS -> e.month?.let { f.month(it) } ?: "-"
    TrashKind.TRANSFERS -> "${e.name ?: "-"} ← ${e.secondName ?: "-"}"
    else -> e.name ?: stringResource(R.string.unknown_item)
}

private fun trashDetail(e: TrashEntry, f: AppFormatter): String? = when (e.kind) {
    TrashKind.BALANCES -> listOfNotNull(e.month?.let { f.month(it) }, f.money(e.amount, e.currency)).joinToString(" • ")
    TrashKind.TRANSFERS, TrashKind.PAYMENTS -> f.money(e.amount, e.currency)
    TrashKind.DEBTS -> e.amount?.let { f.money(it, e.currency) }
    else -> null
}
