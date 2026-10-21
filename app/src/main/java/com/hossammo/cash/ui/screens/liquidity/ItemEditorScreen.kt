package com.hossammo.cash.ui.screens.liquidity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.db.entity.LocationEntity
import com.hossammo.cash.data.db.entity.SavingsCategoryEntity
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.data.repo.BalanceDraft
import com.hossammo.cash.data.repo.ItemDraft
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.domain.model.AssetType
import com.hossammo.cash.domain.model.LiquidityLevel
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.ui.SessionState
import com.hossammo.cash.ui.components.AmountField
import com.hossammo.cash.ui.components.AppTextField
import com.hossammo.cash.ui.components.ColorPicker
import com.hossammo.cash.ui.components.ConfirmDialog
import com.hossammo.cash.ui.components.DropdownField
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.IconPicker
import com.hossammo.cash.ui.components.SectionHeader
import com.hossammo.cash.ui.components.SwitchRow
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages
import com.hossammo.cash.ui.screens.MessagesViewModel
import com.hossammo.cash.ui.theme.ItemPalette
import com.hossammo.cash.ui.theme.parseHexColor
import com.hossammo.cash.util.Labels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemForm(
    val name: String = "",
    val categoryId: String? = null,
    val location: String = "",
    val assetType: AssetType = AssetType.BANK_ACCOUNT,
    val currency: String = "EGP",
    val balance: String = "",
    val lockRate: Boolean = false,
    val customRate: String = "",
    val color: String = ItemPalette.first(),
    val icon: String = "savings",
    val level: LiquidityLevel = LiquidityLevel.UNSPECIFIED,
    val includeInTotal: Boolean = true,
    val isVariable: Boolean = false,
    val notes: String = "",
)

data class ItemEditorState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val form: ItemForm = ItemForm(),
    val original: ItemForm = ItemForm(),
    val categories: List<SavingsCategoryEntity> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val month: MonthKey? = null,
    val base: String = "EGP",
    val currentRate: java.math.BigDecimal? = null,
    val nameError: Boolean = false,
    val saved: Boolean = false,
    val confirmDuplicate: Boolean = false,
) {
    val dirty: Boolean get() = form != original
}

val CommonCurrencies = listOf("EGP", "USD", "SAR", "KWD", "EUR", "AED", "GBP", "QAR", "OMR", "BHD", "JOD", "CHF", "CNY", "TRY")

@HiltViewModel
class ItemEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: LiquidityRepository,
    private val session: SessionState,
) : MessagesViewModel() {
    private val itemId: String? = savedState["id"]
    private val _state = MutableStateFlow(ItemEditorState())
    val state: StateFlow<ItemEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val data = repo.observeData().first()
            val month = session.resolve(data.months.map { MonthKey(it.monthKey) }.sorted())
            val item = itemId?.let { data.itemById[it] }
            val form = if (item != null) ItemForm(
                name = item.name, categoryId = item.categoryId, location = item.locationId?.let { data.locationById[it]?.name } ?: "",
                assetType = item.assetType, currency = item.currencyCode, color = item.colorHex, icon = item.icon, level = item.liquidityLevel,
                includeInTotal = item.includeInTotal, isVariable = item.isVariable, notes = item.notes ?: "",
            ) else ItemForm(categoryId = data.categories.firstOrNull()?.id)
            _state.value = ItemEditorState(
                loading = false, isEdit = item != null, form = form, original = form, categories = data.categories,
                locations = data.locations, month = month, base = data.baseCurrency,
            )
            updateRate(form.currency)
        }
    }

    private fun updateRate(code: String) = viewModelScope.launch {
        val data = repo.observeData().first()
        _state.value = _state.value.copy(currentRate = data.rateByCode[code.uppercase()]?.rateToBase)
    }

    fun update(transform: (ItemForm) -> ItemForm) {
        val old = _state.value.form
        val new = transform(old)
        _state.value = _state.value.copy(form = new, nameError = false)
        if (new.currency != old.currency) updateRate(new.currency)
    }

    fun save(confirmOverwrite: Boolean = false) = viewModelScope.launch {
        val s = _state.value
        val f = s.form
        if (f.name.isBlank()) { _state.value = s.copy(nameError = true); message(R.string.msg_name_required, true); return@launch }
        val catId = f.categoryId ?: run { message(R.string.msg_category_required, true); return@launch }
        val amount = Money.parseOrNull(f.balance)
        if (f.balance.isNotBlank() && (amount == null || amount.signum() < 0)) { message(R.string.msg_invalid_amount, true); return@launch }
        val customRate = Money.parseOrNull(f.customRate)
        if (f.lockRate && f.customRate.isNotBlank() && (customRate == null || customRate.signum() <= 0)) { message(R.string.msg_invalid_rate, true); return@launch }
        val draft = ItemDraft(itemId, f.name, catId, f.location, f.assetType, f.currency, f.color, f.icon, f.level, f.includeInTotal, f.isVariable, f.notes)
        val month = s.month ?: MonthKey.of(java.time.LocalDate.now().year, java.time.LocalDate.now().monthValue)
        // التحقق من التكرار قبل الحفظ إذا أدخل المستخدم رصيدًا لشهر به رصيد مسبق
        if (amount != null && itemId != null && !confirmOverwrite && repo.hasBalance(month, itemId)) {
            _state.value = s.copy(confirmDuplicate = true); return@launch
        }
        val result = repo.saveItem(draft, amount?.let { BalanceDraft(month, itemId ?: "", it, f.lockRate, customRate, null) })
        result.onSuccess {
            message(R.string.msg_saved)
            _state.value = _state.value.copy(saved = true, original = f, confirmDuplicate = false)
        }.onFailure { e ->
            val err = (e as? com.hossammo.cash.data.repo.OpException)?.error
            message(if (err == com.hossammo.cash.data.model.OpError.MISSING_RATE) R.string.msg_missing_rate else R.string.msg_error, true)
            _state.value = _state.value.copy(confirmDuplicate = false)
        }
    }

    fun dismissDuplicate() { _state.value = _state.value.copy(confirmDuplicate = false) }
}

@Composable
fun ItemEditorScreen(onBack: () -> Unit, onSaved: () -> Unit, vm: ItemEditorViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val f = fmt()
    var confirmLeave by remember { mutableStateOf(false) }
    val tryBack = { if (state.dirty) confirmLeave = true else onBack() }
    BackHandler(enabled = state.dirty) { confirmLeave = true }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    val form = state.form
    val isForeign = !form.currency.equals(state.base, true)

    AppScaffold(title = stringResource(if (state.isEdit) R.string.edit_item else R.string.add_liquidity_item), onBack = tryBack, snackbar = snackbar, showHideToggle = false) { padding ->
        if (state.loading) return@AppScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppTextField(form.name, { v -> vm.update { it.copy(name = v) } }, stringResource(R.string.item_name), isError = state.nameError, supporting = if (state.nameError) stringResource(R.string.msg_name_required) else null)
            DropdownField(stringResource(R.string.category), state.categories, state.categories.firstOrNull { it.id == form.categoryId }, { it.name }, { c -> vm.update { it.copy(categoryId = c.id) } })
            AppTextField(form.location, { v -> vm.update { it.copy(location = v) } }, stringResource(R.string.location_project))
            if (state.locations.isNotEmpty()) {
                com.hossammo.cash.ui.components.ChipsRow(state.locations.map { it.name }, form.location, { it }, { v -> vm.update { it.copy(location = v) } })
            }
            DropdownField(stringResource(R.string.asset_type), AssetType.entries.toList(), form.assetType, { stringResource(Labels.assetType(it)) }, { t -> vm.update { it.copy(assetType = t) } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(stringResource(R.string.currency), (CommonCurrencies + form.currency).distinct(), form.currency, { it }, { c -> vm.update { it.copy(currency = c) } }, Modifier.weight(1f))
                AppTextField(form.currency, { v -> vm.update { it.copy(currency = v.uppercase().filter { ch -> ch.isLetter() }.take(3)) } }, stringResource(R.string.currency_code_custom), Modifier.weight(1f))
            }
            DropdownField(stringResource(R.string.liquidity_level), LiquidityLevel.entries.toList(), form.level, { stringResource(Labels.level(it)) }, { l -> vm.update { it.copy(level = l) } })

            SectionHeader(stringResource(R.string.balance_for_month, state.month?.let { f.month(it) } ?: stringResource(R.string.current_month)))
            AmountField(form.balance, { v -> vm.update { it.copy(balance = v) } }, stringResource(R.string.balance_optional), suffix = form.currency)
            if (isForeign) {
                FinanceCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.current_rate_value, form.currency, f.rate(state.currentRate)), style = MaterialTheme.typography.bodyMedium)
                    if (state.currentRate == null) Text(stringResource(R.string.no_rate_hint), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(!form.lockRate, { vm.update { it.copy(lockRate = false) } }); Text(stringResource(R.string.use_current_rate))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(form.lockRate, { vm.update { it.copy(lockRate = true) } }); Text(stringResource(R.string.lock_month_rate))
                    }
                    if (form.lockRate) AmountField(form.customRate, { v -> vm.update { it.copy(customRate = v) } }, stringResource(R.string.month_rate_optional), suffix = state.base)
                }
            }

            SectionHeader(stringResource(R.string.appearance))
            ColorPicker(ItemPalette, form.color) { c -> vm.update { it.copy(color = c) } }
            Spacer(Modifier.height(4.dp))
            IconPicker(form.icon, parseHexColor(form.color)) { i -> vm.update { it.copy(icon = i) } }

            SectionHeader(stringResource(R.string.options))
            SwitchRow(stringResource(R.string.include_in_total), form.includeInTotal, { v -> vm.update { it.copy(includeInTotal = v) } }, subtitle = stringResource(R.string.include_in_total_hint))
            SwitchRow(stringResource(R.string.show_in_variable), form.isVariable, { v -> vm.update { it.copy(isVariable = v) } })
            AppTextField(form.notes, { v -> vm.update { it.copy(notes = v) } }, stringResource(R.string.notes), singleLine = false, minLines = 2)
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.save)) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmLeave) {
        ConfirmDialog(stringResource(R.string.unsaved_title), stringResource(R.string.unsaved_message), confirmText = stringResource(R.string.discard), destructive = true, onConfirm = { confirmLeave = false; onBack() }, onDismiss = { confirmLeave = false })
    }
    if (state.confirmDuplicate) {
        ConfirmDialog(stringResource(R.string.duplicate_balance_title), stringResource(R.string.duplicate_balance_message), confirmText = stringResource(R.string.overwrite), onConfirm = { vm.save(confirmOverwrite = true) }, onDismiss = { vm.dismissDuplicate() })
    }
}
