package com.hossammo.cash.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.db.entity.AuditLogEntity
import com.hossammo.cash.data.repo.AuditLogger
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.SearchField
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AuditLogViewModel @Inject constructor(audit: AuditLogger) : ViewModel() {
    val query = MutableStateFlow("")
    val entries: StateFlow<List<AuditLogEntity>> = audit.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun AuditLogScreen(onBack: () -> Unit, vm: AuditLogViewModel = hiltViewModel()) {
    val list by vm.entries.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val f = fmt()
    AppScaffold(stringResource(R.string.audit_title), onBack = onBack, showHideToggle = false) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(q, { vm.query.value = it }, Modifier.padding(horizontal = 16.dp))
            val labels = list.associate { it.id to "${auditTypeLabel(it.entityType)} • ${auditActionLabel(it.action)}" }
            val filtered = list.filter { q.isBlank() || it.summary.contains(q, true) || (labels[it.id] ?: "").contains(q, true) }
            if (filtered.isEmpty()) EmptyState(stringResource(R.string.audit_empty), stringResource(R.string.audit_empty_message), icon = Icons.Filled.History)
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(filtered, key = { it.id }) { e ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(labels[e.id] ?: "", style = MaterialTheme.typography.titleSmall)
                        if (e.summary.isNotBlank()) Text(e.summary, style = MaterialTheme.typography.bodySmall)
                        Text(f.dateTime(e.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
        }
    }
}

@Composable
fun auditTypeLabel(t: String): String = stringResource(
    when (t) {
        AuditLogger.Type.ITEM -> R.string.audit_type_item
        AuditLogger.Type.CATEGORY -> R.string.audit_type_category
        AuditLogger.Type.MONTH -> R.string.audit_type_month
        AuditLogger.Type.BALANCE -> R.string.audit_type_balance
        AuditLogger.Type.TRANSFER -> R.string.audit_type_transfer
        AuditLogger.Type.RATE -> R.string.audit_type_rate
        AuditLogger.Type.DEBT -> R.string.audit_type_debt
        AuditLogger.Type.PAYMENT -> R.string.audit_type_payment
        AuditLogger.Type.ATTACHMENT -> R.string.audit_type_attachment
        AuditLogger.Type.BACKUP -> R.string.audit_type_backup
        else -> R.string.audit_type_settings
    }
)

@Composable
fun auditActionLabel(a: String): String = stringResource(
    when (a) {
        AuditLogger.Action.CREATE -> R.string.audit_create
        AuditLogger.Action.UPDATE -> R.string.audit_update
        AuditLogger.Action.DELETE -> R.string.audit_delete
        AuditLogger.Action.RESTORE -> R.string.audit_restore
        AuditLogger.Action.PURGE -> R.string.audit_purge
        AuditLogger.Action.ARCHIVE -> R.string.audit_archive
        AuditLogger.Action.UNARCHIVE -> R.string.audit_unarchive
        AuditLogger.Action.LOCK -> R.string.audit_lock
        AuditLogger.Action.UNLOCK -> R.string.audit_unlock
        AuditLogger.Action.COPY -> R.string.audit_copy
        AuditLogger.Action.REFRESH -> R.string.audit_refresh
        AuditLogger.Action.EXPORT -> R.string.audit_export
        else -> R.string.audit_import
    }
)
