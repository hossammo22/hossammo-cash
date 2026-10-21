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
        /** مفتاح الفئة (افتراضيًا = key). */
        val categoryKey: String = key,
        val categoryNameRes: Int = nameRes,
        val metal: com.hossammo.cash.domain.calc.MetalType? = null,
        val metalForm: com.hossammo.cash.domain.calc.MetalForm? = null,
        val weightGrams: String? = null,
        val purity: Int? = null,
    )

    /** البنوك التي يتعامل معها المستخدم (أسماء فقط بدون أرصدة؛ قابلة لإعادة التسمية). */
    private val bankItems = listOf(
        "bank_cib1" to R.string.seed_bank_cib1, "bank_cib2" to R.string.seed_bank_cib2,
        "bank_mashreq" to R.string.seed_bank_mashreq, "bank_cairo" to R.string.seed_bank_cairo,
        "bank_nbe_prepaid" to R.string.seed_bank_nbe_prepaid, "bank_nbe_saving" to R.string.seed_bank_nbe_saving,
        "bank_misr" to R.string.seed_bank_misr,
    ).map { (k, res) ->
        SeedItem(k, res, AssetType.BANK_ACCOUNT, "EGP", "#246BCE", "bank", LiquidityLevel.IMMEDIATE, false, categoryKey = "bank", categoryNameRes = R.string.seed_cat_bank)
    }

    /** سبيكة الفضة 250 جرام التي ذكرها المستخدم (الوزن فقط؛ القيمة تُحسب من سعر السوق). */
    private val silverBar = SeedItem(
        "silver_bar_250", R.string.seed_silver_bar, AssetType.GOLD, "EGP", "#8E9AA6", "gold", LiquidityLevel.NEEDS_LIQUIDATION, true,
        categoryKey = "gold", categoryNameRes = R.string.seed_cat_gold,
        metal = com.hossammo.cash.domain.calc.MetalType.SILVER, metalForm = com.hossammo.cash.domain.calc.MetalForm.BAR, weightGrams = "250", purity = 999,
    )

    private val otherItems = listOf(
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

    private val seedItems get() = bankItems + otherItems + silverBar

    private val owedToMe = listOf(
        R.string.seed_debt_islam, R.string.seed_debt_iman, R.string.seed_debt_khaled, R.string.seed_debt_mahmoud,
        R.string.seed_debt_fayoum, R.string.seed_debt_ahmed_hussein, R.string.seed_debt_walid_ashry,
        R.string.seed_debt_ahmed_ashry, R.string.seed_debt_walid_gebaly,
    )
    private val iOwe = listOf(R.string.seed_debt_land, R.string.seed_debt_to_islam, R.string.seed_debt_to_mahmoud)

    suspend fun seedIfNeeded() {
        val settingDao = db.settingDao()
        if (settingDao.get(AppSettingsStore.KEY_SEEDED) != null) { upgradeV2(); return }
        db.withTransaction {
            if (settingDao.get(AppSettingsStore.KEY_SEEDED) != null) return@withTransaction
            val t = System.currentTimeMillis()
            if (settingDao.get(AppSettingsStore.KEY_BASE_CURRENCY) == null) {
                settingDao.upsert(AppSettingEntity(AppSettingsStore.KEY_BASE_CURRENCY, AppSettingsStore.DEFAULT_BASE, t))
            }
            seedItems.forEachIndexed { index, s -> insertItem(s, index, t) }
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
            settingDao.upsert(AppSettingEntity(KEY_SEEDED_V2, "1", t))
        }
        upgradeV2()
    }

    /**
     * ترقية الإصدار 2 للمستخدمين الحاليين: إضافة البنوك السبعة وسبيكة الفضة.
     * العنصر العام «البنوك» القديم: إن لم يكن له أي رصيد يُؤرشف حتى لا يتكرر، وإلا يبقى كما هو.
     */
    suspend fun upgradeV2() {
        val settingDao = db.settingDao()
        if (settingDao.get(KEY_SEEDED_V2) != null) return
        db.withTransaction {
            if (settingDao.get(KEY_SEEDED_V2) != null) return@withTransaction
            val t = System.currentTimeMillis()
            db.itemDao().bySystemKey("bank")?.let { old ->
                if (old.deletedAt == null && db.balanceDao().countForItem(old.id) == 0) {
                    db.itemDao().upsert(old.copy(isArchived = true, updatedAt = t))
                }
            }
            var order = db.itemDao().maxSort() + 1
            (bankItems + silverBar).forEach { s -> insertItem(s, order++, t) }
            settingDao.upsert(AppSettingEntity(KEY_SEEDED_V2, "1", t))
        }
    }

    private suspend fun insertItem(s: SeedItem, index: Int, t: Long) {
        val catName = context.getString(s.categoryNameRes)
        val catId = db.categoryDao().bySystemKey(s.categoryKey)?.id ?: UUID.randomUUID().toString().also { id ->
            db.categoryDao().upsert(SavingsCategoryEntity(id, catName, s.categoryKey, s.color, s.icon, db.categoryDao().maxSort() + 1, t, t))
        }
        val locId = s.locationRes?.let { res ->
            val locName = context.getString(res)
            db.locationDao().byName(locName)?.id ?: UUID.randomUUID().toString().also { id ->
                db.locationDao().upsert(LocationEntity(id, locName, null, t, t))
            }
        }
        if (db.itemDao().bySystemKey(s.key) != null) return
        db.itemDao().upsert(
            LiquidityItemEntity(
                id = UUID.randomUUID().toString(), name = context.getString(s.nameRes), categoryId = catId, locationId = locId,
                assetType = s.asset, currencyCode = s.currency, colorHex = s.color, icon = s.icon,
                liquidityLevel = s.level, includeInTotal = true, isArchived = false, isVariable = s.variable,
                notes = null, sortOrder = index, systemKey = s.key, createdAt = t, updatedAt = t,
                metalType = s.metal?.name, metalForm = s.metalForm?.name, weightGrams = s.weightGrams?.let { java.math.BigDecimal(it) },
                purity = s.purity, pieceCount = if (s.metal != null) 1 else null,
            )
        )
    }

    companion object {
        const val KEY_SEEDED_V2 = "seeded_v2_banks_metals"
    }
}
