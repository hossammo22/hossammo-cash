package com.hossammo.cash.data

import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.metal.MetalRefreshResult
import com.hossammo.cash.domain.model.MonthKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class MetalsTest {
    private lateinit var env: TestEnv
    private val oct = MonthKey.of(2026, 10)

    @Before fun setUp() = runBlocking { env = TestEnv(); env.seed.seedIfNeeded() }
    @After fun tearDown() = env.close()

    @Test
    fun manual21kPriceValuesSilverAndGoldPound() = runBlocking {
        // سعر جرام 21 = 4200 => جرام 24 = 4800 ؛ فضة 999 = 59.94 => خالص 60
        env.metals.setManual(BigDecimal("4200"), BigDecimal("59.94"))
        val p = env.metals.current()
        assertEquals(0, BigDecimal("4800").compareTo(p.goldPurePerGram))
        val silver = env.db.itemDao().bySystemKey("silver_bar_250")!!
        // 250 × 0.999 × 60 = 14985
        assertEquals(BigDecimal("14985.00"), env.metals.valueOf(silver, p))
        // جنيه ذهب 8 جرام عيار 21 = 33600
        val pound = silver.copy(metalType = "GOLD", metalForm = "GOLD_POUND", weightGrams = BigDecimal("8"), purity = 21, pieceCount = 2)
        assertEquals(BigDecimal("67200.00"), env.metals.valueOf(pound, p))
    }

    @Test
    fun applyToMonthRecordsValuesAndOfflineKeepsOldPrices() = runBlocking {
        assertNull(env.metals.valueOf(env.db.itemDao().bySystemKey("silver_bar_250")!!, env.metals.current()))
        assertEquals(MetalRefreshResult.Ok, env.metals.refresh())
        val before = env.metals.current()
        env.metalFetcher.offline = true
        assertEquals(MetalRefreshResult.Offline, env.metals.refresh())
        assertEquals(before.silverPurePerGram, env.metals.current().silverPurePerGram)
        assertEquals(1, env.metals.applyToMonth(oct))
        val a = LiquidityAnalyzer(env.liquidity.observeData().first())
        val expected = env.metals.valueOf(env.db.itemDao().bySystemKey("silver_bar_250")!!, before)!!
        assertEquals(0, expected.compareTo(a.total(oct)))
    }

    @Test
    fun parsesEgyptianMarketFeedFromDaleelak() {
        val body = """{"schemaVersion":1,"assets":[
          {"slug":"gold-24k","unit":"EGP/g","directions":{"sell":{"best":{"value":7188}},"buy":{"best":{"value":7211}}},"global":{"value":7248.66}},
          {"slug":"gold-21k","unit":"EGP/g","directions":{"sell":{"best":{"seller":"X","value":6300}},"buy":{"best":{"value":6309}}},"global":{"value":6342.58}},
          {"slug":"silver-999","unit":"EGP/g","directions":{"sell":{"best":{"value":99.9}},"buy":{"best":{"value":102}}},"global":{"value":101.08}}
        ]}"""
        val f = com.hossammo.cash.data.metal.MetalPriceService.parseDaleelak(body)
        assertEquals("daleelak", f.source)
        // 6300 لجرام 21 => 7200 لجرام 24
        assertEquals(0, BigDecimal("7200").compareTo(f.goldPureGram.setScale(2, java.math.RoundingMode.HALF_UP)))
        // 99.9 لجرام 999 => 100 للخالص
        assertEquals(0, BigDecimal("100").compareTo(f.silverPureGram.setScale(2, java.math.RoundingMode.HALF_UP)))
    }

    @Test
    fun parserFallsBackToBuyThenGlobalAndRejectsMissingSilver() {
        val onlyGlobal = """{"assets":[{"slug":"gold-21k","directions":{},"global":{"value":6300}},{"slug":"silver-999","directions":{"buy":{"best":{"value":99.9}}}}]}"""
        val f = com.hossammo.cash.data.metal.MetalPriceService.parseDaleelak(onlyGlobal)
        assertEquals(0, BigDecimal("7200").compareTo(f.goldPureGram.setScale(2, java.math.RoundingMode.HALF_UP)))
        val noSilver = """{"assets":[{"slug":"gold-21k","directions":{"sell":{"best":{"value":6300}}}}]}"""
        val r = runCatching { com.hossammo.cash.data.metal.MetalPriceService.parseDaleelak(noSilver) }
        assertEquals(true, r.isFailure)
    }

    @Test
    fun priceChangeUpdatesCurrentMonthAutomatically() = runBlocking {
        env.liquidity.createMonth(oct, copyPrevious = false)
        val today = java.time.LocalDate.of(2026, 10, 21)
        env.metals.refresh()
        assertEquals(1, env.metals.syncCurrentMonth(today))
        val silverId = env.db.itemDao().bySystemKey("silver_bar_250")!!.id
        suspend fun silverValue() = LiquidityAnalyzer(env.liquidity.observeData().first()).itemRows(oct).first { it.item.id == silverId }.valueBase
        assertEquals(0, BigDecimal("14985").compareTo(silverValue()))
        // ارتفع سعر الفضة: 60 -> 70
        env.metalFetcher.silverPure = BigDecimal("70")
        env.metals.refresh()
        env.metals.syncCurrentMonth(today)
        assertEquals(0, BigDecimal("17482.5").compareTo(silverValue()))
        // شهر غير موجود: لا يُنشأ تلقائيًا
        assertEquals(0, env.metals.syncCurrentMonth(java.time.LocalDate.of(2027, 1, 1)))
    }
}
