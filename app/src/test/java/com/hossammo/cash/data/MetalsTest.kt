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
}
