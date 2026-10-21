package com.hossammo.cash.data.currency

import com.hossammo.cash.data.settings.RateProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** أسعار عملات مقابل العملة الأساسية: rates[X] = كم وحدة من العملة الأساسية تساوي 1 من X. */
data class FetchedRates(
    val base: String,
    val rates: Map<String, BigDecimal>,
    val providerId: RateProviderId,
    val fetchedAt: Long,
)

sealed class CurrencyFetchException(message: String) : IOException(message) {
    class Network(message: String) : CurrencyFetchException(message)
    class Http(val code: Int) : CurrencyFetchException("HTTP $code")
    class Parse(message: String) : CurrencyFetchException(message)
}

/** واجهة جلب الأسعار (تسمح باستبدالها في الاختبارات). */
interface RateFetcher {
    suspend fun fetch(providerId: RateProviderId, base: String, currencies: Set<String>): FetchedRates
}

/** مصدر أسعار واحد. */
interface RateProvider {
    val id: RateProviderId
    /** الطلب لا يحمل أي بيانات مالية؛ يرسل فقط رمز العملة الأساسية. */
    fun buildUrls(base: String): List<String>
    fun parse(body: String, base: String, wanted: Set<String>): Map<String, BigDecimal>
}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun invert(perBase: BigDecimal): BigDecimal? =
    if (perBase.signum() <= 0) null else BigDecimal.ONE.divide(perBase, 6, RoundingMode.HALF_UP)

/** ExchangeRate-API (Open Access) — مجاني بدون مفتاح: https://open.er-api.com */
class OpenErApiProvider : RateProvider {
    override val id = RateProviderId.OPEN_ER_API
    override fun buildUrls(base: String) = listOf("https://open.er-api.com/v6/latest/${base.uppercase()}")
    override fun parse(body: String, base: String, wanted: Set<String>): Map<String, BigDecimal> {
        val root = json.parseToJsonElement(body).jsonObject
        val result = root["result"]?.jsonPrimitive?.contentOrNull
        if (result != null && result != "success") throw CurrencyFetchException.Parse("result=$result")
        val rates = root["rates"]?.jsonObject ?: throw CurrencyFetchException.Parse("no rates")
        return extract(rates, wanted) { it.uppercase() }
    }
}

/** fawazahmed0 currency-api — مجاني بدون مفتاح عبر jsDelivr مع رابط احتياطي. */
class FawazCurrencyApiProvider : RateProvider {
    override val id = RateProviderId.FAWAZ_CURRENCY_API
    override fun buildUrls(base: String): List<String> {
        val b = base.lowercase()
        return listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/$b.json",
            "https://latest.currency-api.pages.dev/v1/currencies/$b.json",
        )
    }
    override fun parse(body: String, base: String, wanted: Set<String>): Map<String, BigDecimal> {
        val root = json.parseToJsonElement(body).jsonObject
        val rates = root[base.lowercase()]?.jsonObject ?: throw CurrencyFetchException.Parse("no rates")
        return extract(rates, wanted) { it.lowercase() }
    }
}

private fun extract(rates: JsonObject, wanted: Set<String>, keyOf: (String) -> String): Map<String, BigDecimal> {
    val out = LinkedHashMap<String, BigDecimal>()
    for (code in wanted) {
        val raw = rates[keyOf(code)]?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull() ?: continue
        invert(raw)?.let { out[code.uppercase()] = it }
    }
    if (out.isEmpty()) throw CurrencyFetchException.Parse("no wanted currencies")
    return out
}

/**
 * خدمة أسعار العملات المستقلة.
 * - HTTPS فقط، مهلة اتصال محددة، معالجة أخطاء الشبكة.
 * - لا تحفظ أي شيء بنفسها؛ الحفظ يتم في CurrencyRepository فقط عند النجاح.
 */
@Singleton
class CurrencyService @Inject constructor() : RateFetcher {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val providers: Map<RateProviderId, RateProvider> = listOf(OpenErApiProvider(), FawazCurrencyApiProvider()).associateBy { it.id }

    fun provider(id: RateProviderId): RateProvider = providers.getValue(id)

    override suspend fun fetch(providerId: RateProviderId, base: String, currencies: Set<String>): FetchedRates = withContext(Dispatchers.IO) {
        val provider = provider(providerId)
        val wanted = currencies.map { it.uppercase() }.filter { it != base.uppercase() }.toSet()
        var lastError: Exception? = null
        for (url in provider.buildUrls(base)) {
            require(url.startsWith("https://")) { "HTTPS only" }
            try {
                val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw CurrencyFetchException.Http(resp.code)
                    val body = resp.body?.string() ?: throw CurrencyFetchException.Parse("empty body")
                    val rates = provider.parse(body, base, wanted)
                    return@withContext FetchedRates(base.uppercase(), rates, providerId, System.currentTimeMillis())
                }
            } catch (e: CurrencyFetchException) {
                lastError = e
            } catch (e: IOException) {
                lastError = CurrencyFetchException.Network(e.javaClass.simpleName)
            } catch (e: RuntimeException) {
                lastError = CurrencyFetchException.Parse(e.javaClass.simpleName)
            }
        }
        throw lastError ?: CurrencyFetchException.Network("unknown")
    }
}
