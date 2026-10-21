package com.hossammo.cash.report

import android.content.Context
import com.hossammo.cash.R
import com.hossammo.cash.data.analysis.GroupBy
import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.model.DebtWithPayments
import com.hossammo.cash.data.model.LiquidityData
import com.hossammo.cash.domain.calc.DebtCalculator
import com.hossammo.cash.domain.calc.DebtSnapshot
import com.hossammo.cash.domain.calc.FinanceCalculator
import com.hossammo.cash.domain.model.AssetType
import com.hossammo.cash.domain.model.DebtDirection
import com.hossammo.cash.domain.model.DebtStatus
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.util.AppFormatter
import java.math.BigDecimal
import java.time.LocalDate

/**
 * يبني محتوى التقارير من البيانات الحقيقية. تقارير الديون منفصلة عن تقارير السيولة.
 */
class ReportGenerator(
    private val context: Context,
    private val fmt: AppFormatter,
    private val data: LiquidityData,
    private val debts: List<DebtWithPayments>,
    private val today: LocalDate = LocalDate.now(),
) {
    private val analyzer = LiquidityAnalyzer(data)
    private fun s(id: Int) = context.getString(id)
    private fun money(v: BigDecimal?) = ReportCell(fmt.money(v, ignoreHide = true), v)
    private fun signed(v: BigDecimal?) = ReportCell(fmt.signedMoney(v), v)
    private fun pct(v: BigDecimal?) = ReportCell(fmt.percent(v), v)
    private val base get() = data.baseCurrency

    private fun monthsIn(p: ReportParams): List<MonthKey> {
        val all = analyzer.monthKeys
        val from = p.from ?: all.firstOrNull() ?: return emptyList()
        val to = p.to ?: all.lastOrNull() ?: return emptyList()
        return all.filter { it >= from && it <= to }
    }

    private fun periodSubtitle(p: ReportParams): String {
        val m = monthsIn(p)
        return if (m.isEmpty()) s(R.string.report_no_data) else "${fmt.month(m.first())} — ${fmt.month(m.last())}"
    }

    fun generate(p: ReportParams): ReportDocument {
        val generatedAt = fmt.dateTime(System.currentTimeMillis())
        val title = s(p.type.titleRes)
        return when (p.type) {
            ReportType.TOTAL_PERIOD -> totalPeriod(p, title, generatedAt)
            ReportType.PER_ITEM -> perItem(p, title, generatedAt)
            ReportType.COMPARE_MONTHS -> compare(p, title, generatedAt)
            ReportType.INCREASE_DECREASE -> increaseDecrease(p, title, generatedAt)
            ReportType.DISTRIBUTION -> distribution(p, title, generatedAt)
            ReportType.FOREIGN_CURRENCIES -> filteredItems(p, title, generatedAt) { !it.currencyCode.equals(base, true) }
            ReportType.FARM_FACTORY -> filteredItems(p, title, generatedAt) { it.assetType == AssetType.PROJECT || it.systemKey in setOf("farm", "factory") }
            ReportType.TRANSFERS -> transfers(p, title, generatedAt)
            ReportType.TOP_GROWTH -> topMovers(p, title, generatedAt, true)
            ReportType.TOP_DECLINE -> topMovers(p, title, generatedAt, false)
            ReportType.OWED_TO_ME -> debtList(title, generatedAt, DebtDirection.OWED_TO_ME, overdueOnly = false)
            ReportType.MY_OBLIGATIONS -> debtList(title, generatedAt, DebtDirection.I_OWE, overdueOnly = false)
            ReportType.OVERDUE_DEBTS -> debtList(title, generatedAt, null, overdueOnly = true)
            ReportType.PAYMENTS_LOG -> paymentsLog(title, generatedAt)
            ReportType.ALL_DATA -> allData(title, generatedAt)
        }
    }

    private fun totalPeriod(p: ReportParams, title: String, at: String): ReportDocument {
        val months = monthsIn(p).toSet()
        val series = analyzer.series().filter { it.month in months }
        val stats = FinanceCalculator.periodStats(series)
        val rows = series.map { listOf(ReportCell.t(fmt.month(it.month)), money(it.total), signed(it.change.change), pct(it.change.percent)) }
        val summary = buildList {
            stats.lastMonth?.let { add(s(R.string.dash_last_total) to fmt.money(it.total, base, true)) }
            stats.highest?.let { add(s(R.string.dash_highest) to "${fmt.money(it.total, base, true)} (${fmt.month(it.month)})") }
            stats.lowest?.let { add(s(R.string.dash_lowest) to "${fmt.money(it.total, base, true)} (${fmt.month(it.month)})") }
            stats.average?.let { add(s(R.string.dash_average) to fmt.money(it, base, true)) }
            stats.changeSinceFirst?.let { add(s(R.string.dash_change_since_start) to "${fmt.signedMoney(it.change)} (${fmt.percent(it.percent)})") }
        }
        return ReportDocument(title, periodSubtitle(p), at, summary, listOf(
            ReportTable(title, listOf(s(R.string.col_month), s(R.string.col_total), s(R.string.col_change), s(R.string.col_change_pct)), rows)
        ))
    }

    private fun perItem(p: ReportParams, title: String, at: String): ReportDocument {
        val months = monthsIn(p)
        val tables = data.items.map { item ->
            val rows = months.mapNotNull { k ->
                val row = analyzer.itemRows(k).firstOrNull { it.item.id == item.id && it.balance != null } ?: return@mapNotNull null
                val c = row.comparison?.change
                listOf(
                    ReportCell.t(fmt.month(k)), money(row.balance?.amountOriginal), ReportCell.t(row.item.currencyCode),
                    money(row.valueBase), signed(c?.change), pct(c?.percent), ReportCell.t(fmt.trend(c?.status)),
                )
            }
            ReportTable(item.name, listOf(s(R.string.col_month), s(R.string.col_original), s(R.string.col_currency), s(R.string.col_value_base), s(R.string.col_change), s(R.string.col_change_pct), s(R.string.col_status)), rows)
        }.filter { it.rows.isNotEmpty() }
        return ReportDocument(title, periodSubtitle(p), at, emptyList(), tables)
    }

    private fun compare(p: ReportParams, title: String, at: String): ReportDocument {
        val a = p.monthA ?: analyzer.monthKeys.dropLast(1).lastOrNull()
        val b = p.monthB ?: analyzer.monthKeys.lastOrNull()
        if (a == null || b == null) return ReportDocument(title, s(R.string.report_no_data), at, emptyList(), emptyList())
        val (total, items) = FinanceCalculator.compareMonths(analyzer.monthData(a), analyzer.monthData(b))
        val rows = items.map { c ->
            val item = data.itemById[c.itemId]
            listOf(
                ReportCell.t(item?.name ?: "-"), money(c.change.previous), money(c.change.current),
                signed(c.change.change), pct(c.change.percent), ReportCell.t(fmt.trend(c.change.status)),
            )
        }
        val summary = listOf(
            fmt.month(a) to fmt.money(total.previous, base, true),
            fmt.month(b) to fmt.money(total.current, base, true),
            s(R.string.col_change) to "${fmt.signedMoney(total.change)} (${fmt.percent(total.percent)})",
        )
        return ReportDocument(title, "${fmt.month(a)} ↔ ${fmt.month(b)}", at, summary, listOf(
            ReportTable(title, listOf(s(R.string.col_item), fmt.month(a), fmt.month(b), s(R.string.col_change), s(R.string.col_change_pct), s(R.string.col_status)), rows)
        ))
    }

    private fun increaseDecrease(p: ReportParams, title: String, at: String): ReportDocument {
        val tables = monthsIn(p).reversed().map { k ->
            val d = analyzer.monthDetail(k)
            val rows = (d.increased + d.decreased).map { r ->
                listOf(ReportCell.t(r.item.name), signed(r.comparison?.change?.change), pct(r.comparison?.change?.percent), ReportCell.t(fmt.trend(r.status)))
            }
            ReportTable("${fmt.month(k)} • ${fmt.signedMoney(d.change.change)}", listOf(s(R.string.col_item), s(R.string.col_change), s(R.string.col_change_pct), s(R.string.col_status)), rows)
        }
        return ReportDocument(title, periodSubtitle(p), at, emptyList(), tables)
    }

    private fun distribution(p: ReportParams, title: String, at: String): ReportDocument {
        val k = p.to ?: analyzer.latestMonth() ?: return ReportDocument(title, s(R.string.report_no_data), at, emptyList(), emptyList())
        val noLoc = s(R.string.no_location)
        val groups = listOf(
            GroupBy.ITEM to R.string.group_item, GroupBy.CATEGORY to R.string.group_category,
            GroupBy.LOCATION to R.string.group_location, GroupBy.CURRENCY to R.string.group_currency, GroupBy.LEVEL to R.string.group_level,
        )
        val tables = groups.map { (g, res) ->
            val slices = analyzer.distribution(k, g, { fmt.level(it) }, noLoc)
            ReportTable(s(res), listOf(s(R.string.col_name), s(R.string.col_value_base), s(R.string.col_share)), slices.map {
                listOf(ReportCell.t(it.label), money(it.value), pct(it.percent?.let { p0 -> p0 }))
            })
        }
        return ReportDocument(title, fmt.month(k), at, listOf(s(R.string.col_total) to fmt.money(analyzer.total(k), base, true)), tables)
    }

    private fun filteredItems(p: ReportParams, title: String, at: String, predicate: (com.hossammo.cash.data.db.entity.LiquidityItemEntity) -> Boolean): ReportDocument {
        val rows = monthsIn(p).reversed().flatMap { k ->
            analyzer.itemRows(k).filter { it.balance != null && predicate(it.item) }.map { r ->
                val b = r.balance!!
                listOf(
                    ReportCell.t(fmt.month(k)), ReportCell.t(r.item.name), ReportCell.t(r.location?.name ?: "-"), ReportCell.t(b.currencyCode),
                    money(b.amountOriginal), ReportCell(fmt.rate(b.exchangeRate), b.exchangeRate), money(b.valueBase),
                    ReportCell.t(fmt.rateSource(b.rateSource)), signed(r.comparison?.change?.change),
                )
            }
        }
        return ReportDocument(title, periodSubtitle(p), at, emptyList(), listOf(
            ReportTable(title, listOf(s(R.string.col_month), s(R.string.col_item), s(R.string.col_location), s(R.string.col_currency), s(R.string.col_original), s(R.string.col_rate), s(R.string.col_value_base), s(R.string.col_rate_type), s(R.string.col_change)), rows)
        ))
    }

    private fun transfers(p: ReportParams, title: String, at: String): ReportDocument {
        val months = monthsIn(p).map { it.value }.toSet()
        val list = data.transfers.filter { months.isEmpty() || it.monthKey in months }
        val rows = list.map { t ->
            listOf(
                ReportCell.t(fmt.dateFromEpochDay(t.transferDate)), ReportCell.t(data.itemById[t.fromItemId]?.name ?: "-"),
                ReportCell.t(data.itemById[t.toItemId]?.name ?: "-"), money(t.amount), ReportCell.t(t.currencyCode), money(t.fee),
                ReportCell.t(t.reference ?: ""), ReportCell.t(t.notes ?: ""),
            )
        }
        val fees = list.filter { it.deductFee }.fold(BigDecimal.ZERO) { a, t -> a + t.fee }
        return ReportDocument(title, periodSubtitle(p), at, listOf(
            s(R.string.transfers_count) to fmt.integer(list.size),
            s(R.string.transfer_fees_total) to fmt.money(fees, null, true),
            s(R.string.transfer_note_title) to s(R.string.transfer_note_body),
        ), listOf(
            ReportTable(title, listOf(s(R.string.col_date), s(R.string.col_from), s(R.string.col_to), s(R.string.col_amount), s(R.string.col_currency), s(R.string.col_fee), s(R.string.col_reference), s(R.string.col_notes)), rows)
        ))
    }

    private fun topMovers(p: ReportParams, title: String, at: String, growth: Boolean): ReportDocument {
        val k = p.to ?: analyzer.latestMonth() ?: return ReportDocument(title, s(R.string.report_no_data), at, emptyList(), emptyList())
        val months = monthsIn(p)
        // التغير خلال الفترة لكل عنصر: قيمة آخر شهر − قيمة أول شهر في الفترة
        val first = months.firstOrNull()
        val rowsPeriod = if (first != null && first != k) {
            val (_, items) = FinanceCalculator.compareMonths(analyzer.monthData(first), analyzer.monthData(k))
            items.filter { c -> c.change.change?.let { if (growth) it.signum() > 0 else it.signum() < 0 } == true }
                .sortedBy { if (growth) it.change.change!!.negate() else it.change.change!! }
                .map { c -> listOf(ReportCell.t(data.itemById[c.itemId]?.name ?: "-"), money(c.change.previous), money(c.change.current), signed(c.change.change), pct(c.change.percent)) }
        } else emptyList()
        val rowsMonth = analyzer.topMovers(k, growth, 20).map { r ->
            listOf(ReportCell.t(r.item.name), money(r.comparison?.change?.previous), money(r.valueBase), signed(r.comparison?.change?.change), pct(r.comparison?.change?.percent))
        }
        val headers = listOf(s(R.string.col_item), s(R.string.col_previous), s(R.string.col_current), s(R.string.col_change), s(R.string.col_change_pct))
        return ReportDocument(title, periodSubtitle(p), at, emptyList(), listOfNotNull(
            ReportTable("${s(R.string.report_vs_previous)} • ${fmt.month(k)}", headers, rowsMonth),
            if (first != null && first != k) ReportTable("${fmt.month(first)} → ${fmt.month(k)}", headers, rowsPeriod) else null,
        ))
    }

    private fun snapshots(list: List<DebtWithPayments>) = list.map { d ->
        DebtSnapshot(d.debt.id, d.debt.name, d.debt.direction, d.debt.originalAmount, d.payments.map { it.amount }, d.debt.dueDate?.let { LocalDate.ofEpochDay(it) })
    }

    private fun debtList(title: String, at: String, direction: DebtDirection?, overdueOnly: Boolean): ReportDocument {
        val list = debts.filter { direction == null || it.debt.direction == direction }
        val snaps = snapshots(list)
        val rows = snaps.mapNotNull { s0 ->
            val f = DebtCalculator.figures(s0, today)
            if (overdueOnly && f.status != DebtStatus.OVERDUE) return@mapNotNull null
            val d = list.first { it.debt.id == s0.id }.debt
            listOf(
                ReportCell.t(d.name), ReportCell.t(s(if (d.direction == DebtDirection.OWED_TO_ME) R.string.debt_owed_to_me else R.string.debt_i_owe)),
                money(d.originalAmount), money(f.paid), money(f.remaining), ReportCell.t(fmt.dateFromEpochDay(d.startDate)),
                ReportCell.t(fmt.dateFromEpochDay(d.dueDate)), ReportCell.t(fmt.debtStatus(f.status)), ReportCell.t(d.notes ?: ""),
            )
        }
        val sum = DebtCalculator.summary(snaps, today)
        val summary = when (direction) {
            DebtDirection.OWED_TO_ME -> listOf(s(R.string.debt_total_owed) to fmt.money(sum.owedToMeTotal, base, true), s(R.string.debt_collected) to fmt.money(sum.collected, base, true), s(R.string.debt_remaining_to_me) to fmt.money(sum.remainingToMe, base, true))
            DebtDirection.I_OWE -> listOf(s(R.string.debt_total_obligations) to fmt.money(sum.iOweTotal, base, true), s(R.string.debt_paid_by_me) to fmt.money(sum.paidByMe, base, true), s(R.string.debt_remaining_on_me) to fmt.money(sum.remainingOnMe, base, true))
            null -> listOf(s(R.string.debt_overdue_count) to fmt.integer(rows.size))
        } + (s(R.string.debts_separate_title) to s(R.string.debts_separate_body))
        return ReportDocument(title, fmt.date(today), at, summary, listOf(
            ReportTable(title, listOf(s(R.string.col_name), s(R.string.col_type), s(R.string.col_original), s(R.string.col_paid), s(R.string.col_remaining), s(R.string.col_start), s(R.string.col_due), s(R.string.col_status), s(R.string.col_notes)), rows)
        ))
    }

    private fun paymentsLog(title: String, at: String): ReportDocument {
        val rows = debts.flatMap { d -> d.payments.map { d to it } }.sortedByDescending { it.second.paymentDate }.map { (d, p) ->
            listOf(
                ReportCell.t(fmt.dateFromEpochDay(p.paymentDate)), ReportCell.t(d.debt.name),
                ReportCell.t(s(if (d.debt.direction == DebtDirection.OWED_TO_ME) R.string.debt_owed_to_me else R.string.debt_i_owe)),
                money(p.amount), ReportCell.t(p.method ?: ""), ReportCell.t(p.reference ?: ""), ReportCell.t(p.notes ?: ""),
            )
        }
        return ReportDocument(title, fmt.date(today), at, listOf(s(R.string.payments_count) to fmt.integer(rows.size)), listOf(
            ReportTable(title, listOf(s(R.string.col_date), s(R.string.col_name), s(R.string.col_type), s(R.string.col_amount), s(R.string.col_method), s(R.string.col_reference), s(R.string.col_notes)), rows)
        ))
    }

    /** تصدير جميع البيانات (للنسخ الخارجي إلى Excel/CSV). */
    private fun allData(title: String, at: String): ReportDocument {
        val items = ReportTable(s(R.string.nav_liquidity), listOf(s(R.string.col_name), s(R.string.col_category), s(R.string.col_location), s(R.string.col_type), s(R.string.col_currency), s(R.string.col_level), s(R.string.col_in_total), s(R.string.col_status)),
            data.items.map { i ->
                listOf(ReportCell.t(i.name), ReportCell.t(data.categoryById[i.categoryId]?.name ?: "-"), ReportCell.t(i.locationId?.let { data.locationById[it]?.name } ?: "-"),
                    ReportCell.t(fmt.assetType(i.assetType)), ReportCell.t(i.currencyCode), ReportCell.t(fmt.level(i.liquidityLevel)),
                    ReportCell.t(s(if (i.includeInTotal) R.string.yes else R.string.no)), ReportCell.t(s(if (i.isArchived) R.string.archived else R.string.active)))
            })
        val balances = ReportTable(s(R.string.monthly_balances), listOf(s(R.string.col_month), s(R.string.col_item), s(R.string.col_original), s(R.string.col_currency), s(R.string.col_rate), s(R.string.col_value_base), s(R.string.col_rate_type), s(R.string.col_notes)),
            data.balances.sortedWith(compareBy({ it.monthKey }, { data.itemById[it.itemId]?.sortOrder ?: 0 })).map { b ->
                listOf(ReportCell.t(fmt.month(MonthKey(b.monthKey))), ReportCell.t(data.itemById[b.itemId]?.name ?: "-"), money(b.amountOriginal), ReportCell.t(b.currencyCode),
                    ReportCell(fmt.rate(b.exchangeRate), b.exchangeRate), money(b.valueBase), ReportCell.t(fmt.rateSource(b.rateSource)), ReportCell.t(b.notes ?: ""))
            })
        val docs = listOf(items, balances) +
            totalPeriod(ReportParams(ReportType.TOTAL_PERIOD), s(R.string.report_total_period), at).tables +
            transfers(ReportParams(ReportType.TRANSFERS), s(R.string.report_transfers), at).tables +
            debtList(s(R.string.nav_debts), at, null, overdueOnly = false).tables +
            paymentsLog(s(R.string.report_payments_log), at).tables
        return ReportDocument(title, fmt.date(today), at, emptyList(), docs)
    }
}
