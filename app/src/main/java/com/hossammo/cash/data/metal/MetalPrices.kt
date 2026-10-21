package com.hossammo.cash.data.metal

import androidx.room.withTransaction
import com.hossammo.cash.data.currency.CurrencyFetchException
import com.hossammo.cash.data.db.AppDatabase
import com.hossammo.cash.data.db.entity.AppSettingEntity
import com.hossammo.cash.data.db.entity.LiquidityItemEntity
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.data.repo.AppSettingsStore
import com.hossammo.cash.data.repo.AuditLogger
import com.hossammo.cash.data.repo.BalanceDraft
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.domain.calc.MetalCalculator
import com.hossammo.cash.domain.calc.MetalForm
import com.hossammo.cash.domain.calc.MetalType
import com.hossammo.cash.domain.model.MonthKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** سعر الأوقية بالعملة الأساسية لكل معدن. */
data class FetchedMetalPrices(val goldOunce: BigDecimal, val silverOunce: BigDecimal, val source: String)

/** واجهة جلب أسعار المعادن (قابلة للاستبدال في الاختبارات). الطلب لا يحمل أي بيانات مالية. */
interface MetalPriceFetcher {
    suspend fun fetch(base: String, usdRateToBase: BigDecimal?): FetchedMetalPrices
}

/** أسعار جرام المعدن الخالص الحالية. */
data class MetalPrices(
    val base: String,
    val goldPurePerGram: BigDecimal?,
    val silverPurePerGram: BigDecimal?,
    val updatedAt: Long,
    /** fawaz / gold-api / manual */
    val source: String?,
) {
    fun purePrice(type: MetalType): BigDecimal? = if (type == MetalType.GOLD) goldPurePerGram else silverPurePerGram
    fun gramAt(type: MetalType, purity: Int): BigDecimal? = purePrice(type)?.let { MetalCalculator.gramPriceAt(type, purity, it) }
}

/** تفاصيل المعدن لعنصر سيولة. */
data class MetalSpec(val type: MetalType, val form: MetalForm, val weightGrams: BigDecimal, val purity: Int, val count: Int)

fun LiquidityItemEntity.metalSpec(): MetalSpec? {
    val t = metalType?.let { runCatching { MetalType.valueOf(it) }.getOrNull() } ?: return null
    val w = weightGrams ?: return null
    return MetalSpec(
        t, metalForm?.let { runCatching { MetalForm.valueOf(it) }.getOrNull() } ?: MetalForm.OTHER,
        w, purity ?: MetalCalculator.defaultPurity(t), pieceCount ?: 1,
    )
}

/**
 * مصدر الأسعار:
 * 1) fawazahmed0 currency-api (يعطي xau/xag مقابل العملة الأساسية مباشرة) مع رابط احتياطي.
 * 2) gold-api.com (سعر لحظي بالدولار) × سعر الدولار المحفوظ في التطبيق.
 * HTTPS فقط، بدون أي مفاتيح أو بيانات مستخدم.
 */
@Singleton
class MetalPriceService @Inject constructor() : MetalPriceFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mc = MathContext(20, RoundingMode.HALF_UP)

    private fun get(url: String): String {
        require(url.startsWith("https://"))
        val req = Request.Builder().url(url).header("Accept", "application/json").get().build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw CurrencyFetchException.Http(r.code)
            return r.body?.string() ?: throw CurrencyFetchException.Parse("empty")
        }
    }

    override suspend fun fetch(base: String, usdRateToBase: BigDecimal?): FetchedMetalPrices = withContext(Dispatchers.IO) {
        var last: Exception? = null
        val b = base.lowercase()
        for (url in listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/$b.json",
            "https://latest.currency-api.pages.dev/v1/currencies/$b.json",
        )) {
            try {
                val rates = json.parseToJsonElement(get(url)).jsonObject[b]?.jsonObject ?: throw CurrencyFetchException.Parse("no rates")
                val xau = rates["xau"]?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
                val xag = rates["xag"]?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
                if (xau == null || xag == null || xau.signum() <= 0 || xag.signum() <= 0) throw CurrencyFetchException.Parse("no metals")
                return@withContext FetchedMetalPrices(BigDecimal.ONE.divide(xau, mc), BigDecimal.ONE.divide(xag, mc), SOURCE_FAWAZ)
            } catch (e: IOException) { last = e } catch (e: RuntimeException) { last = CurrencyFetchException.Parse(e.javaClass.simpleName) }
        }
        val usd = if (base.equals("USD", true)) BigDecimal.ONE else usdRateToBase
        if (usd != null) {
            try {
                fun price(sym: String) = json.parseToJsonElement(get("https://api.gold-api.com/price/$sym")).jsonObject["price"]
                    ?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull() ?: throw CurrencyFetchException.Parse("no price")
                return@withContext FetchedMetalPrices(price("XAU").multiply(usd, mc), price("XAG").multiply(usd, mc), SOURCE_GOLD_API)
            } catch (e: IOException) { last = e } catch (e: RuntimeException) { last = CurrencyFetchException.Parse(e.javaClass.simpleName) }
        }
        throw last ?: CurrencyFetchException.Network("unknown")
    }

    companion object {
        const val SOURCE_FAWAZ = "fawaz"
        const val SOURCE_GOLD_API = "gold-api"
        const val SOURCE_MANUAL = "manual"
    }
}

sealed interface MetalRefreshResult {
    data object Ok : MetalRefreshResult
    data object Offline : MetalRefreshResult
    data object Failed : MetalRefreshResult
}

/** أسعار الذهب والفضة وتقييم العناصر وتسجيلها في الأشهر. الأسعار تُحفظ في app_settings وتُنسخ احتياطيًا. */
@Singleton
class MetalRepository @Inject constructor(
    private val db: AppDatabase,
    private val fetcher: MetalPriceFetcher,
    private val appSettings: AppSettingsStore,
    private val liquidity: LiquidityRepository,
    private val audit: AuditLogger,
) {
    private val dao = db.settingDao()

    val prices: Flow<MetalPrices> = dao.observeAll().map { list ->
        val m = list.associate { it.key to it.value }
        MetalPrices(
            base = m[AppSettingsStore.KEY_BASE_CURRENCY] ?: AppSettingsStore.DEFAULT_BASE,
            goldPurePerGram = m[K_GOLD]?.toBigDecimalOrNull(),
            silverPurePerGram = m[K_SILVER]?.toBigDecimalOrNull(),
            updatedAt = m[K_AT]?.toLongOrNull() ?: 0,
            source = m[K_SRC],
        )
    }

    private suspend fun put(key: String, value: String) = dao.upsert(AppSettingEntity(key, value, System.currentTimeMillis()))

    suspend fun current(): MetalPrices {
        return MetalPrices(
            appSettings.baseCurrency(), dao.get(K_GOLD)?.toBigDecimalOrNull(), dao.get(K_SILVER)?.toBigDecimalOrNull(),
            dao.get(K_AT)?.toLongOrNull() ?: 0, dao.get(K_SRC),
        )
    }

    /** تحديث الأسعار من الإنترنت. عند الفشل تبقى الأسعار القديمة كما هي. */
    suspend fun refresh(): MetalRefreshResult {
        val base = appSettings.baseCurrency()
        val usd = db.rateDao().byCode("USD")?.rateToBase
        return try {
            val f = fetcher.fetch(base, usd)
            val gold = MetalCalculator.gramPriceFromOunce(f.goldOunce).setScale(4, RoundingMode.HALF_UP)
            val silver = MetalCalculator.gramPriceFromOunce(f.silverOunce).setScale(4, RoundingMode.HALF_UP)
            savePrices(gold, silver, f.source)
            MetalRefreshResult.Ok
        } catch (e: CurrencyFetchException.Network) {
            MetalRefreshResult.Offline
        } catch (e: IOException) {
            MetalRefreshResult.Offline
        } catch (e: Exception) {
            MetalRefreshResult.Failed
        }
    }

    /** إدخال يدوي: سعر جرام الذهب عيار 21 وسعر جرام الفضة 999 (كما يُسعَّر في السوق المصري). */
    suspend fun setManual(gold21PerGram: BigDecimal?, silver999PerGram: BigDecimal?) {
        val cur = current()
        val gold = gold21PerGram?.let { MetalCalculator.purePriceFrom(MetalType.GOLD, 21, it).setScale(4, RoundingMode.HALF_UP) } ?: cur.goldPurePerGram
        val silver = silver999PerGram?.let { MetalCalculator.purePriceFrom(MetalType.SILVER, 999, it).setScale(4, RoundingMode.HALF_UP) } ?: cur.silverPurePerGram
        savePrices(gold, silver, MetalPriceService.SOURCE_MANUAL)
    }

    private suspend fun savePrices(gold: BigDecimal?, silver: BigDecimal?, source: String) {
        db.withTransaction {
            gold?.let { put(K_GOLD, it.toPlainString()) }
            silver?.let { put(K_SILVER, it.toPlainString()) }
            put(K_AT, System.currentTimeMillis().toString())
            put(K_SRC, source)
        }
        audit.log(AuditLogger.Type.RATE, "METALS", AuditLogger.Action.REFRESH, source)
    }

    /** القيمة الحالية لعنصر معدني (null إن لم تتوفر تفاصيل أو سعر). */
    fun valueOf(item: LiquidityItemEntity, prices: MetalPrices): BigDecimal? {
        val spec = item.metalSpec() ?: return null
        val p = prices.purePrice(spec.type) ?: return null
        return MetalCalculator.value(spec.type, spec.weightGrams, spec.purity, spec.count, p)
    }

    /**
     * تسجيل القيم الحالية لكل عناصر الذهب والفضة في شهر محدد (ينشئ الشهر إن لم يوجد).
     * تُستبدل القيمة السابقة للعنصر في هذا الشهر. يعيد عدد العناصر المسجلة.
     */
    suspend fun applyToMonth(month: MonthKey): Int {
        val prices = current()
        val items = db.itemDao().activeNotArchived().filter { it.metalSpec() != null }
        var n = 0
        for (item in items) {
            val v = valueOf(item, prices) ?: continue
            val r = liquidity.saveBalance(BalanceDraft(month, item.id, v), confirmOverwrite = true, isEdit = true)
            if (r is OpResult.Success) n++
        }
        return n
    }

    companion object {
        const val K_GOLD = "metal_gold_pure_gram"
        const val K_SILVER = "metal_silver_pure_gram"
        const val K_AT = "metal_prices_at"
        const val K_SRC = "metal_prices_src"
    }
}
