package com.hossammo.cash.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.hossammo.cash.R
import com.hossammo.cash.data.db.AppDatabase
import com.hossammo.cash.data.db.entity.AppSettingEntity
import com.hossammo.cash.data.db.entity.DebtEntity
import com.hossammo.cash.data.db.entity.LiquidityItemEntity
import com.hossammo.cash.data.db.entity.LocationEntity
import com.hossammo.cash.data.db.entity.SavingsCategoryEntity
import com.hossammo.cash.domain.model.AssetType
import com.hossammo.cash.domain.model.DebtDirection
import com.hossammo.cash.domain.model.LiquidityLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * بيانات أول تشغيل: الفئات الأساسية وعناصرها وأسماء الديون فقط.
 * لا تُنشأ أي أرصدة أو مبالغ مالية — كل القيم تبقى فارغة.
 */
@Singleton
class SeedRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {
    private data class SeedItem(
        val key: String,
        val nameRes: Int,
        val asset: AssetType,
        val currency: String,
        val color: String,
        val icon: String,
        val level: LiquidityLevel,
        val variable: Boolean,
        val locationRes: Int? = null,
    )

    private val seedItems = listOf(
        SeedItem("bank", R.string.seed_cat_bank, AssetType.BANK_ACCOUNT, "EGP", "#246BCE", "bank", LiquidityLevel.IMMEDIATE, false),
        SeedItem("cash", R.string.seed_cat_cash, AssetType.CASH, "EGP", "#2E8B57", "cash", LiquidityLevel.IMMEDIATE, false),
        SeedItem("gold", R.string.seed_cat_gold, AssetType.GOLD, "EGP", "#C69B35", "gold", LiquidityLevel.NEEDS_LIQUIDATION, false),
        SeedItem("stocks", R.string.seed_cat_stocks, AssetType.STOCKS, "EGP", "#7B4FB5", "stocks", LiquidityLevel.WITHIN_DAYS, false),
        SeedItem("farm", R.string.seed_cat_farm, AssetType.PROJECT, "EGP", "#5E8C31", "farm", LiquidityLevel.UNSPECIFIED, true, R.string.seed_loc_farm),
        SeedItem("factory", R.string.seed_cat_factory, AssetType.PROJECT, "EGP", "#8D5A3B", "factory", LiquidityLevel.UNSPECIFIED, true, R.string.seed_loc_factory),
        SeedItem("usd", R.string.seed_cat_usd, AssetType.FOREIGN_CURRENCY, "USD", "#1F7A6D", "currency", LiquidityLevel.IMMEDIATE, true),
        SeedItem("sar", R.string.seed_cat_sar, AssetType.FOREIGN_CURRENCY, "SAR", "#0F6E3F", "currency", LiquidityLevel.IMMEDIATE, true),
        SeedItem("kwd", R.string.seed_cat_kwd, AssetType.FOREIGN_CURRENCY, "KWD", "#B5473A", "currency", LiquidityLevel.IMMEDIATE, true),
        SeedItem("eur", R.string.seed_cat_eur, AssetType.FOREIGN_CURRENCY, "EUR", "#3D5A99", "currency", LiquidityLevel.IMMEDIATE, true),
    )

    private val owedToMe = listOf(
        R.string.seed_debt_islam, R.string.seed_debt_iman, R.string.seed_debt_khaled, R.string.seed_debt_mahmoud,
        R.string.seed_debt_fayoum, R.string.seed_debt_ahmed_hussein, R.string.seed_debt_walid_ashry,
        R.string.seed_debt_ahmed_ashry, R.string.seed_debt_walid_gebaly,
    )
    private val iOwe = listOf(R.string.seed_debt_land, R.string.seed_debt_to_islam, R.string.seed_debt_to_mahmoud)

    suspend fun seedIfNeeded() {
        val settingDao = db.settingDao()
        if (settingDao.get(AppSettingsStore.KEY_SEEDED) != null) return
        db.withTransaction {
            if (settingDao.get(AppSettingsStore.KEY_SEEDED) != null) return@withTransaction
            val t = System.currentTimeMillis()
            if (settingDao.get(AppSettingsStore.KEY_BASE_CURRENCY) == null) {
                settingDao.upsert(AppSettingEntity(AppSettingsStore.KEY_BASE_CURRENCY, AppSettingsStore.DEFAULT_BASE, t))
            }
            seedItems.forEachIndexed { index, s ->
                val name = context.getString(s.nameRes)
                val catId = db.categoryDao().bySystemKey(s.key)?.id ?: UUID.randomUUID().toString().also { id ->
                    db.categoryDao().upsert(SavingsCategoryEntity(id, name, s.key, s.color, s.icon, index, t, t))
                }
                val locId = s.locationRes?.let { res ->
                    val locName = context.getString(res)
                    db.locationDao().byName(locName)?.id ?: UUID.randomUUID().toString().also { id ->
                        db.locationDao().upsert(LocationEntity(id, locName, null, t, t))
                    }
                }
                if (db.itemDao().bySystemKey(s.key) == null) {
                    db.itemDao().upsert(
                        LiquidityItemEntity(
                            id = UUID.randomUUID().toString(), name = name, categoryId = catId, locationId = locId,
                            assetType = s.asset, currencyCode = s.currency, colorHex = s.color, icon = s.icon,
                            liquidityLevel = s.level, includeInTotal = true, isArchived = false, isVariable = s.variable,
                            notes = null, sortOrder = index, systemKey = s.key, createdAt = t, updatedAt = t,
                        )
                    )
                }
            }
            if (db.debtDao().count() == 0) {
                var order = 0
                owedToMe.forEach { res ->
                    db.debtDao().upsert(DebtEntity(UUID.randomUUID().toString(), context.getString(res), DebtDirection.OWED_TO_ME, null, sortOrder = order++, createdAt = t, updatedAt = t))
                }
                iOwe.forEach { res ->
                    db.debtDao().upsert(DebtEntity(UUID.randomUUID().toString(), context.getString(res), DebtDirection.I_OWE, null, sortOrder = order++, createdAt = t, updatedAt = t))
                }
            }
            settingDao.upsert(AppSettingEntity(AppSettingsStore.KEY_SEEDED, "1", t))
        }
    }
}
