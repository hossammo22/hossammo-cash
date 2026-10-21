package com.hossammo.cash.ui.screens.more

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import com.hossammo.cash.R
import com.hossammo.cash.report.ReportType
import com.hossammo.cash.ui.components.SectionHeader
import com.hossammo.cash.ui.navigation.Routes
import com.hossammo.cash.ui.screens.AppScaffold

private data class MoreEntry(val title: Int, val icon: ImageVector, val route: String, val danger: Boolean = false)

private val settingsEntries = listOf(
    MoreEntry(R.string.metals_title, Icons.Filled.WorkspacePremium, Routes.METALS),
    MoreEntry(R.string.settings_general, Icons.Filled.Settings, Routes.SETTINGS_GENERAL),
    MoreEntry(R.string.settings_security, Icons.Filled.Security, Routes.SETTINGS_SECURITY),
    MoreEntry(R.string.settings_currency, Icons.Filled.CurrencyExchange, Routes.SETTINGS_CURRENCY),
    MoreEntry(R.string.settings_notifications, Icons.Filled.Notifications, Routes.SETTINGS_NOTIFICATIONS),
    MoreEntry(R.string.backup_title, Icons.Filled.Backup, Routes.BACKUP),
    MoreEntry(R.string.trash_title, Icons.Filled.DeleteSweep, Routes.TRASH),
    MoreEntry(R.string.audit_title, Icons.Filled.History, Routes.AUDIT),
    MoreEntry(R.string.about_title, Icons.Filled.Info, Routes.ABOUT),
    MoreEntry(R.string.delete_all_title, Icons.Filled.DeleteForever, Routes.DELETE_ALL, danger = true),
)

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    AppScaffold(title = stringResource(R.string.nav_more)) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionHeader(stringResource(R.string.reports_title), Modifier.padding(horizontal = 16.dp)) }
            items(ReportType.entries.filter { !it.isDebt }) { t -> ReportEntry(t, onNavigate) }
            item { SectionHeader(stringResource(R.string.debt_reports_title), Modifier.padding(horizontal = 16.dp)) }
            items(ReportType.entries.filter { it.isDebt }) { t -> ReportEntry(t, onNavigate) }
            item { SectionHeader(stringResource(R.string.settings_title), Modifier.padding(horizontal = 16.dp)) }
            items(settingsEntries) { e ->
                val color = if (e.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                ListItem(
                    headlineContent = { Text(stringResource(e.title), color = color) },
                    leadingContent = { Icon(e.icon, null, tint = if (e.danger) color else MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier.clickable { onNavigate(e.route) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
        }
    }
}

@Composable
private fun ReportEntry(t: ReportType, onNavigate: (String) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(t.titleRes)) },
        leadingContent = {
            Icon(if (t.isDebt) Icons.AutoMirrored.Filled.ReceiptLong else Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.secondary)
        },
        modifier = Modifier.clickable { onNavigate(Routes.report(t.name)) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}
