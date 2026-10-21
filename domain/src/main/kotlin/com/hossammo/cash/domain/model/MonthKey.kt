package com.hossammo.cash.domain.model

/**
 * مفتاح الشهر بصيغة رقمية yyyyMM (مثال: 202609) لتسهيل الترتيب والفهرسة.
 */
@JvmInline
value class MonthKey(val value: Int) : Comparable<MonthKey> {
    init {
        require(month in 1..12) { "Invalid month in key $value" }
    }

    val year: Int get() = value / 100
    val month: Int get() = value % 100

    fun previous(): MonthKey = if (month == 1) of(year - 1, 12) else of(year, month - 1)
    fun next(): MonthKey = if (month == 12) of(year + 1, 1) else of(year, month + 1)

    /** عدد الأشهر من هذا الشهر حتى [other] (موجب إذا كان other بعده). */
    fun monthsUntil(other: MonthKey): Int = (other.year - year) * 12 + (other.month - month)

    fun minusMonths(n: Int): MonthKey {
        var k = this
        repeat(n) { k = k.previous() }
        return k
    }

    override fun compareTo(other: MonthKey): Int = value.compareTo(other.value)
    override fun toString(): String = "%04d-%02d".format(year, month)

    companion object {
        fun of(year: Int, month: Int): MonthKey = MonthKey(year * 100 + month)
    }
}
