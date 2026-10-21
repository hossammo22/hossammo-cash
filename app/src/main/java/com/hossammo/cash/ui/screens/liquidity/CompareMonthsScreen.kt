package com.hossammo.cash.ui.screens.liquidity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.model.LiquidityData
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.domain.calc.ChangeResult
import com.hossammo.cash.domain.calc.FinanceCalculator
import com.hossammo.cash.domain.calc.ItemComparison
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.ui.components.DropdownField
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.LabeledValue
import com.hossammo.cash.ui.components.TrendBadge
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.MessagesViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CompareState(
    val months: List<MonthKey> = emptyList(),
    val a: MonthKey? = null,
    val b: MonthKey? = null,
    val total: ChangeResult? = null,
    val items: List<ItemComparison> = emptyList(),
    val data: LiquidityData = LiquidityData.EMPTY,
)

@HiltViewModel
class CompareViewModel @Inject constructor(repo: LiquidityRepository) : MessagesViewModel() {
    private val a = MutableStateFlow<MonthKey?>(null)
    private val b = MutableStateFlow<MonthKey?>(null)
    val state: StateFlow<CompareState> = combine(repo.observeData(), a, b) { data, ka, kb ->
        val an = LiquidityAnalyzer(data)
        val months = an.monthKeys
        val ma = ka?.takeIf { it in months } ?: months.getOrNull(months.size - 2)
        val mb = kb?.takeIf { it in months } ?: months.lastOrNull()
        if (ma == null || mb == null) CompareState(months = months, data = data)
        else {
            val (total, items) = FinanceCalculator.compareMonths(an.monthData(ma), an.monthData(mb))
            CompareState(months, ma, mb, total, items.sortedByDescending { it.change.change ?: java.math.BigDecimal.ZERO }, data)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareState())

    fun setA(k: MonthKey) { a.value = k }
    fun setB(k: MonthKey) { b.value = k }
}

@Composable
fun CompareMonthsScreen(onBack: () -> Unit, vm: CompareViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val f = fmt()
    AppScaffold(title = stringResource(R.string.compare_months), onBack = onBack) { padding ->
        if (s.months.size < 2) { EmptyState(stringResource(R.string.compare_needs_two), stringResource(R.string.chart_needs_two_months), Modifier.padding(padding)); return@AppScaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(stringResource(R.string.first_month), s.months, s.a, { f.month(it) }, vm::setA, Modifier.weight(1f))
                DropdownField(stringResource(R.string.second_month), s.months, s.b, { f.month(it) }, vm::setB, Modifier.weight(1f))
            }
            val t = s.total ?: return@Column
            FinanceCard(Modifier.fillMaxWidth()) {
                LabeledValue(f.month(s.a!!), f.money(t.previous, s.data.baseCurrency))
                LabeledValue(f.month(s.b!!), f.money(t.current, s.data.baseCurrency))
                TrendBadge(t.status, t.change, t.percent)
            }
            s.items.forEach { c ->
                val item = s.data.itemById[c.itemId] ?: return@forEach
                FinanceCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TrendBadge(c.change.status, c.change.change, c.change.percent, compact = true)
                    }
                    LabeledValue(f.month(s.a!!), f.money(c.change.previous))
                    LabeledValue(f.month(s.b!!), f.money(c.change.current))
                    LabeledValue(stringResource(R.string.col_change), f.signedMoney(c.change.change), valueColor = com.hossammo.cash.ui.components.trendColor(c.change.status))
                }
            }
        }
    }
}
