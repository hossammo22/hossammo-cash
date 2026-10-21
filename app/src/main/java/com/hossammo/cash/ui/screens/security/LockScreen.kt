package com.hossammo.cash.ui.screens.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.settings.UserPreferencesRepository
import com.hossammo.cash.data.settings.UserPreferencesRepository.PinResult
import com.hossammo.cash.ui.components.fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** أدوات المصادقة الحيوية. */
object BiometricHelper {
    private const val AUTHENTICATORS = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun findActivity(context: Context): FragmentActivity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }

    fun authenticate(context: Context, title: String, negative: String, onSuccess: () -> Unit, onError: (String?) -> Unit = {}) {
        val activity = findActivity(context) ?: return onError(null)
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) onError(errString.toString())
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negative)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}

data class LockUiState(val pin: String = "", val error: Int? = null, val remaining: Int? = null, val lockedSeconds: Long = 0, val checking: Boolean = false)

@HiltViewModel
class LockViewModel @Inject constructor(private val prefs: UserPreferencesRepository) : ViewModel() {
    val state = MutableStateFlow(LockUiState())

    /** عند اكتمال عدد أرقام الرمز المحفوظ يتم التحقق تلقائيًا دون الضغط على «دخول». */
    fun digit(d: Char, pinLength: Int?, onOk: () -> Unit) {
        val s = state.value
        if (s.checking || s.lockedSeconds > 0 || s.pin.length >= 8) return
        state.value = s.copy(pin = s.pin + d, error = null)
        if (pinLength != null && state.value.pin.length == pinLength) submit(onOk)
    }

    fun backspace() { state.value = state.value.let { it.copy(pin = it.pin.dropLast(1), error = null) } }

    /** كل محاولة تُحتسب؛ بعد 5 محاولات خاطئة يُقفل الإدخال مؤقتًا بمدة متزايدة. */
    fun submit(onOk: () -> Unit) {
        val pin = state.value.pin
        if (pin.length < 4) { state.value = state.value.copy(error = R.string.pin_too_short); return }
        viewModelScope.launch {
            state.value = state.value.copy(checking = true)
            when (val r = withContext(Dispatchers.Default) { prefs.verifyPin(pin) }) {
                PinResult.Ok -> { state.value = LockUiState(); onOk() }
                is PinResult.Wrong -> state.value = LockUiState(error = R.string.pin_wrong, remaining = r.remainingAttempts)
                is PinResult.LockedOut -> startCountdown(r.untilMillis)
            }
        }
    }

    private fun startCountdown(until: Long) = viewModelScope.launch {
        while (true) {
            val left = (until - System.currentTimeMillis()) / 1000
            if (left <= 0) { state.value = LockUiState(); break }
            state.value = LockUiState(error = R.string.pin_locked_out, lockedSeconds = left)
            kotlinx.coroutines.delay(1000)
        }
    }
}

@Composable
fun LockScreen(biometricEnabled: Boolean, onUnlocked: () -> Unit, pinLength: Int? = null, vm: LockViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val f = fmt()
    val bioTitle = stringResource(R.string.biometric_title)
    val usePin = stringResource(R.string.use_pin)
    val bioAvailable = remember { BiometricHelper.isAvailable(context) }
    val canBio = biometricEnabled && bioAvailable
    fun bio() = BiometricHelper.authenticate(context, bioTitle, usePin, onSuccess = onUnlocked)
    LaunchedEffect(Unit) { if (canBio) bio() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.app_title_ar), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.enter_pin), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.semantics { contentDescription = context.getString(R.string.pin_digits_entered, s.pin.length) }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(maxOf(4, s.pin.length)) { i ->
                Box(
                    Modifier.size(14.dp).clip(CircleShape).background(
                        if (i < s.pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val err = s.error
        Text(
            when {
                err == R.string.pin_locked_out -> stringResource(err, f.integer(s.lockedSeconds.toInt()))
                err == R.string.pin_wrong && s.remaining != null -> stringResource(R.string.pin_wrong_remaining, f.integer(s.remaining!!))
                err != null -> stringResource(err)
                else -> " "
            },
            color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        PinPad(
            onDigit = { d -> vm.digit(d, pinLength, onUnlocked) },
            onBackspace = vm::backspace,
            onSubmit = { vm.submit(onUnlocked) },
            enabled = s.lockedSeconds <= 0,
        )
        if (canBio) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = { bio() }) {
                Icon(Icons.Filled.Fingerprint, null); Text("  " + stringResource(R.string.use_biometric))
            }
        }
    }
}

/** لوحة أرقام PIN. الأرقام تُخزن داخليًا كأرقام لاتينية وتُعرض حسب إعداد الأرقام. */
@Composable
fun PinPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onSubmit: () -> Unit, enabled: Boolean = true) {
    val f = fmt()
    val rows = listOf("123", "456", "789")
    // ترتيب لوحة الهاتف القياسي دائمًا (1 و4 و7 يسارًا، 3 و6 و9 يمينًا) حتى مع واجهة RTL
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                r.forEach { c -> PadKey(with(f) { c.toString().localizeDigits() }, enabled) { onDigit(c) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackspace, enabled = enabled, modifier = Modifier.size(64.dp)) {
                Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.delete_digit))
            }
            PadKey(with(f) { "0".localizeDigits() }, enabled) { onDigit('0') }
            FilledTonalButton(onClick = onSubmit, enabled = enabled, modifier = Modifier.size(64.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(stringResource(R.string.enter), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    }
}

@Composable
private fun PadKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(70.dp), shape = CircleShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Text(label, style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)
    }
}
