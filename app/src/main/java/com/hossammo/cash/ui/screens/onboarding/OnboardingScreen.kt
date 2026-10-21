package com.hossammo.cash.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.security.PinHasher
import com.hossammo.cash.data.settings.UserPreferencesRepository
import com.hossammo.cash.ui.components.AppTextField
import com.hossammo.cash.ui.screens.security.PinPad
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val prefs: UserPreferencesRepository) : ViewModel() {
    fun saveName(name: String) = viewModelScope.launch { if (name.isNotBlank()) prefs.setDisplayName(name) }
    suspend fun savePin(pin: String) = prefs.setPin(pin)
}

private data class Page(val icon: ImageVector, val title: Int, val body: Int)

private val pages = listOf(
    Page(Icons.Filled.Savings, R.string.onb_welcome_title, R.string.onb_welcome_body),
    Page(Icons.Filled.Security, R.string.onb_local_title, R.string.onb_local_body),
    Page(Icons.Filled.CalendarMonth, R.string.onb_first_month_title, R.string.onb_first_month_body),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    val pinStep = pages.size
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages.size + 1) { i ->
                    Box(Modifier.size(if (i == step) 10.dp else 8.dp).clip(CircleShape).background(
                        if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
                }
            }
            TextButton(onClick = { vm.saveName(name); onFinish() }) { Text(stringResource(R.string.skip)) }
        }
        if (step < pinStep) {
            val p = pages[step]
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(110.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(p.icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(56.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text(stringResource(p.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(p.body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (step == 0) {
                    Spacer(Modifier.height(20.dp))
                    AppTextField(name, { name = it }, stringResource(R.string.your_name_optional))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.previous)) }
                Button(onClick = { step++ }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.next)) }
            }
        } else {
            PinSetupStep(
                modifier = Modifier.weight(1f),
                onSave = { pin -> vm.savePin(pin) },
                onDone = { vm.saveName(name); onFinish() },
                onBack = { step-- },
            )
        }
    }
}

/** خطوة اختيارية لإنشاء PIN: إدخال ثم تأكيد. */
@Composable
fun PinSetupStep(modifier: Modifier, onSave: suspend (String) -> Unit, onDone: () -> Unit, onBack: (() -> Unit)? = null) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onb_pin_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(if (first == null) R.string.onb_pin_body else R.string.confirm_pin),
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).background(
                    if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
            }
        }
        Text(error?.let { stringResource(it) } ?: " ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        PinPad(
            onDigit = { if (pin.length < 8) { pin += it; error = null } },
            onBackspace = { pin = pin.dropLast(1) },
            onSubmit = {
                when {
                    !PinHasher.isValidPin(pin) -> error = R.string.pin_invalid
                    first == null -> { first = pin; pin = "" }
                    first != pin -> { error = R.string.pin_mismatch; first = null; pin = "" }
                    else -> scope.launch { onSave(pin); onDone() }
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) OutlinedButton(onClick = onBack) { Text(stringResource(R.string.previous)) }
            TextButton(onClick = onDone) { Text(stringResource(R.string.skip_pin)) }
        }
    }
}
