package com.hossammo.cash.data

import com.hossammo.cash.data.analysis.LiquidityAnalyzer
import com.hossammo.cash.data.backup.BackupInspectResult
import com.hossammo.cash.data.model.ConfirmReason
import com.hossammo.cash.data.model.OpError
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.data.repo.BalanceDraft
import com.hossammo.cash.data.repo.DebtDraft
import com.hossammo.cash.data.repo.ItemDraft
import com.hossammo.cash.data.repo.PaymentDraft
import com.hossammo.cash.data.repo.RefreshResult
import com.hossammo.cash.data.repo.TransferDraft
import com.hossammo.cash.domain.calc.DebtCalculator
import com.hossammo.cash.domain.model.DebtDirection
import com.hossammo.cash.domain.model.DebtStatus
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.domain.model.RateSource
import com.hossammo.cash.ui.screens.debts.toSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** سيناريوهات المستخدم: إضافة شهر، نسخ الشهر السابق، تعديل عنصر، تحويل، دفعة دين، تحديث سعر، عدم وجود إنترنت، نسخ احتياطي واستعادة، حذف واستعادة. */
@RunWith(RobolectricTestRunner::class)
class RepositoryScenariosTest {
    private lateinit var env: TestEnv
    private val sep = MonthKey.of(2026, 9)
    private val oct = MonthKey.of(2026, 10)

    @Before fun setUp() = runBlocking { env = TestEnv(); env.seed.seedIfNeeded() }
    @After fun tearDown() = env.close()

    private suspend fun id(key: String) = env.db.itemDao().bySystemKey(key)!!.id
    private suspend fun analyzer() = LiquidityAnalyzer(env.liquidity.observeData().first())

    @Test
    fun seedIsIdempotentAndHasNoAmounts() = runBlocking {
        val before = env.db.itemDao().all().size
        env.seed.seedIfNeeded()
        assertEquals(before, env.db.itemDao().all().size)
        assertEquals(10, before)
        assertEquals(12, env.db.debtDao().all().size)
        assertEquals(9, env.db.debtDao().all().count { it.direction == DebtDirection.OWED_TO_ME })
        assertTrue(env.db.balanceDao().all().isEmpty())
        assertTrue(env.db.paymentDao().all().isEmpty())
    }

    @Test
    fun addMonthAndCopyPrevious() = runBlocking {
        assertEquals(OpResult.Success, env.liquidity.createMonth(sep, copyPrevious = false))
        env.liquidity.saveBalance(BalanceDraft(sep, id("bank"), bd("1000")), true)
        env.liquidity.saveBalance(BalanceDraft(sep, id("cash"), bd("500")), true)
        assertEquals(OpResult.Failure(OpError.MONTH_EXISTS), env.liquidity.createMonth(sep, false))
        assertEquals(OpResult.Success, env.liquidity.createMonth(oct, copyPrevious = true))
        val a = analyzer()
        assertEquals(0, bd("1500").compareTo(a.total(oct)))
        assertEquals(2, a.balancesFor(oct).size)
    }

    @Test
    fun duplicateBalanceNeedsConfirmation() = runBlocking {
        env.liquidity.saveBalance(BalanceDraft(sep, id("bank"), bd("1000")), false)
        val r = env.liquidity.saveBalance(BalanceDraft(sep, id("bank"), bd("2000")), false)
        assertEquals(OpResult.NeedsConfirmation(ConfirmReason.DUPLICATE_BALANCE), r)
        assertEquals(OpResult.Success, env.liquidity.saveBalance(BalanceDraft(sep, id("bank"), bd("2000")), true))
        assertEquals(0, bd("2000").compareTo(analyzer().total(sep)))
        assertEquals(OpResult.Failure(OpError.INVALID_AMOUNT), env.liquidity.saveBalance(BalanceDraft(sep, id("bank"), bd("-1")), true))
    }

    @Test
    fun editItemExcludeFromTotal() = runBlocking {
        env.liquidity.saveBalance(BalanceDraft(sep, id("bank"), bd("1000")), true)
        env.liquidity.saveBalance(BalanceDraft(sep, id("gold"), bd("4000")), true)
        val gold = env.db.itemDao().bySystemKey("gold")!!
        val r = env.liquidity.saveItem(
            ItemDraft(gold.id, "ذهب معدل", gold.categoryId, null, gold.assetType, gold.currencyCode, gold.colorHex, gold.icon, gold.liquidityLevel, includeInTotal = false, isVariable = false, notes = null)
        )
        assertTrue(r.isSuccess)
        assertEquals("ذهب معدل", env.db.itemDao().byId(gold.id)!!.name)
        assertEquals(0, bd("1000").compareTo(analyzer().total(sep)))
    }

    @Test
    fun foreignBalanceWithoutRateFailsAndTransaction() = runBlocking {
        val r = env.liquidity.saveBalance(BalanceDraft(sep, id("usd"), bd("100")), true)
        assertEquals(OpResult.Failure(OpError.MISSING_RATE), r)
        assertTrue(env.db.balanceDao().all().isEmpty())
    }

    @Test
    fun transferRules() = runBlocking {
        env.liquidity.saveBalance(BalanceDraft(oct, id("bank"), bd("1000")), true)
        val date = LocalDate.of(2026, 10, 3)
        assertEquals(OpResult.Failure(OpError.SAME_ITEM), env.liquidity.addTransfer(TransferDraft(id("bank"), id("bank"), bd("10"), date = date), false))
        assertEquals(OpResult.NeedsConfirmation(ConfirmReason.EXCEEDS_BALANCE), env.liquidity.addTransfer(TransferDraft(id("bank"), id("cash"), bd("5000"), date = date), false))
        assertTrue(env.db.transferDao().all().isEmpty())
        // رسوم 10 تُخصم من الإجمالي حسب الإعداد الافتراضي
        assertEquals(OpResult.Success, env.liquidity.addTransfer(TransferDraft(id("bank"), id("cash"), bd("100"), fee = bd("10"), date = date), false))
        assertEquals(0, bd("990").compareTo(analyzer().total(oct)))
    }

    @Test
    fun debtPaymentsAndOverpay() = runBlocking {
        env.debts.saveDebt(DebtDraft(null, "اختبار", DebtDirection.OWED_TO_ME, bd("1000"), null, LocalDate.now().plusDays(10), null))
        val debt = env.db.debtDao().all().first { it.name == "اختبار" }
        assertEquals(OpResult.Success, env.debts.savePayment(PaymentDraft(null, debt.id, bd("400"), LocalDate.now(), null, null, null), false))
        assertEquals(OpResult.Success, env.debts.savePayment(PaymentDraft(null, debt.id, bd("500"), LocalDate.now(), "تحويل", "R1", null), false))
        val over = PaymentDraft(null, debt.id, bd("200"), LocalDate.now(), null, null, null)
        assertEquals(OpResult.NeedsConfirmation(ConfirmReason.PAYMENT_EXCEEDS_ORIGINAL), env.debts.savePayment(over, false))
        var d = env.debts.observeDebts().first().first { it.debt.id == debt.id }
        assertEquals(0, bd("100").compareTo(DebtCalculator.remaining(d.debt.originalAmount, d.payments.map { it.amount })))
        assertEquals(OpResult.Success, env.debts.savePayment(over, true))
        d = env.debts.observeDebts().first().first { it.debt.id == debt.id }
        val fig = DebtCalculator.figures(d.toSnapshot(), LocalDate.now())
        assertEquals(DebtStatus.COMPLETED, fig.status)
        assertTrue(fig.overpaid)
        // الديون لا تؤثر على السيولة
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(analyzer().total(oct)))
    }

    @Test
    fun rateRefreshAndOffline() = runBlocking {
        env.fetcher.rates = mapOf("USD" to bd("50"), "SAR" to bd("13.3"), "KWD" to bd("162"), "EUR" to bd("54"))
        assertTrue(env.currency.refresh() is RefreshResult.Success)
        env.liquidity.saveBalance(BalanceDraft(oct, id("usd"), bd("10")), true)
        var b = env.db.balanceDao().all().single()
        assertEquals(RateSource.AUTO, b.rateSource)
        assertEquals(0, bd("500").compareTo(b.valueBase))
        // تحديث السعر يعيد حساب الأرصدة غير المثبتة
        env.fetcher.rates = mapOf("USD" to bd("52"))
        env.currency.refresh()
        b = env.db.balanceDao().all().single()
        assertEquals(0, bd("520").compareTo(b.valueBase))
        // بدون إنترنت: يفشل التحديث ويبقى السعر المحفوظ
        env.fetcher.offline = true
        val r = env.currency.refresh()
        assertTrue(r is RefreshResult.Failed)
        assertEquals(0, bd("52").compareTo(env.db.rateDao().byCode("USD")!!.rateToBase))
        assertEquals(0, bd("520").compareTo(env.db.balanceDao().all().single().valueBase))
        assertFalse(env.prefs.current().lastRateRefreshOk)
    }

    @Test
    fun backupAndRestore() = runBlocking {
        env.liquidity.saveBalance(BalanceDraft(sep, id("bank"), bd("1234.5")), true)
        val payload = env.backup.buildPayload()
        val bytes = env.backup.encode(payload, "secret-pass".toCharArray())
        assertEquals(BackupInspectResult.WrongPasswordOrCorrupt, env.backup.inspect(bytes, "wrong-pass".toCharArray()))
        assertEquals(BackupInspectResult.NotABackup, env.backup.inspect("hello".toByteArray(), "x".toCharArray()))
        val corrupted = bytes.copyOf().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }
        assertEquals(BackupInspectResult.WrongPasswordOrCorrupt, env.backup.inspect(corrupted, "secret-pass".toCharArray()))
        val valid = env.backup.inspect(bytes, "secret-pass".toCharArray())
        assertTrue(valid is BackupInspectResult.Valid)

        // تغيير البيانات بعد النسخ ثم الاستعادة
        env.liquidity.saveBalance(BalanceDraft(sep, id("bank"), bd("1")), true)
        env.liquidity.saveBalance(BalanceDraft(sep, id("cash"), bd("99")), true)
        val result = env.backup.restore((valid as BackupInspectResult.Valid).payload, "secret-pass".toCharArray())
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().exists())
        val a = analyzer()
        assertEquals(0, bd("1234.5").compareTo(a.total(sep)))
        assertEquals(1, a.balancesFor(sep).size)
    }

    @Test
    fun deleteAndRestore() = runBlocking {
        env.liquidity.saveBalance(BalanceDraft(sep, id("bank"), bd("1000")), true)
        val bank = id("bank")
        env.liquidity.softDeleteItem(bank)
        assertNull(env.liquidity.observeData().first().itemById[bank])
        assertEquals(1, env.liquidity.observeDeletedItems().first().size)
        env.liquidity.restoreItem(bank)
        assertNotNull(env.liquidity.observeData().first().itemById[bank])
        assertEquals(0, bd("1000").compareTo(analyzer().total(sep)))

        env.liquidity.softDeleteMonth(sep)
        assertTrue(analyzer().monthKeys.isEmpty())
        assertEquals(OpResult.NeedsConfirmation(ConfirmReason.MONTH_DELETED_EXISTS), env.liquidity.createMonth(sep, false))
        env.liquidity.createMonth(sep, false, restoreIfDeleted = true)
        assertEquals(0, bd("1000").compareTo(analyzer().total(sep)))
    }
}
