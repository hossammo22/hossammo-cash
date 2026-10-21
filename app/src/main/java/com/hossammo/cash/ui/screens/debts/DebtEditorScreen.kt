package com.hossammo.cash.ui.screens.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.data.repo.DebtDraft
import com.hossammo.cash.data.repo.DebtRepository
import com.hossammo.cash.domain.model.DebtDirection
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.ui.components.AmountField
import com.hossammo.cash.ui.components.AppTextField
import com.hossammo.cash.ui.components.ChipsRow
import com.hossammo.cash.ui.components.DateField
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages
import com.hossammo.cash.ui.screens.MessagesViewModel
import com.hossammo.cash.ui.screens.liquidity.errorText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DebtForm(
    val name: String = "",
    val direction: DebtDirection = DebtDirection.OWED_TO_ME,
    val amount: String = "",
    val startDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val notes: String = "",
    val nameError: Boolean = false,
    val amountError: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class DebtEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: DebtRepository,
) : MessagesViewModel() {
    val debtId: String? = savedState["id"]
    val form = MutableStateFlow(
        DebtForm(direction = savedState.get<String>("dir")?.let { runCatching { DebtDirection.valueOf(it) }.getOrNull() } ?: DebtDirection.OWED_TO_ME)
    )
    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved = _saved.receiveAsFlow()

    init {
        if (debtId != null) viewModelScope.launch {
            repo.debtById(debtId)?.let { d ->
                form.value = DebtForm(
                    name = d.name, direction = d.direction, amount = d.originalAmount?.let { Money.money(it).toPlainString() } ?: "",
                    startDate = d.startDate?.let { LocalDate.ofEpochDay(it) }, dueDate = d.dueDate?.let { LocalDate.ofEpochDay(it) },
                    notes = d.notes ?: "", loaded = true,
                )
            }
        } else form.update { it.copy(loaded = true) }
    }

    fun edit(block: (DebtForm) -> DebtForm) = form.update(block)

    fun save() = viewModelScope.launch {
        val f = form.value
        val amount = if (f.amount.isBlank()) null else Money.parseOrNull(f.amount)
        val amountBad = f.amount.isNotBlank() && (amount == null || amount.signum() < 0)
        if (f.name.isBlank() || amountBad) {
            form.update { it.copy(nameError = f.name.isBlank(), amountError = amountBad) }
            return@launch
        }
        when (val r = repo.saveDebt(DebtDraft(debtId, f.name, f.direction, amount, f.startDate, f.dueDate, f.notes))) {
            OpResult.Success -> { message(R.string.msg_saved); _saved.trySend(Unit) }
            is OpResult.Failure -> message(errorText(r.error), true)
            is OpResult.NeedsConfirmation -> Unit
        }
    }
}

@Composable
fun DebtEditorScreen(onBack: () -> Unit, onSaved: () -> Unit, vm: DebtEditorViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    LaunchedEffect(Unit) { vm.saved.collect { onSaved() } }
    AppScaffold(
        title = stringResource(if (vm.debtId == null) R.string.add_debt else R.string.edit_debt),
        onBack = onBack, snackbar = snackbar, showHideToggle = false,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChipsRow(
                listOf(DebtDirection.OWED_TO_ME, DebtDirection.I_OWE), f.direction,
                { stringResource(if (it == DebtDirection.OWED_TO_ME) R.string.debts_owed_to_me else R.string.debts_i_owe) },
                { d -> vm.edit { it.copy(direction = d) } },
            )
            AppTextField(f.name, { v -> vm.edit { it.copy(name = v, nameError = false) } }, stringResource(R.string.debt_name),
                isError = f.nameError, supporting = if (f.nameError) stringResource(R.string.msg_name_required) else null)
            AmountField(f.amount, { v -> vm.edit { it.copy(amount = v, amountError = false) } }, stringResource(R.string.original_amount_optional),
                suffix = "EGP", isError = f.amountError,
                supporting = if (f.amountError) stringResource(R.string.msg_invalid_amount) else stringResource(R.string.amount_can_be_empty))
            DateField(stringResource(R.string.start_date), f.startDate, { d -> vm.edit { it.copy(startDate = d) } })
            DateField(stringResource(R.string.due_date), f.dueDate, { d -> vm.edit { it.copy(dueDate = d) } })
            AppTextField(f.notes, { v -> vm.edit { it.copy(notes = v) } }, stringResource(R.string.notes), singleLine = false, minLines = 3)
            Text(stringResource(R.string.debts_separate_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = vm::save, enabled = f.loaded, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        }
    }
}
