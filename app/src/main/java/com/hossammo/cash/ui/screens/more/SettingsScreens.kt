package com.hossammo.cash.ui.screens.more

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.db.entity.CurrencyRateEntity
import com.hossammo.cash.data.repo.AppSettingsStore
import com.hossammo.cash.data.repo.CurrencyRepository
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.data.repo.RefreshResult
import com.hossammo.cash.data.security.PinHasher
import com.hossammo.cash.data.settings.NotificationPrefs
import com.hossammo.cash.data.settings.NumberStyle
import com.hossammo.cash.data.settings.RateProviderId
import com.hossammo.cash.data.settings.ThemeMode
import com.hossammo.cash.data.settings.UserPreferencesRepository
import com.hossammo.cash.data.settings.UserPrefs
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.notifications.DailyCheckWorker
import com.hossammo.cash.notifications.Notifier
import com.hossammo.cash.ui.components.AmountField
import com.hossammo.cash.ui.components.AppTextField
import com.hossammo.cash.ui.components.ConfirmDialog
import com.hossammo.cash.ui.components.DropdownField
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.LabeledValue
import com.hossammo.cash.ui.components.SectionHeader
import com.hossammo.cash.ui.components.SwitchRow
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages
import com.hossammo.cash.ui.screens.MessagesViewModel
import com.hossammo.cash.ui.screens.liquidity.CommonCurrencies
import com.hossammo.cash.ui.screens.onboarding.PinSetupStep
import com.hossammo.cash.ui.screens.security.BiometricHelper
import com.hossammo.cash.ui.screens.security.PinPad
import com.hossammo.cash.ui.screens.variable.ManualRateDialog
import com.hossammo.cash.ui.screens.variable.sourceName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/** هيكل موحد لصفحات الإعدادات. */
@Composable
fun SettingsPage(title: String, onBack: () -> Unit, snackbar: SnackbarHostState = remember { SnackbarHostState() }, content: @Composable ColumnScope.() -> Unit) {
    AppScaffold(title = title, onBack = onBack, snackbar = snackbar, showHideToggle = false) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

// ======================= الإعدادات العامة =======================

data class GeneralState(val prefs: UserPrefs = UserPrefs(), val base: String = "EGP", val hasBalances: Boolean = false)

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val appSettings: AppSettingsStore,
    private val currency: CurrencyRepository,
    liquidity: LiquidityRepository,
) : MessagesViewModel() {
    val state: StateFlow<GeneralState> = combine(prefs.prefs, appSettings.baseCurrency, liquidity.observeData().map { it.balances.isNotEmpty() }) { p, b, has ->
        GeneralState(p, b, has)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GeneralState())

    fun setName(n: String) = viewModelScope.launch { prefs.setDisplayName(n); message(R.string.msg_saved) }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { prefs.setTheme(t) }
    fun setNumbers(n: NumberStyle) = viewModelScope.launch { prefs.setNumberStyle(n) }
    fun setHide(v: Boolean) = viewModelScope.launch { prefs.setHideValues(v) }
    fun setBase(code: String) = viewModelScope.launch {
        if (state.value.hasBalances) { message(R.string.base_currency_locked, true); return@launch }
        currency.changeBaseCurrency(code)
        message(R.string.msg_saved)
    }
}

@Composable
fun GeneralSettingsScreen(onBack: () -> Unit, vm: GeneralSettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    var name by remember(s.prefs.displayName) { mutableStateOf(s.prefs.displayName) }
    SettingsPage(stringResource(R.string.settings_general), onBack, snackbar) {
        AppTextField(name, { name = it }, stringResource(R.string.your_name_optional))
        OutlinedButton(onClick = { vm.setName(name) }, enabled = name != s.prefs.displayName) { Text(stringResource(R.string.save)) }
        SectionHeader(stringResource(R.string.base_currency))
        DropdownField(stringResource(R.string.base_currency), CommonCurrencies, s.base, { it }, vm::setBase)
        Text(
            stringResource(if (s.hasBalances) R.string.base_currency_locked else R.string.base_currency_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionHeader(stringResource(R.string.appearance))
        DropdownField(stringResource(R.string.theme), ThemeMode.entries, s.prefs.themeMode, { themeLabel(it) }, vm::setTheme)
        DropdownField(stringResource(R.string.number_style), NumberStyle.entries, s.prefs.numberStyle, {
            stringResource(if (it == NumberStyle.ARABIC) R.string.numbers_arabic else R.string.numbers_latin)
        }, vm::setNumbers)
        SwitchRow(stringResource(R.string.hide_values_default), s.prefs.hideValues, vm::setHide, subtitle = stringResource(R.string.hide_values_hint))
    }
}

@Composable
fun themeLabel(t: ThemeMode) = stringResource(
    when (t) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

// ======================= الأمان =======================

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(private val prefs: UserPreferencesRepository) : MessagesViewModel() {
    val state: StateFlow<UserPrefs> = prefs.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPrefs())

    suspend fun setPin(pin: String) { prefs.setPin(pin); message(R.string.msg_pin_saved) }
    suspend fun verify(pin: String): UserPreferencesRepository.PinResult = prefs.verifyPin(pin)
    fun removePin() = viewModelScope.launch { prefs.clearPin(); message(R.string.msg_pin_removed) }
    fun setBiometric(v: Boolean) = viewModelScope.launch { prefs.setBiometric(v) }
    fun setAutoLock(m: Int) = viewModelScope.launch { prefs.setAutoLock(m) }
    fun setSecure(v: Boolean) = viewModelScope.launch { prefs.setSecureScreen(v) }
    fun wrongPin() = message(R.string.pin_wrong, true)
}

private val autoLockOptions = listOf(0, 1, 5, 15, 30, -1)

@Composable
fun SecuritySettingsScreen(onBack: () -> Unit, vm: SecuritySettingsViewModel = hiltViewModel()) {
    val p by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val context = LocalContext.current
    val f = fmt()
    val bioAvailable = remember { BiometricHelper.isAvailable(context) }
    var setPin by remember { mutableStateOf(false) }
    // للتغيير أو الإزالة يجب إدخال الـ PIN الحالي أولًا
    var verifyFor by remember { mutableStateOf<String?>(null) }
    val bioTitle = stringResource(R.string.biometric_title)
    val cancel = stringResource(R.string.cancel)

    SettingsPage(stringResource(R.string.settings_security), onBack, snackbar) {
        SectionHeader(stringResource(R.string.pin_code))
        if (p.pinEnabled) {
            Text(stringResource(R.string.pin_enabled), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { verifyFor = "change" }) { Text(stringResource(R.string.change_pin)) }
                OutlinedButton(onClick = { verifyFor = "remove" }) { Text(stringResource(R.string.remove_pin), color = MaterialTheme.colorScheme.error) }
            }
        } else {
            Text(stringResource(R.string.pin_disabled), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { setPin = true }) { Text(stringResource(R.string.set_pin)) }
        }
        SwitchRow(
            stringResource(R.string.biometric_unlock), p.biometricEnabled && bioAvailable,
            { on ->
                if (on) BiometricHelper.authenticate(context, bioTitle, cancel, onSuccess = { vm.setBiometric(true) }) else vm.setBiometric(false)
            },
            subtitle = stringResource(
                when {
                    !bioAvailable -> R.string.biometric_unavailable
                    !p.pinEnabled -> R.string.biometric_needs_pin
                    else -> R.string.biometric_hint
                }
            ),
            enabled = bioAvailable && p.pinEnabled,
        )
        SectionHeader(stringResource(R.string.auto_lock))
        DropdownField(stringResource(R.string.auto_lock), autoLockOptions, p.autoLockMinutes, { autoLockLabel(it, f) }, vm::setAutoLock)
        Text(stringResource(R.string.auto_lock_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionHeader(stringResource(R.string.privacy))
        SwitchRow(stringResource(R.string.secure_screen), p.secureScreen, vm::setSecure, subtitle = stringResource(R.string.secure_screen_hint))
    }

    if (setPin) {
        AlertDialog(
            onDismissRequest = { setPin = false },
            confirmButton = {},
            text = {
                PinSetupStep(Modifier, onSave = { vm.setPin(it) }, onDone = { setPin = false })
            },
        )
    }
    verifyFor?.let { purpose ->
        VerifyPinDialog(
            onDismiss = { verifyFor = null },
            verify = { vm.verify(it) },
            onVerified = {
                verifyFor = null
                if (purpose == "remove") vm.removePin() else setPin = true
            },
            onWrong = vm::wrongPin,
        )
    }
}

@Composable
private fun autoLockLabel(m: Int, f: com.hossammo.cash.util.AppFormatter): String = when (m) {
    -1 -> stringResource(R.string.auto_lock_never)
    0 -> stringResource(R.string.auto_lock_immediate)
    else -> stringResource(R.string.auto_lock_minutes, f.integer(m))
}

@Composable
fun VerifyPinDialog(onDismiss: () -> Unit, verify: suspend (String) -> UserPreferencesRepository.PinResult, onVerified: () -> Unit, onWrong: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf(0) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        if (verify(pin) == UserPreferencesRepository.PinResult.Ok) onVerified() else { pin = ""; onWrong() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_current_pin)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("•".repeat(pin.length).ifEmpty { " " }, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                PinPad(onDigit = { if (pin.length < 8) pin += it }, onBackspace = { pin = pin.dropLast(1) }, onSubmit = { if (PinHasher.isValidPin(pin)) trigger++ })
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ======================= العملات =======================

data class CurrencyState(val prefs: UserPrefs = UserPrefs(), val rates: List<CurrencyRateEntity> = emptyList(), val base: String = "EGP")

@HiltViewModel
class CurrencySettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val currency: CurrencyRepository,
    appSettings: AppSettingsStore,
) : MessagesViewModel() {
    val refreshing = MutableStateFlow(false)
    val state: StateFlow<CurrencyState> = combine(prefs.prefs, currency.observeRates(), appSettings.baseCurrency) { p, r, b ->
        CurrencyState(p, r.sortedBy { it.currencyCode }, b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrencyState())

    fun setProvider(p: RateProviderId) = viewModelScope.launch { prefs.setRateProvider(p) }
    fun setInterval(h: Int) = viewModelScope.launch { prefs.setAutoRefreshHours(h) }
    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        when (val r = currency.refresh()) {
            is RefreshResult.Success -> message(R.string.msg_rates_updated, false, null, r.updated)
            is RefreshResult.Failed -> message(R.string.msg_rates_failed, true)
        }
        refreshing.value = false
    }
    fun setManual(code: String, rate: BigDecimal) = viewModelScope.launch {
        if (currency.setManualRate(code, rate)) message(R.string.msg_manual_rate_saved) else message(R.string.msg_invalid_rate, true)
    }
}

private val refreshOptions = listOf(0, 6, 12, 24, 72, 168)

@Composable
fun CurrencySettingsScreen(onBack: () -> Unit, vm: CurrencySettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val f = fmt()
    var manualFor by remember { mutableStateOf<String?>(null) }
    var addNew by remember { mutableStateOf(false) }
    SettingsPage(stringResource(R.string.settings_currency), onBack, snackbar) {
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.currency_privacy_note), style = MaterialTheme.typography.bodySmall)
        }
        DropdownField(stringResource(R.string.rate_provider), RateProviderId.entries, s.prefs.rateProvider, { sourceName(it.id) }, vm::setProvider)
        DropdownField(stringResource(R.string.auto_refresh), refreshOptions, s.prefs.autoRefreshHours, {
            if (it == 0) stringResource(R.string.auto_refresh_off) else stringResource(R.string.every_hours, f.integer(it))
        }, vm::setInterval)
        LabeledValue(stringResource(R.string.last_refresh), if (s.prefs.lastRateRefreshAt > 0) f.dateTime(s.prefs.lastRateRefreshAt) else stringResource(R.string.never))
        if (s.prefs.lastRateError != null) Text(stringResource(R.string.last_refresh_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::refresh, enabled = !refreshing) { Icon(Icons.Filled.Refresh, null); Text(" " + stringResource(if (refreshing) R.string.refreshing else R.string.refresh_now)) }
            OutlinedButton(onClick = { addNew = true }) { Icon(Icons.Filled.Add, null); Text(" " + stringResource(R.string.add_currency_rate)) }
        }
        SectionHeader(stringResource(R.string.saved_rates, s.base))
        if (s.rates.isEmpty()) Text(stringResource(R.string.no_rates_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.rates.forEach { r ->
            FinanceCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("1 ${r.currencyCode} = ${f.rate(r.rateToBase)} ${r.baseCurrency}", style = MaterialTheme.typography.titleSmall)
                        Text("${sourceName(r.source)} • ${f.dateTime(r.fetchedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { manualFor = r.currencyCode }) { Icon(Icons.Filled.Edit, stringResource(R.string.manual_rate)) }
                }
            }
        }
    }
    manualFor?.let { code -> ManualRateDialog(code, s.base, onDismiss = { manualFor = null }) { vm.setManual(code, it); manualFor = null } }
    if (addNew) {
        var code by remember { mutableStateOf("USD") }
        var rate by remember { mutableStateOf("") }
        val parsed = Money.parseOrNull(rate)
        AlertDialog(
            onDismissRequest = { addNew = false },
            title = { Text(stringResource(R.string.add_currency_rate)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField(stringResource(R.string.currency), CommonCurrencies.filter { it != s.base }, code, { it }, { code = it })
                    AmountField(rate, { rate = it }, stringResource(R.string.rate), suffix = s.base)
                }
            },
            confirmButton = {
                Button(onClick = { parsed?.let { vm.setManual(code, it) }; addNew = false }, enabled = parsed != null && parsed.signum() > 0) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { addNew = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// ======================= الإشعارات =======================

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val prefs: UserPreferencesRepository,
) : MessagesViewModel() {
    val state: StateFlow<NotificationPrefs> = prefs.prefs.map { it.notifications }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationPrefs())

    fun update(n: NotificationPrefs) = viewModelScope.launch {
        val hourChanged = n.hourOfDay != state.value.hourOfDay
        prefs.setNotifications(n)
        if (hourChanged) DailyCheckWorker.schedule(context, n.hourOfDay, replace = true)
    }

    fun canNotify() = Notifier.canNotify(context)
}

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, vm: NotificationSettingsViewModel = hiltViewModel()) {
    val n by vm.state.collectAsStateWithLifecycle()
    val f = fmt()
    var allowed by remember { mutableStateOf(vm.canNotify()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    SettingsPage(stringResource(R.string.settings_notifications), onBack) {
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.notifications_local_note), style = MaterialTheme.typography.bodySmall)
            if (!allowed && Build.VERSION.SDK_INT >= 33) {
                TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.allow_notifications)) }
            }
        }
        SwitchRow(stringResource(R.string.notif_monthly), n.monthlyReminder, { vm.update(n.copy(monthlyReminder = it)) })
        if (n.monthlyReminder) DropdownField(stringResource(R.string.notif_monthly_day), (1..28).toList(), n.monthlyReminderDay, { f.integer(it) }, { vm.update(n.copy(monthlyReminderDay = it)) })
        SwitchRow(stringResource(R.string.notif_debt_due), n.debtDue, { vm.update(n.copy(debtDue = it)) })
        if (n.debtDue) DropdownField(stringResource(R.string.notif_days_before), listOf(0, 1, 2, 3, 5, 7, 14, 30), n.debtDueDaysBefore, { stringResource(R.string.days_count, f.integer(it)) }, { vm.update(n.copy(debtDueDaysBefore = it)) })
        SwitchRow(stringResource(R.string.notif_debt_overdue), n.debtOverdue, { vm.update(n.copy(debtOverdue = it)) })
        SwitchRow(stringResource(R.string.notif_rates), n.ratesUpdate, { vm.update(n.copy(ratesUpdate = it)) })
        SwitchRow(stringResource(R.string.notif_backup), n.backupReminder, { vm.update(n.copy(backupReminder = it)) })
        if (n.backupReminder) DropdownField(stringResource(R.string.notif_backup_interval), listOf(7, 14, 30, 60, 90), n.backupIntervalDays, { stringResource(R.string.days_count, f.integer(it)) }, { vm.update(n.copy(backupIntervalDays = it)) })
        DropdownField(stringResource(R.string.notif_hour), (0..23).toList(), n.hourOfDay, { "${f.integer(it)}:00" }, { vm.update(n.copy(hourOfDay = it)) })
    }
}
