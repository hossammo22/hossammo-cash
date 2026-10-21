package com.hossammo.cash.ui.screens.dashboard

import androidx.lifecycle.viewModelScope
import com.hossammo.cash.data.analysis.DashboardSummary
import com.hossammo.cash.data.analysis.GroupBy
import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.analysis.MonthDetail
import com.hossammo.cash.data.analysis.PeriodFilter
import com.hossammo.cash.data.analysis.Slice
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.data.settings.UserPreferencesRepository
import com.hossammo.cash.domain.calc.MonthTotal
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.ui.SessionState
import com.hossammo.cash.ui.screens.MessagesViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = true,
    val displayName: String = "",
    val baseCurrency: String = "EGP",
    val months: List<MonthKey> = emptyList(),
    val selected: MonthKey? = null,
    val summary: DashboardSummary? = null,
    val series: List<MonthTotal> = emptyList(),
    val distribution: List<Slice> = emptyList(),
    val selectedDetail: MonthDetail? = null,
    val filter: PeriodFilter = PeriodFilter.ALL,
    val lastRatesUpdate: Long = 0,
    val itemCount: Int = 0,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repo: LiquidityRepository,
    prefs: UserPreferencesRepository,
    private val session: SessionState,
) : MessagesViewModel() {

    private val filter = MutableStateFlow(PeriodFilter.ALL)
    private val chartSelection = MutableStateFlow<MonthKey?>(null)

    val state: StateFlow<DashboardUiState> = combine(
        repo.observeData(), session.selectedMonth, filter, prefs.prefs, chartSelection,
    ) { data, _, f, p, chartSel ->
        val analyzer = LiquidityAnalyzer(data)
        val months = analyzer.monthKeys
        val selected = session.resolve(months)
        if (selected == null) {
            DashboardUiState(loading = false, displayName = p.displayName, baseCurrency = data.baseCurrency, itemCount = data.items.size, lastRatesUpdate = p.lastRateRefreshAt)
        } else {
            val series = analyzer.series(f)
            DashboardUiState(
                loading = false,
                displayName = p.displayName,
                baseCurrency = data.baseCurrency,
                months = months,
                selected = selected,
                summary = analyzer.dashboard(selected, f),
                series = series,
                distribution = analyzer.distribution(selected, GroupBy.ITEM),
                selectedDetail = chartSel?.takeIf { it in months }?.let { analyzer.monthDetail(it) },
                filter = f,
                lastRatesUpdate = p.lastRateRefreshAt,
                itemCount = data.items.size,
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun selectMonth(key: MonthKey) { session.selectMonth(key); chartSelection.value = null }
    fun setFilter(f: PeriodFilter) { filter.value = f }
    fun selectChartMonth(key: MonthKey?) { chartSelection.value = key }
}
