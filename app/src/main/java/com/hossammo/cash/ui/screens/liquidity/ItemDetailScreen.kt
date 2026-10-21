package com.hossammo.cash.ui.screens.liquidity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.hossammo.cash.data.db.entity.MonthlyBalanceEntity
import com.hossammo.cash.data.model.ConfirmReason
import com.hossammo.cash.data.model.OpError
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.data.repo.BalanceDraft
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.domain.calc.ChangeResult
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.ui.SessionState
import com.hossammo.cash.ui.charts.LineChart
import com.hossammo.cash.ui.charts.LineSeries
import com.hossammo.cash.ui.components.ConfirmDialog
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.ItemAvatar
import com.hossammo.cash.ui.components.LabeledValue
import com.hossammo.cash.ui.components.SectionHeader
import com.hossammo.cash.ui.components.TrendBadge
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages
import com.hossammo.cash.ui.screens.MessagesViewModel
import com.hossammo.cash.ui.theme.parseHexColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class ItemDetailState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val row: ItemRow? = null,
    val history: List<Pair<MonthlyBalanceEntity, ChangeResult>> = emptyList(),
    val months: List<MonthKey> = emptyList(),
    val selected: MonthKey? = null,
    val base: String = "EGP",
    val currentRate: BigDecimal? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** منطق مشترك لحفظ رصيد مع تنبيه التكرار. */
abstract class BalanceSavingViewModel(protected val repo: LiquidityRepository) : MessagesViewModel() {
    val pendingDuplicate = MutableStateFlow<Pair<String, BalanceInput>?>(null)

    fun saveBalance(itemId: String, input: BalanceInput, isEdit: Boolean, confirm: Boolean = false, onDone: () -> Unit = {}) = viewModelScope.launch {
        val r = repo.saveBalance(BalanceDraft(input.month, itemId, input.amount, input.lockRate, input.customRate, input.notes), confirmOverwrite = confirm, isEdit = isEdit)
        when (r) {
            OpResult.Success -> { message(R.string.msg_balance_saved); pendingDuplicate.value = null; onDone() }
            is OpResult.NeedsConfirmation -> if (r.reason == ConfirmReason.DUPLICATE_BALANCE) pendingDuplicate.value = itemId to input
            is OpResult.Failure -> message(errorText(r.error), true)
        }
    }

    fun deleteBalance(id: String) = viewModelScope.launch {
        repo.softDeleteBalance(id)
        message(R.string.msg_balance_deleted, undo = { viewModelScope.launch { repo.restoreBalance(id) } })
    }

    fun unlockRate(month: MonthKey, currency: String) = viewModelScope.launch {
        repo.unlockRate(month, currency); message(R.string.msg_rate_unlocked)
    }

    fun lockRate(month: MonthKey, currency: String, rate: BigDecimal?) = viewModelScope.launch {
        when (val r = repo.lockRate(month, currency, rate)) {
            OpResult.Success -> message(R.string.msg_rate_locked)
            is OpResult.Failure -> message(errorText(r.error), true)
            else -> Unit
        }
    }
}

fun errorText(e: OpError): Int = when (e) {
    OpError.INVALID_AMOUNT -> R.string.msg_invalid_amount
    OpError.SAME_ITEM -> R.string.msg_same_item
    OpError.MONTH_EXISTS -> R.string.msg_month_exists
    OpError.MONTH_NOT_FOUND -> R.string.msg_month_not_found
    OpError.ITEM_NOT_FOUND -> R.string.msg_item_not_found
    OpError.MISSING_RATE -> R.string.msg_missing_rate
    OpError.CATEGORY_NOT_EMPTY -> R.string.msg_category_not_empty
    OpError.NAME_REQUIRED -> R.string.msg_name_required
    OpError.NO_PREVIOUS_MONTH -> R.string.msg_no_previous_month
    OpError.UNKNOWN -> R.string.msg_error
}

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repo: LiquidityRepository,
    private val session: SessionState,
) : BalanceSavingViewModel(repo) {
    val itemId: String = checkNotNull(savedState["id"])

    val state: StateFlow<ItemDetailState> = repo.observeData().map { data ->
        val item = data.itemById[itemId] ?: return@map ItemDetailState(loading = false, notFound = true)
        val a = LiquidityAnalyzer(data)
        val selected = session.resolve(a.monthKeys)
        val row = if (selected != null) a.itemRows(selected).first { it.item.id == itemId }
        else ItemRow(item, data.categoryById[item.categoryId], item.locationId?.let { data.locationById[it] }, null, null, null, BigDecimal.ZERO)
        ItemDetailState(
            loading = false, row = row, history = a.itemHistory(itemId).reversed(), months = a.monthKeys, selected = selected,
            base = data.baseCurrency, currentRate = data.rateByCode[item.currencyCode]?.rateToBase, createdAt = item.createdAt, updatedAt = item.updatedAt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailState())

    val deleted = MutableStateFlow(false)

    fun duplicate(suffix: String) = viewModelScope.launch { repo.duplicateItem(itemId, suffix).onSuccess { message(R.string.msg_item_copied) } }
    fun toggleArchive(archived: Boolean) = viewModelScope.launch { repo.setArchived(itemId, !archived); message(if (!archived) R.string.msg_item_archived else R.string.msg_item_unarchived) }
    fun deleteItem() = viewModelScope.launch { repo.softDeleteItem(itemId); deleted.value = true }
}

@Composable
fun ItemDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, onOpenItem: (String) -> Unit, vm: ItemDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by vm.pendingDuplicate.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    LaunchedEffect(deleted) { if (deleted) onBack() }
    val f = fmt()
    var dialog by remember { mutableStateOf<MonthlyBalanceEntity?>(null) }
    var addDialog by remember { mutableStateOf(false) }
    var confirmDeleteItem by remember { mutableStateOf(false) }
    var confirmDeleteBalance by remember { mutableStateOf<String?>(null) }
    var confirmUnlock by remember { mutableStateOf<MonthlyBalanceEntity?>(null) }
    val copySuffix = stringResource(R.string.copy_suffix)
    val row = state.row

    AppScaffold(
        title = row?.item?.name ?: stringResource(R.string.item_details),
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            if (row != null) {
                IconButton(onClick = { onEdit(row.item.id) }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                IconButton(onClick = { vm.duplicate(copySuffix) }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.duplicate)) }
                IconButton(onClick = { vm.toggleArchive(row.item.isArchived) }) { Icon(if (row.item.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive, stringResource(if (row.item.isArchived) R.string.unarchive else R.string.archive)) }
                IconButton(onClick = { confirmDeleteItem = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
            }
        },
        floatingActionButton = {
            if (row != null) ExtendedFloatingActionButton(onClick = { addDialog = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_balance)) })
        },
    ) { padding ->
        if (state.notFound) { EmptyState(stringResource(R.string.msg_item_not_found), "", Modifier.padding(padding)); return@AppScaffold }
        if (row == null) return@AppScaffold
        val item = row.item
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            FinanceCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemAvatar(item.icon, item.colorHex, size = 52)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleLarge)
                        Text(row.category?.name ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                state.selected?.let { m ->
                    LabeledValue(stringResource(R.string.value_in_month, f.month(m)), f.money(row.valueBase, state.base))
                    row.comparison?.let { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.vs_previous_month), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TrendBadge(c.change.status, c.change.change, c.change.percent)
                        }
                        LabeledValue(stringResource(R.string.share_label), f.percent(c.shareOfTotal, signed = false))
                    }
                }
                LabeledValue(stringResource(R.string.location_project), row.location?.name ?: stringResource(R.string.no_location))
                LabeledValue(stringResource(R.string.asset_type), f.assetType(item.assetType))
                LabeledValue(stringResource(R.string.currency), item.currencyCode)
                if (item.currencyCode != state.base) LabeledValue(stringResource(R.string.current_rate), f.rate(state.currentRate))
                LabeledValue(stringResource(R.string.liquidity_level), f.level(item.liquidityLevel))
                LabeledValue(stringResource(R.string.include_in_total), stringResource(if (item.includeInTotal) R.string.yes else R.string.no))
                LabeledValue(stringResource(R.string.status), stringResource(if (item.isArchived) R.string.archived else R.string.active))
                LabeledValue(stringResource(R.string.created_at), f.dateTime(state.createdAt))
                LabeledValue(stringResource(R.string.updated_at), f.dateTime(state.updatedAt))
                item.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            }
            if (state.history.size >= 2) {
                SectionHeader(stringResource(R.string.item_evolution))
                FinanceCard(Modifier.fillMaxWidth()) {
                    val ordered = state.history.reversed()
                    LineChart(
                        listOf(LineSeries(item.name, parseHexColor(item.colorHex), ordered.map { it.first.valueBase?.toFloat() })),
                        ordered.map { f.monthShort(MonthKey(it.first.monthKey)) },
                        description = stringResource(R.string.item_evolution),
                    )
                }
            }
            SectionHeader(stringResource(R.string.full_history))
            if (state.history.isEmpty()) {
                Text(stringResource(R.string.no_history), color = MaterialTheme.colorScheme.outline)
            }
            state.history.forEach { (b, c) ->
                FinanceCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.month(MonthKey(b.monthKey)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (b.rateLocked) IconButton(onClick = { confirmUnlock = b }) { Icon(Icons.Filled.Lock, stringResource(R.string.unlock_rate), tint = MaterialTheme.colorScheme.secondary) }
                        else if (item.currencyCode != state.base) IconButton(onClick = { vm.lockRate(MonthKey(b.monthKey), item.currencyCode, null) }) { Icon(Icons.Filled.LockOpen, stringResource(R.string.lock_rate)) }
                        IconButton(onClick = { dialog = b }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                        IconButton(onClick = { confirmDeleteBalance = b.id }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                    if (item.currencyCode != state.base) {
                        LabeledValue(stringResource(R.string.original_value), f.money(b.amountOriginal, item.currencyCode))
                        LabeledValue(stringResource(R.string.rate_used), "${f.rate(b.exchangeRate)} • ${f.rateSource(b.rateSource)}")
                    }
                    LabeledValue(stringResource(R.string.value_in_base), f.money(b.valueBase, state.base))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.vs_previous_month), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TrendBadge(c.status, c.change, c.percent)
                    }
                    b.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.last_modified, f.dateTime(b.updatedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    val item = row?.item
    if (item != null && (dialog != null || addDialog)) {
        val b = dialog
        val defaultMonth = b?.let { MonthKey(it.monthKey) } ?: state.selected ?: MonthKey.of(java.time.LocalDate.now().year, java.time.LocalDate.now().monthValue)
        BalanceDialog(
            itemName = item.name, currency = item.currencyCode, baseCurrency = state.base, months = state.months,
            initialMonth = defaultMonth, initialAmount = b?.amountOriginal, initialLocked = b?.rateLocked ?: false,
            initialRate = b?.exchangeRate, currentRate = state.currentRate, initialNotes = b?.notes, allowMonthChange = b == null,
            onDismiss = { dialog = null; addDialog = false },
            onSave = { input -> vm.saveBalance(item.id, input, isEdit = b != null) { dialog = null; addDialog = false } },
        )
    }
    pending?.let { (id, input) ->
        ConfirmDialog(stringResource(R.string.duplicate_balance_title), stringResource(R.string.duplicate_balance_message), confirmText = stringResource(R.string.overwrite),
            onConfirm = { vm.saveBalance(id, input, isEdit = false, confirm = true) { dialog = null; addDialog = false } }, onDismiss = { vm.pendingDuplicate.value = null })
    }
    if (confirmDeleteItem) {
        ConfirmDialog(stringResource(R.string.delete_item_title), stringResource(R.string.delete_item_message), confirmText = stringResource(R.string.delete), destructive = true,
            onConfirm = { confirmDeleteItem = false; vm.deleteItem() }, onDismiss = { confirmDeleteItem = false })
    }
    confirmDeleteBalance?.let { id ->
        ConfirmDialog(stringResource(R.string.delete_balance_title), stringResource(R.string.delete_balance_message), confirmText = stringResource(R.string.delete), destructive = true,
            onConfirm = { vm.deleteBalance(id); confirmDeleteBalance = null }, onDismiss = { confirmDeleteBalance = null })
    }
    confirmUnlock?.let { b ->
        ConfirmDialog(stringResource(R.string.unlock_rate_title), stringResource(R.string.unlock_rate_message), confirmText = stringResource(R.string.unlock_rate),
            onConfirm = { vm.unlockRate(MonthKey(b.monthKey), b.currencyCode); confirmUnlock = null }, onDismiss = { confirmUnlock = null })
    }
}
