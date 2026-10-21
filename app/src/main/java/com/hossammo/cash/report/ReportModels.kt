package com.hossammo.cash.report

import com.hossammo.cash.R
import com.hossammo.cash.domain.model.MonthKey
import java.math.BigDecimal

enum class ReportType(val titleRes: Int, val isDebt: Boolean = false, val needsTwoMonths: Boolean = false, val needsPeriod: Boolean = true) {
    TOTAL_PERIOD(R.string.report_total_period),
    PER_ITEM(R.string.report_per_item),
    COMPARE_MONTHS(R.string.report_compare_months, needsTwoMonths = true, needsPeriod = false),
    INCREASE_DECREASE(R.string.report_increase_decrease),
    DISTRIBUTION(R.string.report_distribution),
    FOREIGN_CURRENCIES(R.string.report_foreign),
    FARM_FACTORY(R.string.report_farm_factory),
    TRANSFERS(R.string.report_transfers),
    TOP_GROWTH(R.string.report_top_growth),
    TOP_DECLINE(R.string.report_top_decline),
    OWED_TO_ME(R.string.report_owed_to_me, isDebt = true, needsPeriod = false),
    MY_OBLIGATIONS(R.string.report_obligations, isDebt = true, needsPeriod = false),
    OVERDUE_DEBTS(R.string.report_overdue, isDebt = true, needsPeriod = false),
    PAYMENTS_LOG(R.string.report_payments_log, isDebt = true, needsPeriod = false),
    ALL_DATA(R.string.report_all_data, needsPeriod = false),
}

enum class ExportFormat(val ext: String, val mime: String) {
    PDF("pdf", "application/pdf"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("csv", "text/csv"),
}

data class ReportParams(
    val type: ReportType,
    val from: MonthKey? = null,
    val to: MonthKey? = null,
    val monthA: MonthKey? = null,
    val monthB: MonthKey? = null,
)

data class ReportCell(val text: String, val number: BigDecimal? = null) {
    companion object {
        fun t(s: String) = ReportCell(s)
    }
}

data class ReportTable(val title: String, val headers: List<String>, val rows: List<List<ReportCell>>)

data class ReportDocument(
    val title: String,
    val subtitle: String,
    val generatedAt: String,
    val summary: List<Pair<String, String>>,
    val tables: List<ReportTable>,
) {
    val isEmpty: Boolean get() = tables.all { it.rows.isEmpty() } && summary.isEmpty()
}
