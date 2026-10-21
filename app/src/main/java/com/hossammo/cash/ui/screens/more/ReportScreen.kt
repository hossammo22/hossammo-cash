package com.hossammo.cash.ui.screens.more

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.repo.DebtRepository
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.data.settings.UserPreferencesRepository
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.report.ExportFormat
import com.hossammo.cash.report.ReportDocument
import com.hossammo.cash.report.ReportExporter
import com.hossammo.cash.report.ReportGenerator
import com.hossammo.cash.report.ReportParams
import com.hossammo.cash.report.ReportType
import com.hossammo.cash.ui.components.DropdownField
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.LabeledValue
import com.hossammo.cash.ui.components.SectionHeader
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages
import com.hossammo.cash.ui.screens.MessagesViewModel
import com.hossammo.cash.util.AppFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ReportUiState(
    val loading: Boolean = true,
    val type: ReportType = ReportType.TOTAL_PERIOD,
    val months: List<MonthKey> = emptyList(),
    val params: ReportParams = ReportParams(ReportType.TOTAL_PERIOD),
    val doc: ReportDocument? = null,
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    liquidity: LiquidityRepository,
    debtRepo: DebtRepository,
    prefs: UserPreferencesRepository,
    private val exporter: ReportExporter,
) : MessagesViewModel() {
    val type: ReportType = runCatching { ReportType.valueOf(savedState.get<String>("type") ?: "") }.getOrDefault(ReportType.TOTAL_PERIOD)
    private val params = MutableStateFlow<ReportParams?>(null)
    val exporting = MutableStateFlow(false)
    /** آخر ملف مُصدَّر بانتظار المشاركة أو الحفظ. */
    val exported = MutableStateFlow<Pair<File, ExportFormat>?>(null)

    val state: StateFlow<ReportUiState> = combine(liquidity.observeData(), debtRepo.observeDebts(), prefs.prefs, params) { data, debts, p, prm ->
        val months = LiquidityAnalyzer(data).monthKeys
        val effective = prm ?: ReportParams(
            type, from = months.firstOrNull(), to = months.lastOrNull(),
            monthA = months.getOrNull(months.size - 2), monthB = months.lastOrNull(),
        )
        // التقارير تعرض القيم دائمًا (حتى مع إخفاء القيم في الواجهة) لأنها طلب صريح من المستخدم
        val fmt = AppFormatter(context, p.numberStyle, hideValues = false)
        val doc = withContext(Dispatchers.Default) { ReportGenerator(context, fmt, data, debts).generate(effective) }
        ReportUiState(false, type, months, effective, doc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState(type = type))

    fun update(block: (ReportParams) -> ReportParams) { params.value = block(params.value ?: state.value.params) }

    fun export(format: ExportFormat) = viewModelScope.launch {
        val doc = state.value.doc ?: return@launch
        exported.value = null
        exporting.value = true
        runCatching { exporter.export(doc, format, type.name.lowercase()) }
            .onSuccess { exported.value = it to format; message(R.string.msg_report_ready) }
            .onFailure { message(R.string.msg_export_failed, true) }
        exporting.value = false
    }

    fun shareUri(file: File): Uri = exporter.shareUri(file)

    fun saveTo(target: Uri) = viewModelScope.launch {
        val (file, _) = exported.value ?: return@launch
        val ok = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(target, "wt")?.use { o -> file.inputStream().use { it.copyTo(o) } } != null }.getOrDefault(false)
        }
        message(if (ok) R.string.msg_saved else R.string.msg_export_failed, !ok)
    }
}

@Composable
fun ReportScreen(onBack: () -> Unit, vm: ReportViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val exported by vm.exported.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val f = fmt()
    val context = LocalContext.current
    var pendingSave by remember { mutableStateOf<ExportFormat?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri -> uri?.let(vm::saveTo); pendingSave = null }
    var pendingShare by remember { mutableStateOf(false) }

    LaunchedEffect(exported, pendingShare, pendingSave) {
        val e = exported ?: return@LaunchedEffect
        if (pendingShare) { pendingShare = false; share(context, vm.shareUri(e.first), e.second) }
        pendingSave?.let { fmtx -> if (fmtx == e.second) saver.launch(e.first.name) }
    }

    AppScaffold(title = stringResource(vm.type.titleRes), onBack = onBack, snackbar = snackbar, showHideToggle = false) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.type.needsPeriod && s.months.isNotEmpty()) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField(stringResource(R.string.from_month), s.months, s.params.from, { f.month(it) }, { m -> vm.update { it.copy(from = m) } }, Modifier.weight(1f))
                    DropdownField(stringResource(R.string.to_month), s.months, s.params.to, { f.month(it) }, { m -> vm.update { it.copy(to = m) } }, Modifier.weight(1f))
                }
            }
            if (s.type.needsTwoMonths && s.months.isNotEmpty()) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField(stringResource(R.string.month_a), s.months, s.params.monthA, { f.month(it) }, { m -> vm.update { it.copy(monthA = m) } }, Modifier.weight(1f))
                    DropdownField(stringResource(R.string.month_b), s.months, s.params.monthB, { f.month(it) }, { m -> vm.update { it.copy(monthB = m) } }, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ExportFormat.entries.forEach { ef ->
                        AssistChip(
                            onClick = { pendingShare = true; vm.export(ef) },
                            enabled = !exporting && s.doc?.isEmpty == false,
                            label = { Text(stringResource(R.string.share_as, ef.name)) },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                        )
                        AssistChip(
                            onClick = { pendingSave = ef; vm.export(ef) },
                            enabled = !exporting && s.doc?.isEmpty == false,
                            label = { Text(stringResource(R.string.save_as, ef.name)) },
                            leadingIcon = { Icon(Icons.Filled.Save, null) },
                        )
                    }
                    if (exporting) CircularProgressIndicator(Modifier.width(24.dp))
                }
            }
            val doc = s.doc
            when {
                s.loading -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                doc == null || doc.isEmpty -> item { EmptyState(stringResource(R.string.report_no_data), stringResource(R.string.report_no_data_message)) }
                else -> {
                    item {
                        FinanceCard(Modifier.fillMaxWidth()) {
                            Text(doc.title, style = MaterialTheme.typography.titleMedium)
                            Text(doc.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            doc.summary.forEach { (k, v) -> LabeledValue(k, v) }
                        }
                    }
                    doc.tables.forEach { t ->
                        item { SectionHeader(t.title) }
                        item { ReportTableView(t.headers, t.rows.map { r -> r.map { it.text } }) }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportTableView(headers: List<String>, rows: List<List<String>>) {
    if (rows.isEmpty()) {
        Text(stringResource(R.string.report_no_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))) {
            headers.forEach { h -> Text(h, Modifier.width(120.dp).padding(6.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 2) }
        }
        rows.forEachIndexed { i, r ->
            Row(Modifier.background(if (i % 2 == 1) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.background)) {
                r.forEach { c -> Text(c, Modifier.width(120.dp).padding(6.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

private fun share(context: Context, uri: Uri, format: ExportFormat) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = format.mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}
