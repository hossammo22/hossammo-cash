package com.hossammo.cash.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hossammo.cash.data.backup.BackupManager
import com.hossammo.cash.data.currency.FetchedRates
import com.hossammo.cash.data.currency.RateFetcher
import com.hossammo.cash.data.db.AppDatabase
import com.hossammo.cash.data.repo.AppSettingsStore
import com.hossammo.cash.data.repo.AuditLogger
import com.hossammo.cash.data.repo.CurrencyRepository
import com.hossammo.cash.data.repo.DebtRepository
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.data.repo.SeedRepository
import com.hossammo.cash.data.settings.RateProviderId
import com.hossammo.cash.data.settings.UserPreferencesRepository
import java.math.BigDecimal

/** مصدر أسعار وهمي للاختبارات: يمكن جعله يفشل لمحاكاة انقطاع الإنترنت. */
class FakeRateFetcher : RateFetcher {
    var rates: Map<String, BigDecimal> = emptyMap()
    var offline = false
    var calls = 0
    override suspend fun fetch(providerId: RateProviderId, base: String, currencies: Set<String>): FetchedRates {
        calls++
        if (offline) throw com.hossammo.cash.data.currency.CurrencyFetchException.Network("offline")
        return FetchedRates(base, rates.filterKeys { it in currencies }, providerId, System.currentTimeMillis())
    }
}

class FakeMetalFetcher : com.hossammo.cash.data.metal.MetalPriceFetcher {
    var goldOunce: BigDecimal = BigDecimal("150000")
    var silverOunce: BigDecimal = BigDecimal("1800")
    var offline = false
    override suspend fun fetch(base: String, usdRateToBase: BigDecimal?): com.hossammo.cash.data.metal.FetchedMetalPrices {
        if (offline) throw com.hossammo.cash.data.currency.CurrencyFetchException.Network("offline")
        return com.hossammo.cash.data.metal.FetchedMetalPrices(goldOunce, silverOunce, "fake")
    }
}

/** بيئة اختبار كاملة بقاعدة بيانات في الذاكرة (بدون SQLCipher لأن مكتبته الأصلية لا تعمل على JVM). */
class TestEnv {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val audit = AuditLogger(db.auditDao())
    val settings = AppSettingsStore(db.settingDao())
    val liquidity = LiquidityRepository(db, audit, settings)
    val debts = DebtRepository(context, db, audit)
    val seed = SeedRepository(context, db)
    val prefs = UserPreferencesRepository(context)
    val fetcher = FakeRateFetcher()
    val currency = CurrencyRepository(db, fetcher, prefs, settings, liquidity, audit)
    val backup = BackupManager(context, db, audit)
    val metalFetcher = FakeMetalFetcher()
    val metals = com.hossammo.cash.data.metal.MetalRepository(db, metalFetcher, settings, liquidity, audit)

    fun close() = db.close()
}

fun bd(v: String) = BigDecimal(v)
