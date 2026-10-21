package com.hossammo.cash.ui.screens.dashboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hossammo.cash.R
import com.hossammo.cash.data.analysis.ItemRow
import com.hossammo.cash.data.analysis.MonthDetail
import com.hossammo.cash.data.analysis.PeriodFilter
import com.hossammo.cash.ui.charts.BarChart
import com.hossammo.cash.ui.charts.LineChart
import com.hossammo.cash.ui.charts.LineSeries
import com.hossammo.cash.ui.charts.PieChart
import com.hossammo.cash.ui.charts.PieSlice
import com.hossammo.cash.ui.charts.Legend
import com.hossammo.cash.ui.components.ChipsRow
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.HeroCard
import com.hossammo.cash.ui.components.ItemAvatar
import com.hossammo.cash.ui.components.LabeledValue
import com.hossammo.cash.ui.components.MonthSelector
import com.hossammo.cash.ui.components.SectionHeader
import com.hossammo.cash.ui.components.StatCard
import com.hossammo.cash.ui.components.TrendBadge
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.theme.LocalFinanceColors
import com.hossammo.cash.ui.theme.parseHexColor

@Composable
fun periodLabel(p: PeriodFilter): String = stringResource(
    when (p) {
        PeriodFilter.M3 -> R.string.period_3m
        PeriodFilter.M6 -> R.string.period_6m
        PeriodFilter.Y1 -> R.string.period_1y
        PeriodFilter.Y2 -> R.string.period_2y
        PeriodFilter.ALL -> R.string.period_all
        PeriodFilter.CUSTOM -> R.string.period_custom
    }
)

@Composable
fun DashboardScreen(
    onOpenCharts: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenMonths: () -> Unit,
    onOpenMonthEntry: (Int) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppScaffold(title = stringResource(R.string.app_title_ar)) { padding ->
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            state.selected == null -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                EmptyState(
                    title = stringResource(R.string.dash_empty_title),
                    message = stringResource(R.string.dash_empty_message),
                    icon = Icons.Filled.CalendarMonth,
                    action = { Button(onClick = onOpenMonths) { Icon(Icons.Filled.AddCircle, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_first_month)) } },
                )
            }
            else -> DashboardContent(state, Modifier.padding(padding), viewModel, onOpenCharts, onOpenItem, onOpenMonthEntry)
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    modifier: Modifier,
    vm: DashboardViewModel,
    onOpenCharts: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenMonthEntry: (Int) -> Unit,
) {
    val f = fmt()
    val s = state.summary ?: return
    val fc = LocalFinanceColors.current
    val cols = if (LocalConfiguration.current.screenWidthDp >= 600) 4 else 2
    val base = state.baseCurrency
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (state.displayName.isNotBlank()) {
            Text(stringResource(R.string.greeting, state.displayName), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            MonthSelector(state.months, state.selected, vm::selectMonth)
        }
        Spacer(Modifier.height(8.dp))
        // البطاقة الرئيسية: أين أموالي الآن؟
        HeroCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dash_total_liquidity), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
            Text(
                f.money(s.total, base), style = MaterialTheme.typography.headlineLarge.copy(textDirection = TextDirection.Content),
                fontWeight = FontWeight.ExtraBold, color = fc.gold,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrendBadge(s.changeVsPrevious.status, s.changeVsPrevious.change, s.changeVsPrevious.percent)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vs_previous_month), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
            }
            if (s.changeVsPrevious.percent == null) {
                Text(stringResource(R.string.comparison_unavailable_long), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
            if (s.netTransfersMoved.signum() > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SwapHoriz, null, tint = fc.editable)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.dash_transfers_note, f.money(s.netTransfersMoved, base)), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { onOpenMonthEntry(state.selected!!.value) }) { Text(stringResource(R.string.edit_month_balances), color = fc.gold) }
        }

        SectionHeader(stringResource(R.string.dash_key_figures))
        val cards: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> StatCard(stringResource(R.string.dash_last_total), f.money(s.lastRecorded?.total, base), m, subtitle = s.lastRecorded?.let { f.month(it.month) }, icon = Icons.Filled.CalendarMonth) },
            { m -> StatCard(stringResource(R.string.dash_change_since_start), f.signedMoney(s.stats.changeSinceFirst?.change), m, subtitle = f.percent(s.stats.changeSinceFirst?.percent), icon = Icons.Filled.History, accent = if ((s.stats.changeSinceFirst?.change?.signum() ?: 0) >= 0) fc.increase else fc.decrease) },
            { m -> StatCard(stringResource(R.string.dash_highest), f.money(s.stats.highest?.total, base), m, subtitle = s.stats.highest?.let { f.month(it.month) }, icon = Icons.Filled.ArrowUpward, accent = fc.increase) },
            { m -> StatCard(stringResource(R.string.dash_lowest), f.money(s.stats.lowest?.total, base), m, subtitle = s.stats.lowest?.let { f.month(it.month) }, icon = Icons.Filled.ArrowDownward, accent = fc.decrease) },
            { m -> StatCard(stringResource(R.string.dash_average), f.money(s.stats.average, base), m, subtitle = periodLabel(state.filter), icon = Icons.Filled.Functions) },
            { m -> StatCard(stringResource(R.string.dash_best_month), s.stats.bestMonth?.let { f.month(it.month) } ?: stringResource(R.string.not_available), m, subtitle = s.stats.bestMonth?.let { f.signedMoney(it.change.change) }, icon = Icons.Filled.EmojiEvents, accent = fc.increase) },
            { m -> StatCard(stringResource(R.string.dash_worst_month), s.stats.worstMonth?.let { f.month(it.month) } ?: stringResource(R.string.not_available), m, subtitle = s.stats.worstMonth?.let { f.signedMoney(it.change.change) }, icon = Icons.AutoMirrored.Filled.TrendingDown, accent = fc.decrease) },
            { m -> StatCard(stringResource(R.string.dash_biggest_increase), s.biggestIncrease?.item?.name ?: stringResource(R.string.not_available), m, subtitle = s.biggestIncrease?.comparison?.change?.change?.let { f.signedMoney(it) }, icon = Icons.AutoMirrored.Filled.TrendingUp, accent = fc.increase, onClick = s.biggestIncrease?.let { { onOpenItem(it.item.id) } }) },
            { m -> StatCard(stringResource(R.string.dash_biggest_decrease), s.biggestDecrease?.item?.name ?: stringResource(R.string.not_available), m, subtitle = s.biggestDecrease?.comparison?.change?.change?.let { f.signedMoney(it) }, icon = Icons.AutoMirrored.Filled.TrendingDown, accent = fc.decrease, onClick = s.biggestDecrease?.let { { onOpenItem(it.item.id) } }) },
            { m -> StatCard(stringResource(R.string.dash_largest_share), s.largestShare?.item?.name ?: stringResource(R.string.not_available), m, subtitle = s.largestShare?.comparison?.shareOfTotal?.let { f.percent(it, signed = false) }, icon = Icons.Filled.PieChart, onClick = s.largestShare?.let { { onOpenItem(it.item.id) } }) },
            { m -> StatCard(stringResource(R.string.dash_immediate), f.money(s.immediate, base), m, icon = Icons.Filled.Bolt, accent = fc.editable) },
            { m -> StatCard(stringResource(R.string.dash_needs_liquidation), f.money(s.needsLiquidation, base), m, icon = Icons.Filled.HourglassBottom) },
            { m -> StatCard(stringResource(R.string.dash_last_update), f.dateTime(s.lastUpdatedAt), m, icon = Icons.Filled.AccessTime) },
            { m -> StatCard(stringResource(R.string.dash_rates_update), f.dateTime(state.lastRatesUpdate), m, icon = Icons.Filled.CurrencyExchange) },
        )
        cards.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { card -> card(Modifier.weight(1f)) }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        SectionHeader(stringResource(R.string.charts_title)) {
            TextButton(onClick = onOpenCharts) { Icon(Icons.Filled.BarChart, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.all_charts)) }
        }
        ChipsRow(PeriodFilter.entries.filter { it != PeriodFilter.CUSTOM }, state.filter, { periodLabel(it) }, vm::setFilter)
        Spacer(Modifier.height(8.dp))
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.chart_total_evolution), style = MaterialTheme.typography.titleSmall)
            val sel = state.selectedDetail?.month
            LineChart(
                series = listOf(LineSeries(stringResource(R.string.dash_total_liquidity), MaterialTheme.colorScheme.secondary, state.series.map { it.total.toFloat() })),
                xLabels = state.series.map { f.monthShort(it.month) },
                selectedIndex = state.series.indexOfFirst { it.month == sel }.takeIf { it >= 0 },
                onSelect = { i -> vm.selectChartMonth(state.series.getOrNull(i)?.month) },
                description = stringResource(R.string.chart_total_evolution),
            )
            Text(stringResource(R.string.chart_tap_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            state.selectedDetail?.let { MonthDetailPanel(it, base, onOpenItem) }
        }
        Spacer(Modifier.height(8.dp))
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.chart_monthly_change), style = MaterialTheme.typography.titleSmall)
            val changes = state.series.drop(1)
            if (changes.isEmpty()) {
                Text(stringResource(R.string.chart_needs_two_months), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            } else {
                BarChart(
                    values = changes.map { it.change.change?.toFloat() ?: 0f },
                    xLabels = changes.map { f.monthShort(it.month) },
                    positiveColor = fc.increase, negativeColor = fc.decrease,
                    selectedIndex = changes.indexOfFirst { it.month == state.selectedDetail?.month }.takeIf { it >= 0 },
                    onSelect = { i -> vm.selectChartMonth(changes.getOrNull(i)?.month) },
                    description = stringResource(R.string.chart_monthly_change),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        FinanceCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.chart_distribution, f.month(state.selected!!)), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            val slices = state.distribution.map { PieSlice(it.label, it.value.toFloat(), parseHexColor(it.colorHex)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PieChart(slices, size = 160, description = stringResource(R.string.chart_distribution, f.month(state.selected)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    state.distribution.take(8).forEach { sl ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Legend(listOf(sl.label to parseHexColor(sl.colorHex)), Modifier.weight(1f))
                            Text(f.percent(sl.percent, signed = false), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        SectionHeader(stringResource(R.string.dash_items_title))
        s.rows.forEach { row -> ItemSummaryRow(row, base, onClick = { onOpenItem(row.item.id) }) }
        if (s.rows.isEmpty()) {
            Text(stringResource(R.string.month_has_no_balances), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            OutlinedButton(onClick = { onOpenMonthEntry(state.selected!!.value) }) { Text(stringResource(R.string.edit_month_balances)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ItemSummaryRow(row: ItemRow, base: String, onClick: () -> Unit) {
    val f = fmt()
    val c = row.comparison?.change
    FinanceCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemAvatar(row.item.icon, row.item.colorHex)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(row.item.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.share_of_total, f.percent(row.comparison?.shareOfTotal, signed = false)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(f.money(row.valueBase, base), style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content), fontWeight = FontWeight.Bold)
                if (row.missingRate) Text(stringResource(R.string.missing_rate), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.previous_value, f.money(c?.previous, base)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TrendBadge(c?.status, c?.change, c?.percent)
        }
        if (row.netTransfersBase.signum() != 0) {
            Text(stringResource(R.string.item_transfer_effect, f.signedMoney(row.netTransfersBase)), style = MaterialTheme.typography.labelSmall, color = LocalFinanceColors.current.editable)
        }
    }
}

@Composable
fun MonthDetailPanel(d: MonthDetail, base: String, onOpenItem: (String) -> Unit) {
    val f = fmt()
    val fc = LocalFinanceColors.current
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(f.month(d.month), style = MaterialTheme.typography.titleSmall)
    LabeledValue(stringResource(R.string.dash_total_liquidity), f.money(d.total, base))
    LabeledValue(stringResource(R.string.col_change), f.signedMoney(d.change.change), valueColor = com.hossammo.cash.ui.components.trendColor(d.change.status))
    LabeledValue(stringResource(R.string.col_change_pct), f.percent(d.change.percent))
    if (d.transfersCount > 0) LabeledValue(stringResource(R.string.transfers_count), f.integer(d.transfersCount))
    if (d.increased.isNotEmpty()) {
        Text(stringResource(R.string.items_increased), style = MaterialTheme.typography.labelLarge, color = fc.increase)
        d.increased.forEach { r -> LabeledValue(r.item.name, f.signedMoney(r.comparison?.change?.change), valueColor = fc.increase) }
    }
    if (d.decreased.isNotEmpty()) {
        Text(stringResource(R.string.items_decreased), style = MaterialTheme.typography.labelLarge, color = fc.decrease)
        d.decreased.forEach { r -> LabeledValue(r.item.name, f.signedMoney(r.comparison?.change?.change), valueColor = fc.decrease) }
    }
    Text(stringResource(R.string.items_values), style = MaterialTheme.typography.labelLarge)
    d.rows.forEach { r ->
        TextButton(onClick = { onOpenItem(r.item.id) }, modifier = Modifier.fillMaxWidth()) {
            Text(r.item.name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Text(f.money(r.valueBase, base), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
