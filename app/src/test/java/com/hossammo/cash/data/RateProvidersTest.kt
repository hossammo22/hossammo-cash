package com.hossammo.cash.data

import com.hossammo.cash.data.currency.CurrencyFetchException
import com.hossammo.cash.data.currency.FawazCurrencyApiProvider
import com.hossammo.cash.data.currency.OpenErApiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** تحليل ردود مصادر الأسعار (بدون شبكة). */
class RateProvidersTest {

    @Test
    fun openErApiInvertsRatesToBase() {
        val body = """{"result":"success","base_code":"EGP","rates":{"EGP":1,"USD":0.02,"SAR":0.075,"KWD":0.00625,"EUR":0.0185}}"""
        val r = OpenErApiProvider().parse(body, "EGP", setOf("USD", "SAR", "KWD"))
        assertEquals(0, bd("50").compareTo(r["USD"]))
        assertEquals(0, bd("13.333333").compareTo(r["SAR"]))
        assertEquals(0, bd("160").compareTo(r["KWD"]))
        assertEquals(3, r.size)
    }

    @Test
    fun fawazProviderParsesLowercaseKeys() {
        val body = """{"date":"2026-10-20","egp":{"usd":0.0196,"eur":0.018}}"""
        val r = FawazCurrencyApiProvider().parse(body, "EGP", setOf("USD", "EUR"))
        assertEquals(0, bd("51.020408").compareTo(r["USD"]))
        assertEquals(0, bd("55.555556").compareTo(r["EUR"]))
    }

    @Test(expected = CurrencyFetchException.Parse::class)
    fun errorResultThrows() {
        OpenErApiProvider().parse("""{"result":"error","error-type":"unsupported-code"}""", "EGP", setOf("USD"))
    }

    @Test
    fun urlsAreHttpsOnlyAndCarryNoFinancialData() {
        val urls = OpenErApiProvider().buildUrls("EGP") + FawazCurrencyApiProvider().buildUrls("EGP")
        assertTrue(urls.all { it.startsWith("https://") })
        assertTrue(urls.none { it.contains("?") })
    }
}
