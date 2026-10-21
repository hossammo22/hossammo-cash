package com.hossammo.cash.util

import android.content.Context
import com.hossammo.cash.R
import com.hossammo.cash.data.settings.NumberStyle
import com.hossammo.cash.domain.model.AssetType
import com.hossammo.cash.domain.model.DebtStatus
import com.hossammo.cash.domain.model.LiquidityLevel
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.domain.model.RateSource
import com.hossammo.cash.domain.model.TrendStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * تنسيق الأرقام والتواريخ مع دعم الأرقام العربية أو الإنجليزية وإخفاء القيم.
 */
class AppFormatter(
    private val context: Context,
    val numberStyle: NumberStyle = NumberStyle.LATIN,
    val hideValues: Boolean = false,
) {
    private val locale: Locale = if (numberStyle == NumberStyle.ARABIC) Locale.forLanguageTag("ar-EG") else Locale.US

    private fun symbols(): DecimalFormatSymbols = DecimalFormatSymbols.getInstance(locale)

    private val moneyFormat = DecimalFormat("#,##0.##", symbols()).apply { roundingMode = RoundingMode.HALF_UP }
    private val moneyFixed = DecimalFormat("#,##0.00", symbols()).apply { roundingMode = RoundingMode.HALF_UP }
    private val rateFormat = DecimalFormat("#,##0.####", symbols()).apply { roundingMode = RoundingMode.HALF_UP }
    private val percentFormat = DecimalFormat("#,##0.##", symbols()).apply { roundingMode = RoundingMode.HALF_UP }
    private val intFormat = DecimalFormat("#,##0", symbols())

    val masked: String get() = "••••••"

    /** مبلغ مع فواصل الآلاف. */
    fun money(v: BigDecimal?, withCurrency: String? = null, ignoreHide: Boolean = false): String {
        if (v == null) return context.getString(R.string.value_empty)
        if (hideValues && !ignoreHide) return masked
        val text = moneyFormat.format(v)
        return if (withCurrency != null) "$text ${currencySymbol(withCurrency)}" else text
    }

    fun moneyExact(v: BigDecimal?): String = if (v == null) "" else moneyFixed.format(v)

    /** مبلغ بإشارة + أو − للتغيرات. */
    fun signedMoney(v: BigDecimal?, withCurrency: String? = null): String {
        if (v == null) return context.getString(R.string.not_available)
        if (hideValues) return masked
        val sign = when (v.signum()) { 1 -> "+"; -1 -> "−"; else -> "" }
        val text = moneyFormat.format(v.abs())
        return "$sign$text" + (withCurrency?.let { " ${currencySymbol(it)}" } ?: "")
    }

    fun percent(v: BigDecimal?, signed: Boolean = true): String {
        if (v == null) return context.getString(R.string.not_available)
        val sign = if (!signed) "" else when (v.signum()) { 1 -> "+"; -1 -> "−"; else -> "" }
        return "$sign${percentFormat.format(v.abs())}%"
    }

    fun rate(v: BigDecimal?): String = if (v == null) context.getString(R.string.value_empty) else rateFormat.format(v)

    fun integer(v: Int): String = intFormat.format(v.toLong())

    /** رقم عشري عام (مثل الأوزان بالجرام) حتى 4 منازل. */
    fun decimal(v: BigDecimal?): String = when {
        v == null -> context.getString(R.string.value_empty)
        hideValues -> masked
        else -> rateFormat.format(v)
    }

    fun currencySymbol(code: String): String = when (code.uppercase()) {
        "EGP" -> context.getString(R.string.currency_egp_short)
        else -> code.uppercase()
    }

    fun monthName(month: Int): String = context.resources.getStringArray(R.array.month_names)[month - 1]

    fun month(key: MonthKey): String = "${monthName(key.month)} ${yearText(key.year)}"

    fun monthShort(key: MonthKey): String = "${monthName(key.month).take(3)} ${(key.year % 100).toString().padStart(2, '0').localizeDigits()}"

    private fun yearText(y: Int): String = y.toString().localizeDigits()

    fun date(d: LocalDate?): String {
        if (d == null) return context.getString(R.string.value_empty)
        return d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
    }

    fun dateFromEpochDay(day: Long?): String = date(day?.let { LocalDate.ofEpochDay(it) })

    fun dateTime(millis: Long?): String {
        if (millis == null || millis <= 0) return context.getString(R.string.never)
        val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return dt.format(DateTimeFormatter.ofPattern("d MMM yyyy - hh:mm a", locale))
    }

    fun String.localizeDigits(): String = if (numberStyle == NumberStyle.ARABIC) {
        map { c -> if (c in '0'..'9') ('٠' + (c - '0')) else c }.joinToString("")
    } else this

    fun trend(status: TrendStatus?): String = context.getString(
        when (status) {
            TrendStatus.INCREASED -> R.string.trend_increased
            TrendStatus.DECREASED -> R.string.trend_decreased
            TrendStatus.STABLE -> R.string.trend_stable
            TrendStatus.NEW, null -> R.string.trend_new
        }
    )

    fun assetType(t: AssetType): String = context.getString(Labels.assetType(t))
    fun level(l: LiquidityLevel): String = context.getString(Labels.level(l))
    fun rateSource(s: RateSource): String = context.getString(Labels.rateSource(s))
    fun debtStatus(s: DebtStatus): String = context.getString(Labels.debtStatus(s))
}

object Labels {
    fun assetType(t: AssetType) = when (t) {
        AssetType.CASH -> R.string.asset_cash
        AssetType.BANK_ACCOUNT -> R.string.asset_bank
        AssetType.GOLD -> R.string.asset_gold
        AssetType.STOCKS -> R.string.asset_stocks
        AssetType.PROJECT -> R.string.asset_project
        AssetType.FOREIGN_CURRENCY -> R.string.asset_foreign
        AssetType.LIQUIDATABLE_ASSET -> R.string.asset_liquidatable
        AssetType.CUSTOM -> R.string.asset_custom
    }

    fun level(l: LiquidityLevel) = when (l) {
        LiquidityLevel.IMMEDIATE -> R.string.level_immediate
        LiquidityLevel.WITHIN_DAYS -> R.string.level_days
        LiquidityLevel.NEEDS_LIQUIDATION -> R.string.level_liquidation
        LiquidityLevel.UNSPECIFIED -> R.string.level_unspecified
    }

    fun rateSource(s: RateSource) = when (s) {
        RateSource.BASE -> R.string.rate_src_base
        RateSource.AUTO -> R.string.rate_src_auto
        RateSource.MANUAL -> R.string.rate_src_manual
        RateSource.SAVED -> R.string.rate_src_saved
        RateSource.LOCKED -> R.string.rate_src_locked
    }

    fun debtStatus(s: DebtStatus) = when (s) {
        DebtStatus.OPEN -> R.string.debt_status_open
        DebtStatus.PARTIALLY_PAID -> R.string.debt_status_partial
        DebtStatus.COMPLETED -> R.string.debt_status_completed
        DebtStatus.OVERDUE -> R.string.debt_status_overdue
        DebtStatus.NO_DUE_DATE -> R.string.debt_status_no_due
    }
}
