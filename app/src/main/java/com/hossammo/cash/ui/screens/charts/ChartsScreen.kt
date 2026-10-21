package com.hossammo.cash.ui.screens.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.analysis.GroupBy
import com.hossammo.cash.data.analysis.ItemRow
import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.analysis.MonthDetail
import com.hossammo.cash.data.analysis.PeriodFilter
import com.hossammo.cash.data.analysis.Slice
import com.hossammo.cash.data.model.LiquidityData
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.domain.calc.MonthTotal
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.ui.SessionState
import com.hossammo.cash.ui.charts.BarChart
import com.hossammo.cash.ui.charts.HorizontalBars
import com.hossammo.cash.ui.charts.Legend
import com.hossammo.cash.ui.charts.LineChart
import com.hossammo.cash.ui.charts.LineSeries
import com.hossammo.cash.ui.charts.PieChart
import com.hossammo.cash.ui.charts.PieSlice
import com.hossammo.cash.ui.components.ChipsRow
import com.hossammo.cash.ui.components.DropdownField
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.MonthSelector
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.MessagesViewModel
import com.hossammo.cash.ui.screens.dashboard.MonthDetailPanel
import com.hossammo.cash.ui.screens.dashboard.periodLabel
import com.hossammo.cash.ui.theme.LocalFinanceColors
import com.hossammo.cash.ui.theme.parseHexColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import javax.inject.Inject

data class ChartsUiState(
    val loading: Boolean = true,
    val base: String = "EGP",
    val allMonths: List<MonthKey> = emptyList(),
    val months: List<MonthKey> = emptyList(),
    val selected: MonthKey? = null,
    val filter: PeriodFilter = PeriodFilter.ALL,
    val custom: Pair<MonthKey, MonthKey>? = null,
    val series: List<MonthTotal> = emptyList(),
    val itemSeries: List<Pair<ItemRow, List<Float?>>> = emptyList(),
    val byItem: List<Slice> = emptyList(),
    val byLocation: List<Slice> = emptyList(),
    val byCurrency: List<Slice> = emptyList(),
    val byLevel: List<Slice> = emptyList(),
    val rows: List<ItemRow> = emptyList(),
    val topUp: List<ItemRow> = emptyList(),
    val topDown: List<ItemRow> = emptyList(),
    val detail: MonthDetail? = null,
)

@HiltViewModel
class ChartsViewModel @Inject constructor(repo: LiquidityRepository, private val session: SessionState) : MessagesViewModel() {
    private val filter = MutableStateFlow(PeriodFilter.ALL)
    private val custom = MutableStateFlow<Pair<MonthKey, MonthKey>?>(null)
    private val tapped = MutableStateFlow<MonthKey?>(null)
    private val labels = MutableStateFlow<Labels?>(null)

    data class Labels(val noLocation: String, val level: (com.hossammo.cash.domain.model.LiquidityLevel) -> String)

    fun setLabels(l: Labels) { if (labels.value == null) labels.value = l }

    val state: StateFlow<ChartsUiState> = combine(repo.observeData(), session.selectedMonth, filter, custom, combine(tapped, labels) { a, b -> a to b }) { data, _, f, c, tl ->
        build(data, f, c, tl.first, tl.second)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsUiState())

    private fun build(data: LiquidityData, f: PeriodFilter, c: Pair<MonthKey, MonthKey>?, tap: MonthKey?, l: Labels?): ChartsUiState {
        val a = LiquidityAnalyzer(data)
        val selected = session.resolve(a.monthKeys) ?: return ChartsUiState(loading = false, base = data.baseCurrency)
        val series = a.series(f, c)
        val months = series.map { it.month }
        val rows = a.itemRows(selected).filter { it.balance != null }
        val itemSeries = data.items.mapNotNull { item ->
            val s = a.itemSeries(item.id).toMap()
            val values = months.map { s[it]?.toFloat() }
            if (values.all { it == null }) null else (a.itemRows(selected).first { it.item.id == item.id } to values)
        }
        val noLoc = l?.noLocation ?: "-"
        val lvl = l?.level ?: { it.name }
        return ChartsUiState(
            loading = false, base = data.baseCurrency, allMonths = a.monthKeys, months = months, selected = selected, filter = f, custom = c,
            series = series, itemSeries = itemSeries,
            byItem = a.distribution(selected, GroupBy.ITEM),
            byLocation = a.distribution(selected, GroupBy.LOCATION, lvl, noLoc),
            byCurrency = a.distribution(selected, GroupBy.CURRENCY),
            byLevel = a.distribution(selected, GroupBy.LEVEL, lvl, noLoc),
            rows = rows.sortedByDescending { it.valueBase ?: BigDecimal.ZERO },
            topUp = a.topMovers(selected, true), topDown = a.topMovers(selected, false),
            detail = tap?.takeIf { it in a.monthKeys }?.let { a.monthDetail(it) },
        )
    }

    fun selectMonth(k: MonthKey) { session.selectMonth(k) }
    fun setFilter(f: PeriodFilter) { filter.value = f }
    fun setCustom(from: MonthKey, to: MonthKey) { custom.value = if (from <= to) from to to else to to from; filter.value = PeriodFilter.CUSTOM }
    fun tap(k: MonthKey?) { tapped.value = k }
}

private val fallbackPalette = listOf(Color(0xFF246BCE), Color(0xFF2E8B57), Color(0xFFC69B35), Color(0xFF7B4FB5), Color(0xFF8D5A3B), Color(0xFF1F7A6D), Color(0xFFB5473A), Color(0xFF3D5A99))

@Composable
fun ChartsScreen(onBack: () -> Unit, onOpenItem: (String) -> Unit, vm: ChartsViewModel = hiltViewModel()) {
    val f = fmt()
    val noLoc = stringResource(R.string.no_location)
    vm.setLabels(ChartsViewModel.Labels(noLoc) { f.level(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    val fc = LocalFinanceColors.current
    AppScaffold(title = stringResource(R.string.charts_title), onBack = onBack) { padding ->
        if (!state.loading && state.selected == null) {
            EmptyState(stringResource(R.string.dash_empty_title), stringResource(R.string.dash_empty_message), Modifier.padding(padding))
            return@AppScaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { MonthSelector(state.allMonths, state.selected, vm::selectMonth) }
            ChipsRow(PeriodFilter.entries.toList(), state.filter, { periodLabel(it) }, vm::setFilter)
            if (state.filter == PeriodFilter.CUSTOM && state.allMonths.isNotEmpty()) {
                var from by remember { mutableStateOf(state.custom?.first ?: state.allMonths.first()) }
                var to by remember { mutableStateOf(state.custom?.second ?: state.allMonths.last()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    DropdownField(stringResource(R.string.from), state.allMonths, from, { f.month(it) }, { from = it; vm.setCustom(from, to) }, Modifier.weight(1f))
                    DropdownField(stringResource(R.string.to), state.allMonths, to, { f.month(it) }, { to = it; vm.setCustom(from, to) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            val xLabels = state.months.map { f.monthShort(it) }

            ChartCard(stringResource(R.string.chart_total_evolution)) {
                LineChart(
                    listOf(LineSeries(stringResource(R.string.dash_total_liquidity), MaterialTheme.colorScheme.secondary, state.series.map { it.total.toFloat() })),
                    xLabels, selectedIndex = state.months.indexOf(state.detail?.month).takeIf { it >= 0 },
                    onSelect = { vm.tap(state.months.getOrNull(it)) }, description = stringResource(R.string.chart_total_evolution),
                )
                Text(stringResource(R.string.chart_tap_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                state.detail?.let { MonthDetailPanel(it, state.base, onOpenItem) }
            }

            ChartCard(stringResource(R.string.chart_items_evolution)) {
                val ls = state.itemSeries.mapIndexed { i, (row, values) -> LineSeries(row.item.name, parseHexColor(row.item.colorHex, fallbackPalette[i % fallbackPalette.size]), values) }
                LineChart(ls, xLabels, selectedIndex = state.months.indexOf(state.detail?.month).takeIf { it >= 0 }, onSelect = { vm.tap(state.months.getOrNull(it)) }, description = stringResource(R.string.chart_items_evolution))
                Legend(ls.map { it.label to it.color })
            }

            ChartCard(stringResource(R.string.chart_monthly_change)) {
                val changes = state.series.drop(1)
                if (changes.isEmpty()) Text(stringResource(R.string.chart_needs_two_months), color = MaterialTheme.colorScheme.outline)
                else BarChart(
                    changes.map { it.change.change?.toFloat() ?: 0f }, changes.map { f.monthShort(it.month) },
                    positiveColor = fc.increase, negativeColor = fc.decrease,
                    selectedIndex = changes.indexOfFirst { it.month == state.detail?.month }.takeIf { it >= 0 },
                    onSelect = { vm.tap(changes.getOrNull(it)?.month) }, description = stringResource(R.string.chart_monthly_change),
                )
            }

            ChartCard(stringResource(R.string.chart_current_vs_previous)) {
                state.rows.forEach { r ->
                    val cur = r.valueBase?.toFloat() ?: 0f
                    val prev = r.comparison?.change?.previous?.toFloat() ?: 0f
                    val color = parseHexColor(r.item.colorHex)
                    Text(r.item.name, style = MaterialTheme.typography.labelLarge)
                    HorizontalBars(
                        listOf(Triple(stringResource(R.string.previous_month), prev, MaterialTheme.colorScheme.outline), Triple(stringResource(R.string.current_month), cur, color)),
                        valueText = { i -> if (i == 0) f.money(r.comparison?.change?.previous) else f.money(r.valueBase) },
                    )
                }
            }

            PieCard(stringResource(R.string.chart_distribution, state.selected?.let { f.month(it) } ?: ""), state.byItem)

            ChartCard(stringResource(R.string.chart_share)) {
                HorizontalBars(
                    state.byItem.map { Triple(it.label, it.percent?.toFloat() ?: 0f, parseHexColor(it.colorHex)) },
                    valueText = { i -> f.percent(state.byItem[i].percent, signed = false) },
                )
            }

            ChartCard(stringResource(R.string.chart_top_growth)) {
                if (state.topUp.isEmpty()) Text(stringResource(R.string.no_growth_items), color = MaterialTheme.colorScheme.outline)
                HorizontalBars(
                    state.topUp.map { Triple(it.item.name, it.comparison?.change?.change?.toFloat() ?: 0f, fc.increase) },
                    valueText = { i -> f.signedMoney(state.topUp[i].comparison?.change?.change) },
                    onClick = { onOpenItem(state.topUp[it].item.id) },
                )
            }
            ChartCard(stringResource(R.string.chart_top_decline)) {
                if (state.topDown.isEmpty()) Text(stringResource(R.string.no_decline_items), color = MaterialTheme.colorScheme.outline)
                HorizontalBars(
                    state.topDown.map { Triple(it.item.name, it.comparison?.change?.change?.toFloat() ?: 0f, fc.decrease) },
                    valueText = { i -> f.signedMoney(state.topDown[i].comparison?.change?.change) },
                    onClick = { onOpenItem(state.topDown[it].item.id) },
                )
            }
            PieCard(stringResource(R.string.chart_by_location), state.byLocation)
            PieCard(stringResource(R.string.chart_by_currency), state.byCurrency)
            PieCard(stringResource(R.string.chart_by_level), state.byLevel)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    FinanceCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun PieCard(title: String, slices: List<Slice>) {
    val f = fmt()
    var selected by remember(slices) { mutableStateOf<Int?>(null) }
    ChartCard(title) {
        if (slices.isEmpty()) { Text(stringResource(R.string.no_data_for_chart), color = MaterialTheme.colorScheme.outline); return@ChartCard }
        val colored = slices.mapIndexed { i, s -> s to parseHexColor(s.colorHex, fallbackPalette[i % fallbackPalette.size]) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PieChart(colored.map { PieSlice(it.first.label, it.first.value.toFloat(), it.second) }, size = 150, selectedIndex = selected, onSelect = { selected = it }, description = title) {
                selected?.let { i ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(f.percent(slices[i].percent, signed = false), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                colored.forEachIndexed { i, (s, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Legend(listOf(s.label to c), Modifier.weight(1f))
                        Text(f.percent(s.percent, signed = false), style = if (i == selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
                    }
                }
                selected?.let { i -> Text("${slices[i].label}: ${f.money(slices[i].value)}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
