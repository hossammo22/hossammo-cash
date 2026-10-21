package com.hossammo.cash.data.repo

import com.hossammo.cash.data.currency.CurrencyFetchException
import com.hossammo.cash.data.currency.RateFetcher
import com.hossammo.cash.data.db.AppDatabase
import com.hossammo.cash.data.db.entity.CurrencyRateEntity
import com.hossammo.cash.data.settings.UserPreferencesRepository
import com.hossammo.cash.domain.model.Money
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RefreshResult {
    data class Success(val updated: Int) : RefreshResult
    /** فشل التحديث؛ الأسعار القديمة محفوظة كما هي. */
    data class Failed(val kind: Kind) : RefreshResult
    enum class Kind { NETWORK, SERVER, PARSE }
}

@Singleton
class CurrencyRepository @Inject constructor(
    private val db: AppDatabase,
    private val service: RateFetcher,
    private val prefs: UserPreferencesRepository,
    private val appSettings: AppSettingsStore,
    private val liquidity: LiquidityRepository,
    private val audit: AuditLogger,
) {
    private val rateDao = db.rateDao()

    fun observeRates(): Flow<List<CurrencyRateEntity>> = rateDao.observeAll()

    /** العملات المطلوب تتبعها: الافتراضية + عملات العناصر النشطة. */
    suspend fun trackedCurrencies(): Set<String> {
        val base = appSettings.baseCurrency()
        val fromItems = db.itemDao().all().filter { it.deletedAt == null }.map { it.currencyCode.uppercase() }
        val fromRates = rateDao.all().map { it.currencyCode }
        return (DEFAULT_CURRENCIES + fromItems + fromRates).filter { it != base }.toSortedSet()
    }

    /** تحديث الأسعار الآن. عند الفشل لا يُحذف أي سعر قديم. */
    suspend fun refresh(): RefreshResult {
        val p = prefs.current()
        val base = appSettings.baseCurrency()
        return try {
            val fetched = service.fetch(p.rateProvider, base, trackedCurrencies())
            val t = System.currentTimeMillis()
            for ((code, rate) in fetched.rates) {
                val existing = rateDao.byCode(code)
                // لا نستبدل سعرًا يدويًا أدخله المستخدم إلا إذا كان أقدم من السعر الجديد ويُعرض الجديد كتلقائي
                rateDao.upsert(
                    CurrencyRateEntity(code, Money.rate(rate), base, fetched.providerId.id, false, t, t)
                )
                if (existing == null || existing.rateToBase.compareTo(rate) != 0 || existing.isManual) {
                    liquidity.recalcUnlocked(code)
                }
            }
            prefs.recordRateRefresh(true, t, null)
            audit.log(AuditLogger.Type.RATE, "ALL", AuditLogger.Action.REFRESH, fetched.providerId.id)
            RefreshResult.Success(fetched.rates.size)
        } catch (e: CurrencyFetchException) {
            val kind = when (e) {
                is CurrencyFetchException.Network -> RefreshResult.Kind.NETWORK
                is CurrencyFetchException.Http -> RefreshResult.Kind.SERVER
                is CurrencyFetchException.Parse -> RefreshResult.Kind.PARSE
            }
            prefs.recordRateRefresh(false, System.currentTimeMillis(), kind.name)
            RefreshResult.Failed(kind)
        }
    }

    /** إدخال سعر يدوي لعملة. */
    suspend fun setManualRate(code: String, rate: BigDecimal): Boolean {
        if (rate.signum() <= 0) return false
        val base = appSettings.baseCurrency()
        val t = System.currentTimeMillis()
        rateDao.upsert(CurrencyRateEntity(code.uppercase(), Money.rate(rate), base, "manual", true, t, t))
        liquidity.recalcUnlocked(code.uppercase())
        audit.log(AuditLogger.Type.RATE, code, AuditLogger.Action.UPDATE, "manual")
        return true
    }

    /** عند تغيير العملة الأساسية (مسموح فقط قبل وجود أرصدة) تُمسح الأسعار القديمة لأنها منسوبة لعملة أخرى. */
    suspend fun changeBaseCurrency(code: String) {
        db.maintenanceDao().clearRates()
        appSettings.setBaseCurrency(code)
        audit.log(AuditLogger.Type.RATE, code.uppercase(), AuditLogger.Action.UPDATE, "base")
    }

    /** هل حان وقت التحديث التلقائي حسب إعداد المستخدم؟ */
    suspend fun isAutoRefreshDue(now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs.current()
        if (p.autoRefreshHours <= 0) return false
        return now - p.lastRateRefreshAt >= p.autoRefreshHours * 3_600_000L
    }

    companion object {
        val DEFAULT_CURRENCIES = setOf("USD", "SAR", "KWD", "EUR")
    }
}
