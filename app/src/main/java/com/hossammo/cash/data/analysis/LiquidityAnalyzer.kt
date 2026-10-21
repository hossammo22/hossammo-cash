package com.hossammo.cash.data.analysis

import com.hossammo.cash.data.db.entity.LiquidityItemEntity
import com.hossammo.cash.data.db.entity.LocationEntity
import com.hossammo.cash.data.db.entity.MonthlyBalanceEntity
import com.hossammo.cash.data.db.entity.SavingsCategoryEntity
import com.hossammo.cash.data.db.entity.TransferEntity
import com.hossammo.cash.data.model.LiquidityData
import com.hossammo.cash.domain.calc.ChangeResult
import com.hossammo.cash.domain.calc.FinanceCalculator
import com.hossammo.cash.domain.calc.ItemComparison
import com.hossammo.cash.domain.calc.ItemMonthValue
import com.hossammo.cash.domain.calc.MonthData
import com.hossammo.cash.domain.calc.MonthTotal
import com.hossammo.cash.domain.calc.PeriodStats
import com.hossammo.cash.domain.calc.TransferCalculator
import com.hossammo.cash.domain.calc.TransferInput
import com.hossammo.cash.domain.model.LiquidityLevel
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.domain.model.TrendStatus
import java.math.BigDecimal

enum class GroupBy { ITEM, CATEGORY, LOCATION, CURRENCY, LEVEL }

/** فترة زمنية للفلترة. */
enum class PeriodFilter(val months: Int?) { M3(3), M6(6), Y1(12), Y2(24), ALL(null), CUSTOM(null) }

data class ItemRow(
    val item: LiquidityItemEntity,
    val category: SavingsCategoryEntity?,
    val location: LocationEntity?,
    val balance: MonthlyBalanceEntity?,
    val previousBalance: MonthlyBalanceEntity?,
    val comparison: ItemComparison?,
    /** صافي التحويلات الداخلية الواردة للعنصر خلال الشهر (بالعملة الأساسية). */
    val netTransfersBase: BigDecimal,
) {
    val valueBase: BigDecimal? get() = balance?.valueBase
    val status: TrendStatus? get() = comparison?.change?.status
    /** رصيد بعملة أجنبية دون سعر صرف متاح. */
    val missingRate: Boolean get() = balance != null && balance.valueBase == null
}

data class Slice(val key: String, val label: String, val colorHex: String?, val value: BigDecimal, val percent: BigDecimal?)

data class MonthDetail(
    val month: MonthKey,
    val total: BigDecimal,
    val change: ChangeResult,
    val rows: List<ItemRow>,
    val increased: List<ItemRow>,
    val decreased: List<ItemRow>,
    val transfersCount: Int,
    val transferFeesBase: BigDecimal,
)

data class DashboardSummary(
    val selected: MonthKey,
    val total: BigDecimal,
    val lastRecorded: MonthTotal?,
    val changeVsPrevious: ChangeResult,
    val stats: PeriodStats,
    val biggestIncrease: ItemRow?,
    val biggestDecrease: ItemRow?,
    val largestShare: ItemRow?,
    val immediate: BigDecimal,
    val needsLiquidation: BigDecimal,
    val lastUpdatedAt: Long?,
    val rows: List<ItemRow>,
    val transferFeesBase: BigDecimal,
    val netTransfersMoved: BigDecimal,
)

/**
 * يحوّل البيانات الخام إلى نماذج عرض اعتمادًا على طبقة الحسابات (domain).
 * لا توجد أي معادلة مالية داخل الواجهات.
 */
class LiquidityAnalyzer(val data: LiquidityData) {

    val monthKeys: List<MonthKey> by lazy { data.months.map { MonthKey(it.monthKey) }.sorted() }

    private val balancesByMonth: Map<Int, List<MonthlyBalanceEntity>> by lazy { data.balances.groupBy { it.monthKey } }
    private val activeItemIds by lazy { data.items.map { it.id }.toSet() }

    fun latestMonth(): MonthKey? = monthKeys.lastOrNull()

    fun previousKey(key: MonthKey): MonthKey? = monthKeys.lastOrNull { it < key }

    fun balancesFor(key: MonthKey): List<MonthlyBalanceEntity> =
        balancesByMonth[key.value].orEmpty().filter { it.itemId in activeItemIds }

    fun monthData(key: MonthKey): MonthData = MonthData(
        key,
        balancesFor(key).mapNotNull { b ->
            val item = data.itemById[b.itemId] ?: return@mapNotNull null
            val v = b.valueBase ?: return@mapNotNull null
            ItemMonthValue(item.id, v, item.includeInTotal, item.liquidityLevel)
        },
    )

    fun total(key: MonthKey): BigDecimal = FinanceCalculator.monthTotal(monthData(key))

    fun transfersIn(key: MonthKey): List<TransferEntity> = data.transfers.filter { it.monthKey == key.value }

    /** رسوم التحويلات المخصومة في الشهر بالعملة الأساسية. */
    fun transferFeesBase(key: MonthKey): BigDecimal {
        val bals = balancesFor(key).associateBy { it.itemId }
        return Money.money(transfersIn(key).filter { it.deductFee && it.appliedToBalances }.fold(BigDecimal.ZERO) { acc, t ->
            val rate = bals[t.fromItemId]?.exchangeRate ?: data.rateByCode[t.currencyCode]?.rateToBase
                ?: if (t.currencyCode == data.baseCurrency) BigDecimal.ONE else BigDecimal.ZERO
            acc + t.fee.multiply(rate)
        })
    }

    private fun netTransfersBase(itemId: String, key: MonthKey, balance: MonthlyBalanceEntity?): BigDecimal {
        val list = transfersIn(key).filter { it.appliedToBalances && (it.fromItemId == itemId || it.toItemId == itemId) }
            .map { TransferInput(it.fromItemId, it.toItemId, it.amount, it.receivedAmount, it.fee, it.deductFee) }
        if (list.isEmpty()) return BigDecimal.ZERO
        val netOriginal = TransferCalculator.netForItem(itemId, list)
        val rate = balance?.exchangeRate ?: BigDecimal.ONE
        return Money.money(netOriginal.multiply(rate))
    }

    fun itemRows(key: MonthKey, includeArchived: Boolean = true): List<ItemRow> {
        val prevKey = previousKey(key)
        val current = monthData(key)
        val previous = prevKey?.let { monthData(it) }
        val comparisons = FinanceCalculator.compareItems(current, previous).associateBy { it.itemId }
        val curBalances = balancesFor(key).associateBy { it.itemId }
        val prevBalances = prevKey?.let { balancesFor(it) }.orEmpty().associateBy { it.itemId }
        return data.items
            .filter { includeArchived || !it.isArchived || curBalances.containsKey(it.id) }
            .map { item ->
                val b = curBalances[item.id]
                ItemRow(
                    item = item,
                    category = data.categoryById[item.categoryId],
                    location = item.locationId?.let { data.locationById[it] },
                    balance = b,
                    previousBalance = prevBalances[item.id],
                    comparison = comparisons[item.id],
                    netTransfersBase = netTransfersBase(item.id, key, b),
                )
            }
    }

    fun series(filter: PeriodFilter = PeriodFilter.ALL, custom: Pair<MonthKey, MonthKey>? = null): List<MonthTotal> {
        val all = FinanceCalculator.totalsSeries(monthKeys.map { monthData(it) })
        return applyFilter(all, filter, custom) { it.month }
    }

    fun <T> applyFilter(list: List<T>, filter: PeriodFilter, custom: Pair<MonthKey, MonthKey>?, keyOf: (T) -> MonthKey): List<T> {
        if (list.isEmpty()) return list
        return when {
            filter == PeriodFilter.CUSTOM && custom != null -> list.filter { keyOf(it) >= custom.first && keyOf(it) <= custom.second }
            filter.months != null -> {
                val last = keyOf(list.last())
                val from = last.minusMonths(filter.months - 1)
                list.filter { keyOf(it) >= from }
            }
            else -> list
        }
    }

    /** تطور قيمة عنصر واحد عبر الأشهر. */
    fun itemSeries(itemId: String): List<Pair<MonthKey, BigDecimal>> = monthKeys.mapNotNull { k ->
        balancesFor(k).firstOrNull { it.itemId == itemId }?.valueBase?.let { k to it }
    }

    fun itemHistory(itemId: String): List<Pair<MonthlyBalanceEntity, ChangeResult>> {
        var prev: BigDecimal? = null
        return monthKeys.mapNotNull { k ->
            val b = balancesFor(k).firstOrNull { it.itemId == itemId } ?: return@mapNotNull null
            val v = b.valueBase ?: return@mapNotNull null
            val c = FinanceCalculator.change(v, prev)
            prev = v
            b to c
        }
    }

    fun distribution(key: MonthKey, by: GroupBy, levelLabel: (LiquidityLevel) -> String = { it.name }, noLocation: String = "-"): List<Slice> {
        val values = monthData(key).values
        val dist = FinanceCalculator.distribution(values) { v ->
            val item = data.itemById[v.itemId]
            when (by) {
                GroupBy.ITEM -> v.itemId
                GroupBy.CATEGORY -> item?.categoryId ?: "-"
                GroupBy.LOCATION -> item?.locationId ?: "-"
                GroupBy.CURRENCY -> item?.currencyCode ?: "-"
                GroupBy.LEVEL -> v.liquidityLevel.name
            }
        }
        return dist.map { (k, share) ->
            val (label, color) = when (by) {
                GroupBy.ITEM -> data.itemById[k]?.let { it.name to it.colorHex } ?: (k to null)
                GroupBy.CATEGORY -> data.categoryById[k]?.let { it.name to it.colorHex } ?: (k to null)
                GroupBy.LOCATION -> (data.locationById[k]?.name ?: noLocation) to null
                GroupBy.CURRENCY -> k to null
                GroupBy.LEVEL -> levelLabel(LiquidityLevel.valueOf(k)) to null
            }
            Slice(k, label, color, share.value, share.percent)
        }
    }

    fun monthDetail(key: MonthKey): MonthDetail {
        val rows = itemRows(key).filter { it.balance != null }
        val total = total(key)
        val prev = previousKey(key)?.let { total(it) }
        return MonthDetail(
            month = key,
            total = total,
            change = FinanceCalculator.change(total, prev),
            rows = rows.sortedByDescending { it.valueBase ?: BigDecimal.ZERO },
            increased = rows.filter { it.status == TrendStatus.INCREASED }.sortedByDescending { it.comparison?.change?.change },
            decreased = rows.filter { it.status == TrendStatus.DECREASED }.sortedBy { it.comparison?.change?.change },
            transfersCount = transfersIn(key).size,
            transferFeesBase = transferFeesBase(key),
        )
    }

    fun dashboard(selected: MonthKey, filter: PeriodFilter = PeriodFilter.ALL): DashboardSummary {
        val rows = itemRows(selected)
        val withBalance = rows.filter { it.comparison != null }
        val comparisons = withBalance.mapNotNull { it.comparison }
        val inc = FinanceCalculator.biggestIncrease(comparisons)?.itemId
        val dec = FinanceCalculator.biggestDecrease(comparisons)?.itemId
        val largest = FinanceCalculator.largestShare(comparisons)?.itemId
        val values = monthData(selected).values
        val total = FinanceCalculator.monthTotal(values)
        val prev = previousKey(selected)?.let { total(it) }
        val fullSeries = series(PeriodFilter.ALL)
        val upToSelected = fullSeries.filter { it.month <= selected }
        val lastUpdated = (data.balances.maxOfOrNull { it.updatedAt })
        val moved = transfersIn(selected).filter { it.appliedToBalances }.fold(BigDecimal.ZERO) { a, t ->
            val rate = data.rateByCode[t.currencyCode]?.rateToBase ?: BigDecimal.ONE
            a + if (t.currencyCode == data.baseCurrency) t.amount else t.amount.multiply(rate)
        }
        return DashboardSummary(
            selected = selected,
            total = total,
            lastRecorded = fullSeries.lastOrNull(),
            changeVsPrevious = FinanceCalculator.change(total, prev),
            stats = FinanceCalculator.periodStats(applyFilter(upToSelected, filter, null) { it.month }),
            biggestIncrease = withBalance.firstOrNull { it.item.id == inc },
            biggestDecrease = withBalance.firstOrNull { it.item.id == dec },
            largestShare = withBalance.firstOrNull { it.item.id == largest },
            immediate = FinanceCalculator.totalByLevel(values, LiquidityLevel.IMMEDIATE),
            needsLiquidation = FinanceCalculator.totalByLevel(values, LiquidityLevel.NEEDS_LIQUIDATION),
            lastUpdatedAt = lastUpdated,
            rows = rows.filter { it.balance != null }.sortedByDescending { it.valueBase ?: BigDecimal.ZERO },
            transferFeesBase = transferFeesBase(selected),
            netTransfersMoved = Money.money(moved),
        )
    }

    /** أكبر العناصر نموًا/تراجعًا في الشهر. */
    fun topMovers(key: MonthKey, increasing: Boolean, limit: Int = 5): List<ItemRow> {
        val rows = itemRows(key).filter { it.comparison?.change?.change != null }
        return if (increasing) rows.filter { it.comparison!!.change.change!!.signum() > 0 }.sortedByDescending { it.comparison!!.change.change }.take(limit)
        else rows.filter { it.comparison!!.change.change!!.signum() < 0 }.sortedBy { it.comparison!!.change.change }.take(limit)
    }
}
