package com.hossammo.cash.ui.screens.debts

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.db.entity.AttachmentEntity
import com.hossammo.cash.data.db.entity.DebtPaymentEntity
import com.hossammo.cash.data.model.ConfirmReason
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.data.repo.DebtRepository
import com.hossammo.cash.data.repo.PaymentDraft
import com.hossammo.cash.domain.calc.DebtCalculator
import com.hossammo.cash.domain.model.DebtDirection
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.ui.components.AmountField
import com.hossammo.cash.ui.components.AppTextField
import com.hossammo.cash.ui.components.ConfirmDialog
import com.hossammo.cash.ui.components.DateField
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.LabeledValue
import com.hossammo.cash.ui.components.SectionHeader
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages
import com.hossammo.cash.ui.screens.MessagesViewModel
import com.hossammo.cash.ui.screens.liquidity.errorText
import com.hossammo.cash.ui.theme.LocalFinanceColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class DebtDetailState(
    val row: DebtRow? = null,
    val attachments: List<AttachmentEntity> = emptyList(),
    val missing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: DebtRepository,
) : MessagesViewModel() {
    val debtId: String = checkNotNull(savedState["id"])
    val pendingOverpay = MutableStateFlow<PaymentDraft?>(null)

    val state: StateFlow<DebtDetailState?> = combine(
        repo.observeDebts(),
        repo.observeAttachments(DebtRepository.OWNER_DEBT, debtId),
    ) { debts, atts ->
        val d = debts.firstOrNull { it.debt.id == debtId } ?: return@combine DebtDetailState(missing = true)
        val snap = d.toSnapshot()
        DebtDetailState(
            row = DebtRow(d.copy(payments = d.payments.sortedByDescending { it.paymentDate }), snap, DebtCalculator.figures(snap, LocalDate.now())),
            attachments = atts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun savePayment(p: PaymentDraft, confirm: Boolean = false, onDone: () -> Unit) = viewModelScope.launch {
        when (val r = repo.savePayment(p, confirm)) {
            OpResult.Success -> { pendingOverpay.value = null; message(R.string.msg_payment_saved); onDone() }
            is OpResult.NeedsConfirmation -> if (r.reason == ConfirmReason.PAYMENT_EXCEEDS_ORIGINAL) pendingOverpay.value = p
            is OpResult.Failure -> message(errorText(r.error), true)
        }
    }

    fun deletePayment(id: String) = viewModelScope.launch {
        repo.softDeletePayment(id)
        message(R.string.msg_payment_deleted, undo = { viewModelScope.launch { repo.restorePayment(id) } })
    }

    fun deleteDebt(onDone: () -> Unit) = viewModelScope.launch { repo.softDeleteDebt(debtId); onDone() }

    fun addAttachment(uri: Uri) = viewModelScope.launch {
        repo.addAttachment(DebtRepository.OWNER_DEBT, debtId, uri)
            .onSuccess { message(R.string.msg_attachment_added) }
            .onFailure { message(R.string.msg_attachment_failed, true) }
    }

    fun attachmentOpenFailed() = message(R.string.msg_attachment_open_failed, true)

    fun deleteAttachment(id: String) = viewModelScope.launch { repo.deleteAttachment(id); message(R.string.msg_attachment_deleted) }
}

@Composable
fun DebtDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, vm: DebtDetailViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val overpay by vm.pendingOverpay.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val f = fmt()
    val fc = LocalFinanceColors.current
    val context = LocalContext.current
    var paymentDialog by remember { mutableStateOf<DebtPaymentEntity?>(null) }
    var addPayment by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deletePaymentId by remember { mutableStateOf<String?>(null) }
    var deleteAttachment by remember { mutableStateOf<AttachmentEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::addAttachment) }

    val row = st?.row
    AppScaffold(
        title = row?.data?.debt?.name ?: stringResource(R.string.debt_details),
        onBack = onBack, snackbar = snackbar,
        actions = {
            if (row != null) {
                IconButton(onClick = { onEdit(vm.debtId) }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
            }
        },
        floatingActionButton = {
            if (row != null) ExtendedFloatingActionButton(
                onClick = { addPayment = true }, icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(if (row.data.debt.direction == DebtDirection.OWED_TO_ME) R.string.add_collection else R.string.add_payment)) },
            )
        },
    ) { padding ->
        if (st?.missing == true) {
            EmptyState(stringResource(R.string.debt_not_found), stringResource(R.string.debt_not_found_message), Modifier.padding(padding))
            return@AppScaffold
        }
        if (row == null) return@AppScaffold
        val d = row.data.debt
        val fig = row.figures
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FinanceCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (d.direction == DebtDirection.OWED_TO_ME) R.string.debts_owed_to_me else R.string.debts_i_owe),
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(f.debtStatus(fig.status), color = debtStatusColor(fig.status), style = MaterialTheme.typography.labelLarge)
                    }
                    LabeledValue(stringResource(R.string.original_amount),
                        if (d.originalAmount == null) stringResource(R.string.amount_not_entered) else f.money(fig.original, d.currencyCode))
                    LabeledValue(stringResource(if (d.direction == DebtDirection.OWED_TO_ME) R.string.collected else R.string.paid_by_me), f.money(fig.paid, d.currencyCode))
                    LabeledValue(stringResource(R.string.remaining), f.money(fig.remaining, d.currencyCode), valueColor = if (fig.overpaid) fc.decrease else Color.Unspecified)
                    LabeledValue(stringResource(R.string.start_date), f.dateFromEpochDay(d.startDate))
                    LabeledValue(stringResource(R.string.due_date), f.dateFromEpochDay(d.dueDate))
                    LabeledValue(stringResource(R.string.payments_count), f.integer(row.data.payments.size))
                    d.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                    if (fig.overpaid) Text(stringResource(R.string.overpaid_note), color = fc.decrease, style = MaterialTheme.typography.labelSmall)
                }
            }
            item { SectionHeader(stringResource(R.string.payments_log)) }
            if (row.data.payments.isEmpty()) {
                item { Text(stringResource(R.string.no_payments), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(row.data.payments, key = { it.id }) { p ->
                FinanceCard(Modifier.fillMaxWidth(), onClick = { paymentDialog = p }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Payments, null, tint = MaterialTheme.colorScheme.secondary)
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(f.money(p.amount, d.currencyCode), style = MaterialTheme.typography.titleSmall)
                            Text(f.dateFromEpochDay(p.paymentDate), style = MaterialTheme.typography.bodySmall)
                            listOfNotNull(p.method, p.reference).takeIf { it.isNotEmpty() }?.let {
                                Text(it.joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            p.notes?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        }
                        IconButton(onClick = { deletePaymentId = p.id }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                }
            }
            item {
                SectionHeader(stringResource(R.string.attachments)) {
                    TextButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }) {
                        Icon(Icons.Filled.AttachFile, null); Text(stringResource(R.string.add_attachment))
                    }
                }
                Text(stringResource(R.string.attachments_local_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(st?.attachments.orEmpty(), key = { it.id }) { a ->
                FinanceCard(Modifier.fillMaxWidth(), onClick = { openAttachment(context, a) { vm.attachmentOpenFailed() } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AttachFile, null)
                        Text(a.fileName, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { deleteAttachment = a }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                }
            }
        }
    }

    if (row != null && (addPayment || paymentDialog != null)) {
        PaymentDialog(
            debtId = vm.debtId, existing = paymentDialog, currency = row.data.debt.currencyCode,
            onDismiss = { addPayment = false; paymentDialog = null },
            onSave = { draft -> vm.savePayment(draft) { addPayment = false; paymentDialog = null } },
        )
    }
    overpay?.let { draft ->
        ConfirmDialog(stringResource(R.string.overpay_title), stringResource(R.string.overpay_message), confirmText = stringResource(R.string.continue_anyway),
            onConfirm = { vm.savePayment(draft, confirm = true) { addPayment = false; paymentDialog = null } },
            onDismiss = { vm.pendingOverpay.value = null })
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.delete_debt_title), stringResource(R.string.delete_debt_message), confirmText = stringResource(R.string.delete), destructive = true,
            onConfirm = { confirmDelete = false; vm.deleteDebt(onBack) }, onDismiss = { confirmDelete = false })
    }
    deletePaymentId?.let { id ->
        ConfirmDialog(stringResource(R.string.delete_payment_title), stringResource(R.string.delete_payment_message), confirmText = stringResource(R.string.delete), destructive = true,
            onConfirm = { vm.deletePayment(id); deletePaymentId = null }, onDismiss = { deletePaymentId = null })
    }
    deleteAttachment?.let { a ->
        ConfirmDialog(stringResource(R.string.delete_attachment_title), a.fileName, confirmText = stringResource(R.string.delete), destructive = true,
            onConfirm = { vm.deleteAttachment(a.id); deleteAttachment = null }, onDismiss = { deleteAttachment = null })
    }
}

private fun openAttachment(context: android.content.Context, a: AttachmentEntity, onError: () -> Unit) {
    val file = File(a.localPath)
    if (!file.exists()) { onError(); return }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mimeType ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, a.fileName))
    } catch (_: ActivityNotFoundException) {
        onError()
    }
}

@Composable
fun PaymentDialog(debtId: String, existing: DebtPaymentEntity?, currency: String, onDismiss: () -> Unit, onSave: (PaymentDraft) -> Unit) {
    var amount by remember { mutableStateOf(existing?.amount?.let { Money.money(it).toPlainString() } ?: "") }
    var date by remember { mutableStateOf(existing?.paymentDate?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()) }
    var method by remember { mutableStateOf(existing?.method ?: "") }
    var reference by remember { mutableStateOf(existing?.reference ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.add_payment else R.string.edit_payment)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AmountField(amount, { amount = it; error = false }, stringResource(R.string.col_amount), suffix = currency, isError = error,
                    supporting = if (error) stringResource(R.string.msg_invalid_amount) else null)
                DateField(stringResource(R.string.col_date), date, { d -> if (d != null) date = d }, clearable = false)
                AppTextField(method, { method = it }, stringResource(R.string.payment_method_optional))
                AppTextField(reference, { reference = it }, stringResource(R.string.reference_optional))
                AppTextField(notes, { notes = it }, stringResource(R.string.notes), singleLine = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = Money.parseOrNull(amount)
                if (a == null || a.signum() <= 0) error = true
                else onSave(PaymentDraft(existing?.id, debtId, a, date, method, reference, notes))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
