package com.hossammo.cash.ui.screens.more

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.backup.BackupManager
import com.hossammo.cash.data.repo.SeedRepository
import com.hossammo.cash.data.security.LockManager
import com.hossammo.cash.data.settings.UserPreferencesRepository
import com.hossammo.cash.ui.components.FinanceCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeleteAllViewModel @Inject constructor(
    private val backup: BackupManager,
    private val prefs: UserPreferencesRepository,
    private val seed: SeedRepository,
    private val lock: LockManager,
) : ViewModel() {
    val working = MutableStateFlow(false)

    /** يحذف كل البيانات ويعيد التطبيق لحالة التثبيت الأول (بدون أي قيم مالية). */
    fun deleteAll() = viewModelScope.launch {
        working.value = true
        backup.deleteAllData()
        prefs.clearAll()
        seed.seedIfNeeded()
        lock.evaluateOnStart()
        working.value = false
    }
}

@Composable
fun DeleteAllScreen(onBack: () -> Unit, vm: DeleteAllViewModel = hiltViewModel()) {
    val working by vm.working.collectAsStateWithLifecycle()
    val phrase = stringResource(R.string.delete_all_phrase)
    var typed by remember { mutableStateOf("") }
    SettingsPage(stringResource(R.string.delete_all_title), onBack) {
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.delete_all_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Text(stringResource(R.string.delete_all_backup_first), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.type_to_confirm, phrase), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = vm::deleteAll,
            enabled = typed.trim() == phrase && !working,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (working) CircularProgressIndicator() else Text(stringResource(R.string.delete_all_button))
        }
    }
}
