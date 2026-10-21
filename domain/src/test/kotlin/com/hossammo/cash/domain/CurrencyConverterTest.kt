package com.hossammo.cash.domain

import com.hossammo.cash.domain.calc.CurrencyConverter
import com.hossammo.cash.domain.calc.CurrentRate
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.domain.model.RateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CurrencyConverterTest {
    private fun bd(v: String) = BigDecimal(v)

    @Test
    fun base_currency_value_is_unchanged() {
        assertEquals(bd("200000.00"), CurrencyConverter.toBase(bd("200000"), "EGP", "EGP", null))
        assertEquals(RateSource.BASE, CurrencyConverter.resolveRate("EGP", "EGP", false, null, null)!!.source)
    }

    @Test
    fun foreign_currency_converts_with_rate() {
        assertEquals(bd("51000.00"), CurrencyConverter.toBase(bd("1000"), "USD", "EGP", bd("51")))
        assertEquals(bd("13.60"), CurrencyConverter.toBase(bd("1"), "SAR", "EGP", bd("13.6")))
    }

    @Test
    fun missing_rate_returns_null_not_zero() {
        assertNull(CurrencyConverter.toBase(bd("1000"), "USD", "EGP", null))
        assertNull(CurrencyConverter.resolveRate("USD", "EGP", false, null, null))
    }

    @Test
    fun locked_rate_wins_over_current_rate() {
        val r = CurrencyConverter.resolveRate("USD", "EGP", true, bd("51"), CurrentRate(bd("55"), false, true))!!
        assertEquals(RateSource.LOCKED, r.source)
        assertEquals(bd("51.000000"), r.rate)
        // تحديث السعر الحالي لا يغير قيمة الشهر المثبت
        val value = CurrencyConverter.toBase(bd("1000"), "USD", "EGP", r.rate)
        assertEquals(bd("51000.00"), value)
    }

    @Test
    fun unlocked_uses_current_rate() {
        val r = CurrencyConverter.resolveRate("USD", "EGP", false, bd("51"), CurrentRate(bd("55"), false, true))!!
        assertEquals(RateSource.AUTO, r.source)
        assertEquals(bd("55000.00"), CurrencyConverter.toBase(bd("1000"), "USD", "EGP", r.rate))
    }

    @Test
    fun manual_rate_is_marked_manual() {
        val r = CurrencyConverter.resolveRate("KWD", "EGP", false, null, CurrentRate(bd("160.5"), true, false))!!
        assertEquals(RateSource.MANUAL, r.source)
        assertEquals(bd("1605.00"), CurrencyConverter.toBase(bd("10"), "KWD", "EGP", r.rate))
    }

    @Test
    fun offline_saved_rate_is_marked_saved() {
        val r = CurrencyConverter.resolveRate("EUR", "EGP", false, null, CurrentRate(bd("56"), false, false))!!
        assertEquals(RateSource.SAVED, r.source)
    }

    @Test
    fun cross_currency_conversion() {
        // 100 USD (51) -> SAR (13.6)
        assertEquals(bd("375.00"), CurrencyConverter.convert(bd("100"), bd("51"), bd("13.6")))
    }

    @Test
    fun parses_arabic_digits_and_thousands_separators() {
        assertEquals(bd("200000"), Money.parseOrNull("٢٠٠٬٠٠٠"))
        assertEquals(bd("1234.5"), Money.parseOrNull("1,234.5"))
        assertEquals(bd("12.75"), Money.parseOrNull("١٢٫٧٥"))
        assertNull(Money.parseOrNull(""))
        assertNull(Money.parseOrNull("abc"))
    }
}
