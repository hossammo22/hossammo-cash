package com.hossammo.cash.ui.screens.liquidity

import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.analysis.ItemRow
import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.db.entity.SavingsCategoryEntity
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.domain.calc.FinanceCalculator
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
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

enum class ViewMode { CARDS, TABLE, BY_CATEGORY, BY_LOCATION, BY_CURRENCY }
enum class ItemFilter { ALL, ACTIVE, ARCHIVED, INCLUDED, EXCLUDED, WITH_BALANCE, WITHOUT_BALANCE }
enum class ItemSort { CUSTOM, NAME, VALUE_DESC, VALUE_ASC, CHANGE_DESC, CHANGE_ASC, SHARE }

data class LiquidityQuery(
    val search: String = "",
    val filter: ItemFilter = ItemFilter.ACTIVE,
    val sort: ItemSort = ItemSort.CUSTOM,
    val mode: ViewMode = ViewMode.CARDS,
    val categoryId: String? = null,
)

data class LiquidityUiState(
    val loading: Boolean = true,
    val base: String = "EGP",
    val months: List<MonthKey> = emptyList(),
    val selected: MonthKey? = null,
    val total: BigDecimal = BigDecimal.ZERO,
    val rows: List<ItemRow> = emptyList(),
    val groups: List<Pair<String, List<ItemRow>>> = emptyList(),
    val categories: List<SavingsCategoryEntity> = emptyList(),
    val query: LiquidityQuery = LiquidityQuery(),
    val totalItems: Int = 0,
)

@HiltViewModel
class LiquidityViewModel @Inject constructor(
    private val repo: LiquidityRepository,
    private val session: SessionState,
) : MessagesViewModel() {

    private val query = MutableStateFlow(LiquidityQuery())
    private val groupLabels = MutableStateFlow(GroupLabels("-", "-"))

    data class GroupLabels(val noLocation: String, val noCategory: String)
    fun setLabels(l: GroupLabels) { groupLabels.value = l }

    val state: StateFlow<LiquidityUiState> = combine(repo.observeData(), session.selectedMonth, query, groupLabels) { data, _, q, labels ->
        val a = LiquidityAnalyzer(data)
        val selected = session.resolve(a.monthKeys)
        val allRows = if (selected != null) a.itemRows(selected) else data.items.map {
            ItemRow(it, data.categoryById[it.categoryId], it.locationId?.let { l -> data.locationById[l] }, null, null, null, BigDecimal.ZERO)
        }
        val text = q.search.trim()
        var rows = allRows.filter { r ->
            (text.isEmpty() || r.item.name.contains(text, true) || (r.location?.name?.contains(text, true) ?: false) ||
                (r.category?.name?.contains(text, true) ?: false) || r.item.currencyCode.contains(text, true) || (r.item.notes?.contains(text, true) ?: false)) &&
                (q.categoryId == null || r.item.categoryId == q.categoryId) &&
                when (q.filter) {
                    ItemFilter.ALL -> true
                    ItemFilter.ACTIVE -> !r.item.isArchived
                    ItemFilter.ARCHIVED -> r.item.isArchived
                    ItemFilter.INCLUDED -> r.item.includeInTotal
                    ItemFilter.EXCLUDED -> !r.item.includeInTotal
                    ItemFilter.WITH_BALANCE -> r.balance != null
                    ItemFilter.WITHOUT_BALANCE -> r.balance == null
                }
        }
        rows = when (q.sort) {
            ItemSort.CUSTOM -> rows.sortedBy { it.item.sortOrder }
            ItemSort.NAME -> rows.sortedBy { it.item.name }
            ItemSort.VALUE_DESC -> rows.sortedByDescending { it.valueBase ?: BigDecimal(-1) }
            ItemSort.VALUE_ASC -> rows.sortedBy { it.valueBase ?: BigDecimal(Long.MAX_VALUE) }
            ItemSort.CHANGE_DESC -> rows.sortedByDescending { it.comparison?.change?.change ?: BigDecimal(Long.MIN_VALUE) }
            ItemSort.CHANGE_ASC -> rows.sortedBy { it.comparison?.change?.change ?: BigDecimal(Long.MAX_VALUE) }
            ItemSort.SHARE -> rows.sortedByDescending { it.comparison?.shareOfTotal ?: BigDecimal(-1) }
        }
        val groups = when (q.mode) {
            ViewMode.BY_CATEGORY -> rows.groupBy { it.category?.name ?: labels.noCategory }.toList()
            ViewMode.BY_LOCATION -> rows.groupBy { it.location?.name ?: labels.noLocation }.toList()
            ViewMode.BY_CURRENCY -> rows.groupBy { it.item.currencyCode }.toList()
            else -> emptyList()
        }
        LiquidityUiState(
            loading = false, base = data.baseCurrency, months = a.monthKeys, selected = selected,
            total = selected?.let { a.total(it) } ?: BigDecimal.ZERO, rows = rows, groups = groups,
            categories = data.categories, query = q, totalItems = data.items.size,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiquidityUiState())

    fun groupTotal(rows: List<ItemRow>): BigDecimal =
        FinanceCalculator.monthTotal(rows.mapNotNull { r -> r.valueBase?.let { com.hossammo.cash.domain.calc.ItemMonthValue(r.item.id, it, r.item.includeInTotal) } })

    fun selectMonth(k: MonthKey) = session.selectMonth(k)
    fun updateQuery(transform: (LiquidityQuery) -> LiquidityQuery) { query.value = transform(query.value) }

    fun duplicate(id: String, suffix: String) = viewModelScope.launch {
        repo.duplicateItem(id, suffix).onSuccess { message(R.string.msg_item_copied) }.onFailure { message(R.string.msg_error, true) }
    }

    fun setArchived(id: String, archived: Boolean) = viewModelScope.launch {
        repo.setArchived(id, archived)
        message(if (archived) R.string.msg_item_archived else R.string.msg_item_unarchived, undo = { viewModelScope.launch { repo.setArchived(id, !archived) } })
    }

    fun delete(id: String) = viewModelScope.launch {
        repo.softDeleteItem(id)
        message(R.string.msg_item_deleted, undo = { viewModelScope.launch { repo.restoreItem(id) } })
    }
}
