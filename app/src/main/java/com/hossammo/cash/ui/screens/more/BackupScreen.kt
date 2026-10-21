package com.hossammo.cash.ui.screens.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.backup.BackupFormat
import com.hossammo.cash.data.backup.BackupInspectResult
import com.hossammo.cash.data.backup.BackupManager
import com.hossammo.cash.data.backup.BackupMeta
import com.hossammo.cash.data.backup.BackupPayload
import com.hossammo.cash.data.db.entity.BackupHistoryEntity
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.ui.components.DropdownField
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.LabeledValue
import com.hossammo.cash.ui.components.SectionHeader
import com.hossammo.cash.ui.components.SwitchRow
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.MessagesViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

const val MIN_BACKUP_PASSWORD = 8

data class BackupUiState(
    val months: List<MonthKey> = emptyList(),
    val history: List<BackupHistoryEntity> = emptyList(),
    val lastBackup: BackupHistoryEntity? = null,
    val safetyCount: Int = 0,
)

/** ما تم فحصه من ملف الاستعادة بانتظار التأكيد. */
data class PendingRestore(val meta: BackupMeta, val payload: BackupPayload, val password: CharArray)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupManager,
    liquidity: LiquidityRepository,
) : MessagesViewModel() {
    val busy = MutableStateFlow(false)
    val pendingRestore = MutableStateFlow<PendingRestore?>(null)
    private var pendingCreate: Pair<CharArray, Pair<MonthKey, MonthKey>?>? = null

    val state: StateFlow<BackupUiState> = combine(
        liquidity.observeData().map { LiquidityAnalyzer(it).monthKeys },
        backup.observeHistory(),
        backup.observeLastSuccessful(),
    ) { months, history, last -> BackupUiState(months, history.take(20), last, backup.safetyBackups().size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun prepareCreate(password: CharArray, period: Pair<MonthKey, MonthKey>?) { pendingCreate = password to period }

    fun create(uri: Uri) = viewModelScope.launch {
        val (pw, period) = pendingCreate ?: return@launch
        busy.value = true
        backup.createBackup(uri, pw, period)
            .onSuccess { message(R.string.msg_backup_created) }
            .onFailure { message(R.string.msg_backup_failed, true) }
        pw.fill(' ')
        pendingCreate = null
        busy.value = false
    }

    fun inspect(uri: Uri, password: CharArray) = viewModelScope.launch {
        busy.value = true
        when (val r = backup.inspectUri(uri, password)) {
            is BackupInspectResult.Valid -> pendingRestore.value = PendingRestore(r.meta, r.payload, password)
            BackupInspectResult.NotABackup -> message(R.string.backup_not_valid, true)
            BackupInspectResult.WrongPasswordOrCorrupt -> message(R.string.backup_wrong_password, true)
            BackupInspectResult.NewerVersion -> message(R.string.backup_newer_version, true)
            BackupInspectResult.ChecksumMismatch -> message(R.string.backup_checksum_failed, true)
        }
        busy.value = false
    }

    fun confirmRestore() = viewModelScope.launch {
        val p = pendingRestore.value ?: return@launch
        pendingRestore.value = null
        busy.value = true
        backup.restore(p.payload, p.password)
            .onSuccess { message(R.string.msg_restore_done) }
            .onFailure { message(R.string.msg_restore_failed, true) }
        p.password.fill(' ')
        busy.value = false
    }

    fun cancelRestore() { pendingRestore.value?.password?.fill(' '); pendingRestore.value = null }
}

@Composable
fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    com.hossammo.cash.ui.screens.CollectMessages(vm.messages, snackbar)
    val f = fmt()

    var createDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val creator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFormat.MIME)) { uri -> uri?.let(vm::create) }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    SettingsPage(stringResource(R.string.backup_title), onBack, snackbar) {
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_local_note), style = MaterialTheme.typography.bodySmall)
            LabeledValue(stringResource(R.string.last_backup), s.lastBackup?.let { f.dateTime(it.createdAt) } ?: stringResource(R.string.never))
            LabeledValue(stringResource(R.string.safety_backups), f.integer(s.safetyCount))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = { createDialog = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Backup, null); Text(" " + stringResource(R.string.create_backup))
        }
        OutlinedButton(onClick = { opener.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Restore, null); Text(" " + stringResource(R.string.restore_backup))
        }
        SectionHeader(stringResource(R.string.backup_history))
        if (s.history.isEmpty()) Text(stringResource(R.string.no_backups_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.history.forEach { h ->
            LabeledValue(
                "${f.dateTime(h.createdAt)} • ${backupTypeLabel(h.type)}",
                if (h.success) stringResource(R.string.success) else stringResource(R.string.failed),
                valueColor = if (h.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }

    if (createDialog) {
        CreateBackupDialog(
            months = s.months,
            onDismiss = { createDialog = false },
            onCreate = { pw, period ->
                createDialog = false
                vm.prepareCreate(pw, period)
                val stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                val suffix = if (period == null) "full" else "period_${period.first.value}_${period.second.value}"
                creator.launch("HossamMoCash_${stamp}_$suffix.${BackupFormat.FILE_EXTENSION}")
            },
        )
    }
    restoreUri?.let { uri ->
        PasswordDialog(
            title = stringResource(R.string.backup_password),
            onDismiss = { restoreUri = null },
            onOk = { pw -> restoreUri = null; vm.inspect(uri, pw) },
        )
    }
    pending?.let { p -> RestorePreviewDialog(p.meta, onConfirm = vm::confirmRestore, onDismiss = vm::cancelRestore) }
}

@Composable
fun backupTypeLabel(type: String) = stringResource(
    when (type) {
        BackupManager.TYPE_FULL -> R.string.backup_type_full
        BackupManager.TYPE_PERIOD -> R.string.backup_type_period
        BackupManager.TYPE_AUTO -> R.string.backup_type_auto
        else -> R.string.backup_type_restore
    }
)

@Composable
private fun CreateBackupDialog(months: List<MonthKey>, onDismiss: () -> Unit, onCreate: (CharArray, Pair<MonthKey, MonthKey>?) -> Unit) {
    val f = fmt()
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var periodOnly by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf(months.firstOrNull()) }
    var to by remember { mutableStateOf(months.lastOrNull()) }
    val tooShort = pw.length < MIN_BACKUP_PASSWORD
    val mismatch = pw != pw2
    val badPeriod = periodOnly && (from == null || to == null || from!! > to!!)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_backup)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.backup_password_hint), style = MaterialTheme.typography.bodySmall)
                PasswordField(pw, { pw = it }, stringResource(R.string.backup_password), isError = pw.isNotEmpty() && tooShort)
                PasswordField(pw2, { pw2 = it }, stringResource(R.string.confirm_password), isError = pw2.isNotEmpty() && mismatch)
                SwitchRow(stringResource(R.string.backup_period_only), periodOnly, { periodOnly = it }, enabled = months.isNotEmpty())
                if (periodOnly) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DropdownField(stringResource(R.string.from_month), months, from, { f.month(it) }, { from = it }, Modifier.weight(1f))
                        DropdownField(stringResource(R.string.to_month), months, to, { f.month(it) }, { to = it }, Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(pw.toCharArray(), if (periodOnly) from!! to to!! else null) }, enabled = !tooShort && !mismatch && !badPeriod) {
                Text(stringResource(R.string.continue_text))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean = false) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, isError = isError, modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

@Composable
private fun PasswordDialog(title: String, onDismiss: () -> Unit, onOk: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { PasswordField(pw, { pw = it }, stringResource(R.string.backup_password)) },
        confirmButton = { Button(onClick = { onOk(pw.toCharArray()) }, enabled = pw.isNotEmpty()) { Text(stringResource(R.string.continue_text)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** معاينة النسخة ثم تأكيد قوي بكتابة كلمة "استعادة". */
@Composable
private fun RestorePreviewDialog(meta: BackupMeta, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val f = fmt()
    val phrase = stringResource(R.string.restore_confirm_phrase)
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_preview)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LabeledValue(stringResource(R.string.backup_type), backupTypeLabel(meta.type))
                LabeledValue(stringResource(R.string.created_at), f.dateTime(meta.createdAt))
                LabeledValue(stringResource(R.string.app_version), meta.appVersion)
                if (meta.periodFrom != null && meta.periodTo != null) {
                    LabeledValue(stringResource(R.string.period), "${f.month(MonthKey(meta.periodFrom))} — ${f.month(MonthKey(meta.periodTo))}")
                }
                meta.counts.forEach { (k, v) -> LabeledValue(countLabel(k), f.integer(v)) }
                Text(
                    stringResource(if (meta.type == BackupManager.TYPE_FULL) R.string.restore_full_warning else R.string.restore_merge_note),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(R.string.restore_type_to_confirm, phrase), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = typed.trim() == phrase) { Text(stringResource(R.string.restore_backup)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun countLabel(key: String): String = when (key) {
    "items" -> stringResource(R.string.count_items)
    "months" -> stringResource(R.string.count_months)
    "balances" -> stringResource(R.string.count_balances)
    "transfers" -> stringResource(R.string.count_transfers)
    "debts" -> stringResource(R.string.count_debts)
    "payments" -> stringResource(R.string.count_payments)
    "attachments" -> stringResource(R.string.count_attachments)
    else -> key
}
