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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.hossammo.cash.data.db.entity.LiquidityItemEntity
import com.hossammo.cash.data.db.entity.TransferEntity
import com.hossammo.cash.data.model.ConfirmReason
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.data.repo.AppSettingsStore
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.data.repo.TransferDraft
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.ui.components.AmountField
import com.hossammo.cash.ui.components.AppTextField
import com.hossammo.cash.ui.components.ConfirmDialog
import com.hossammo.cash.ui.components.DropdownField
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.LabeledValue
import com.hossammo.cash.ui.components.SearchField
import com.hossammo.cash.ui.components.SwitchRow
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

data class TransfersState(
    val transfers: List<TransferEntity> = emptyList(),
    val items: List<LiquidityItemEntity> = emptyList(),
    val itemNames: Map<String, String> = emptyMap(),
    val deductFees: Boolean = true,
    val query: String = "",
)

@HiltViewModel
class TransfersViewModel @Inject constructor(private val repo: LiquidityRepository, private val settings: AppSettingsStore) : MessagesViewModel() {
    private val query = MutableStateFlow("")
    val pendingOverdraft = MutableStateFlow<TransferDraft?>(null)
    val state: StateFlow<TransfersState> = combine(repo.observeData(), settings.deductTransferFees, query) { data, deduct, q ->
        val names = data.items.associate { it.id to it.name }
        TransfersState(
            transfers = data.transfers.filter { t -> q.isBlank() || (names[t.fromItemId] ?: "").contains(q, true) || (names[t.toItemId] ?: "").contains(q, true) || (t.reference ?: "").contains(q, true) || (t.notes ?: "").contains(q, true) },
            items = data.items.filter { !it.isArchived }, itemNames = names, deductFees = deduct, query = q,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransfersState())

    fun setQuery(q: String) { query.value = q }
    fun setDeduct(v: Boolean) = viewModelScope.launch { settings.setDeductTransferFees(v) }

    fun add(d: TransferDraft, confirm: Boolean = false, onDone: () -> Unit) = viewModelScope.launch {
        when (val r = repo.addTransfer(d, confirm)) {
            OpResult.Success -> { message(R.string.msg_transfer_added); pendingOverdraft.value = null; onDone() }
            is OpResult.NeedsConfirmation -> if (r.reason == ConfirmReason.EXCEEDS_BALANCE) pendingOverdraft.value = d
            is OpResult.Failure -> message(errorText(r.error), true)
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        repo.softDeleteTransfer(id)
        message(R.string.msg_transfer_deleted, undo = { viewModelScope.launch { repo.restoreTransfer(id) } })
    }
}

@Composable
fun TransfersScreen(onBack: () -> Unit, vm: TransfersViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val overdraft by vm.pendingOverdraft.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val f = fmt()
    var add by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    AppScaffold(
        title = stringResource(R.string.transfers_title), onBack = onBack, snackbar = snackbar,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { add = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_transfer)) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FinanceCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.transfer_note_body), style = MaterialTheme.typography.bodySmall)
                SwitchRow(stringResource(R.string.deduct_fees), s.deductFees, vm::setDeduct, subtitle = stringResource(R.string.deduct_fees_hint))
            }
            SearchField(s.query, vm::setQuery, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (s.transfers.isEmpty()) EmptyState(stringResource(R.string.no_transfers_title), stringResource(R.string.no_transfers_message), icon = Icons.Filled.SwapHoriz)
            LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.transfers, key = { it.id }) { t ->
                    FinanceCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.itemNames[t.fromItemId] ?: "-", style = MaterialTheme.typography.titleSmall)
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.padding(horizontal = 6.dp))
                            Text(s.itemNames[t.toItemId] ?: "-", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { deleteId = t.id }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                        }
                        LabeledValue(stringResource(R.string.col_amount), f.money(t.amount, t.currencyCode))
                        if (t.receivedAmount.compareTo(t.amount) != 0) LabeledValue(stringResource(R.string.received_amount), f.money(t.receivedAmount))
                        if (t.fee.signum() > 0) LabeledValue(stringResource(R.string.col_fee), f.money(t.fee, t.currencyCode) + if (t.deductFee) "" else " (${stringResource(R.string.not_deducted)})")
                        LabeledValue(stringResource(R.string.col_date), f.dateFromEpochDay(t.transferDate))
                        t.reference?.let { LabeledValue(stringResource(R.string.col_reference), it) }
                        t.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (!t.appliedToBalances) Text(stringResource(R.string.transfer_not_applied), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
    if (add) TransferDialog(s.items, onDismiss = { add = false }) { d -> vm.add(d) { add = false } }
    overdraft?.let { d ->
        ConfirmDialog(stringResource(R.string.exceeds_balance_title), stringResource(R.string.exceeds_balance_message), confirmText = stringResource(R.string.continue_anyway),
            onConfirm = { vm.add(d, confirm = true) { add = false } }, onDismiss = { vm.pendingOverdraft.value = null })
    }
    deleteId?.let { id ->
        ConfirmDialog(stringResource(R.string.delete_transfer_title), stringResource(R.string.delete_transfer_message), confirmText = stringResource(R.string.delete), destructive = true,
            onConfirm = { vm.delete(id); deleteId = null }, onDismiss = { deleteId = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDialog(items: List<LiquidityItemEntity>, onDismiss: () -> Unit, onSave: (TransferDraft) -> Unit) {
    val f = fmt()
    var from by remember { mutableStateOf(items.firstOrNull()) }
    var to by remember { mutableStateOf(items.getOrNull(1)) }
    var amount by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var apply by remember { mutableStateOf(true) }
    var pickDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_transfer)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DropdownField(stringResource(R.string.from_item), items, from, { "${it.name} (${it.currencyCode})" }, { from = it; error = null })
                DropdownField(stringResource(R.string.to_item), items, to, { "${it.name} (${it.currencyCode})" }, { to = it; error = null })
                AmountField(amount, { amount = it; error = null }, stringResource(R.string.col_amount), suffix = from?.currencyCode)
                AmountField(fee, { fee = it }, stringResource(R.string.fee_optional), suffix = from?.currencyCode)
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.date_value, f.date(date))) }
                AppTextField(reference, { reference = it }, stringResource(R.string.reference_optional))
                AppTextField(notes, { notes = it }, stringResource(R.string.notes), singleLine = false)
                SwitchRow(stringResource(R.string.apply_to_balances), apply, { apply = it }, subtitle = stringResource(R.string.apply_to_balances_hint))
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = Money.parseOrNull(amount)
                val fe = if (fee.isBlank()) java.math.BigDecimal.ZERO else Money.parseOrNull(fee)
                when {
                    from == null || to == null -> error = R.string.msg_select_items
                    from!!.id == to!!.id -> error = R.string.msg_same_item
                    a == null || a.signum() <= 0 -> error = R.string.msg_invalid_amount
                    fe == null || fe.signum() < 0 -> error = R.string.msg_invalid_amount
                    else -> onSave(TransferDraft(from!!.id, to!!.id, a, fe, date, reference, notes, apply))
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (pickDate) {
        val dp = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { dp.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; pickDate = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(dp) }
    }
}
