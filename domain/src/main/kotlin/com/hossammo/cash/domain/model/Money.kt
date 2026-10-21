package com.hossammo.cash.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * قواعد التقريب الموحدة للتطبيق.
 * - المبالغ المالية: منزلتان عشريتان، HALF_UP.
 * - أسعار الصرف: 6 منازل عشرية، HALF_UP.
 * - النسب المئوية: 4 منازل عشرية، HALF_UP.
 */
object Money {
    const val MONEY_SCALE = 2
    const val RATE_SCALE = 6
    const val PERCENT_SCALE = 4
    val ROUNDING: RoundingMode = RoundingMode.HALF_UP
    val HUNDRED: BigDecimal = BigDecimal(100)

    fun money(v: BigDecimal): BigDecimal = v.setScale(MONEY_SCALE, ROUNDING)
    fun rate(v: BigDecimal): BigDecimal = v.setScale(RATE_SCALE, ROUNDING)
    fun percent(v: BigDecimal): BigDecimal = v.setScale(PERCENT_SCALE, ROUNDING)

    /** يحوّل نصًا أدخله المستخدم إلى BigDecimal بأمان (يدعم الأرقام العربية والفواصل). */
    fun parseOrNull(input: String?): BigDecimal? {
        if (input.isNullOrBlank()) return null
        val normalized = buildString {
            for (c in input.trim()) {
                when (c) {
                    in '٠'..'٩' -> append('0' + (c - '٠'))
                    in '۰'..'۹' -> append('0' + (c - '۰'))
                    '٫' -> append('.') // فاصلة عشرية عربية
                    ',', '٬', ' ', ' ' -> Unit // فواصل الآلاف
                    else -> append(c)
                }
            }
        }
        return normalized.toBigDecimalOrNull()
    }

    fun isZero(v: BigDecimal): Boolean = v.signum() == 0
}

fun Iterable<BigDecimal>.sumMoney(): BigDecimal = fold(BigDecimal.ZERO) { acc, v -> acc + v }
