package com.hossammo.cash.data

import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.data.repo.BalanceDraft
import com.hossammo.cash.data.repo.DebtDraft
import com.hossammo.cash.data.repo.TransferDraft
import com.hossammo.cash.domain.calc.FinanceCalculator
import com.hossammo.cash.domain.model.DebtDirection
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.domain.model.TrendStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * سيناريو التحقق الإلزامي على مستوى المستودعات وقاعدة البيانات الحقيقية (Room).
 * هذه الأرقام داخل الاختبار فقط وليست بيانات افتراضية للمستخدم.
 */
@RunWith(RobolectricTestRunner::class)
class MandatoryScenarioTest {
    private lateinit var env: TestEnv
    private val sep = MonthKey.of(2026, 9)
    private val oct = MonthKey.of(2026, 10)

    @Before fun setUp() { env = TestEnv() }
    @After fun tearDown() = env.close()

    private suspend fun id(key: String) = env.db.itemDao().bySystemKey(key)!!.id
    private suspend fun analyzer() = LiquidityAnalyzer(env.liquidity.observeData().first())
    private suspend fun put(month: MonthKey, key: String, amount: String, lock: Boolean = false, rate: String? = null) {
        val r = env.liquidity.saveBalance(BalanceDraft(month, id(key), bd(amount), lockRate = lock, customRate = rate?.let(::bd)), confirmOverwrite = true)
        assertEquals("save $key", OpResult.Success, r)
    }

    @Test
    fun mandatoryScenario() = runBlocking {
        env.seed.seedIfNeeded()
        // أول تشغيل: لا توجد أي قيم مالية
        assertTrue(env.db.balanceDao().all().isEmpty())
        assertTrue(env.db.debtDao().all().all { it.originalAmount == null })

        // سبتمبر 2026
        put(sep, "bank", "200000"); put(sep, "cash", "50000"); put(sep, "gold", "300000")
        put(sep, "stocks", "150000"); put(sep, "farm", "100000"); put(sep, "factory", "80000")
        put(sep, "usd", "1000", lock = true, rate = "51")
        assertEquals(0, bd("931000").compareTo(analyzer().total(sep)))

        // أكتوبر 2026: إجمالي 950,000
        put(oct, "bank", "210000"); put(oct, "cash", "50000"); put(oct, "gold", "309000")
        put(oct, "stocks", "150000"); put(oct, "farm", "100000"); put(oct, "factory", "80000")
        put(oct, "usd", "1000", lock = true, rate = "51")
        val a = analyzer()
        assertEquals(0, bd("950000").compareTo(a.total(oct)))
        val ch = FinanceCalculator.change(a.total(oct), a.total(sep))
        assertEquals(0, bd("19000").compareTo(ch.change))
        assertEquals(0, bd("2.0408").compareTo(ch.percent))
        assertEquals(TrendStatus.INCREASED, ch.status)

        // دين بمليون جنيه لا يؤثر على إجمالي السيولة
        assertEquals(OpResult.Success, env.debts.saveDebt(DebtDraft(null, "دين اختبار", DebtDirection.I_OWE, bd("1000000"), null, null, null)))
        assertEquals(0, bd("950000").compareTo(analyzer().total(oct)))

        // تحويل 20,000 من البنك إلى النقدية
        val r = env.liquidity.addTransfer(TransferDraft(id("bank"), id("cash"), bd("20000"), date = LocalDate.of(2026, 10, 15)), confirmOverdraft = false)
        assertEquals(OpResult.Success, r)
        val after = analyzer()
        val rows = after.itemRows(oct).associateBy { it.item.systemKey }
        assertEquals(0, bd("190000").compareTo(rows["bank"]!!.valueBase))
        assertEquals(0, bd("70000").compareTo(rows["cash"]!!.valueBase))
        assertEquals(0, bd("950000").compareTo(after.total(oct)))
    }

    @Test
    fun lockedRateKeepsHistoricalValueWhenRatesChange() = runBlocking {
        env.seed.seedIfNeeded()
        put(sep, "usd", "1000", lock = true, rate = "51")
        env.currency.setManualRate("USD", bd("60"))
        put(oct, "usd", "1000")
        val a = analyzer()
        assertEquals(0, bd("51000").compareTo(a.total(sep)))
        assertEquals(0, bd("60000").compareTo(a.total(oct)))
    }

    @Test
    fun deletingTransferRestoresBalances() = runBlocking {
        env.seed.seedIfNeeded()
        put(oct, "bank", "100000"); put(oct, "cash", "1000")
        env.liquidity.addTransfer(TransferDraft(id("bank"), id("cash"), bd("20000"), date = LocalDate.of(2026, 10, 2)), false)
        val t = env.db.transferDao().all().single()
        env.liquidity.softDeleteTransfer(t.id)
        val rows = analyzer().itemRows(oct).associateBy { it.item.systemKey }
        assertEquals(0, bd("100000").compareTo(rows["bank"]!!.valueBase))
        assertEquals(0, bd("1000").compareTo(rows["cash"]!!.valueBase))
    }
}
