package com.hossammo.cash.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hossammo.cash.domain.model.AssetType
import com.hossammo.cash.domain.model.DebtDirection
import com.hossammo.cash.domain.model.LiquidityLevel
import com.hossammo.cash.domain.model.RateSource
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/*
 * ملاحظات عامة:
 * - كل المعرفات UUID كنص.
 * - createdAt / updatedAt / deletedAt بالمللي ثانية (Epoch).
 * - deletedAt != null يعني أن السجل في سلة المحذوفات (Soft Delete).
 * - التواريخ (بدون وقت) تُحفظ كـ epochDay.
 */

@Serializable
@Entity(
    tableName = "savings_categories",
    indices = [Index("deletedAt"), Index("sortOrder")]
)
data class SavingsCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** مفتاح الفئة الأساسية (للفئات التي أنشأها التطبيق) أو null للفئات المخصصة. */
    val systemKey: String? = null,
    val colorHex: String,
    val icon: String,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(tableName = "locations", indices = [Index("deletedAt"), Index(value = ["name"])])
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "liquidity_items",
    foreignKeys = [
        ForeignKey(entity = SavingsCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = LocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("categoryId"), Index("locationId"), Index("currencyCode"), Index("deletedAt"), Index("isArchived")]
)
data class LiquidityItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String,
    val locationId: String? = null,
    val assetType: AssetType,
    val currencyCode: String,
    val colorHex: String,
    val icon: String,
    val liquidityLevel: LiquidityLevel = LiquidityLevel.UNSPECIFIED,
    val includeInTotal: Boolean = true,
    val isArchived: Boolean = false,
    /** يظهر في شاشة «المدخرات المتغيرة». */
    val isVariable: Boolean = false,
    val notes: String? = null,
    val sortOrder: Int = 0,
    val systemKey: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** تفاصيل المعادن الثمينة (اختيارية): GOLD / SILVER. */
    val metalType: String? = null,
    /** BAR / GOLD_POUND / COIN / JEWELRY / OTHER */
    val metalForm: String? = null,
    /** وزن القطعة الواحدة بالجرام. */
    @Contextual val weightGrams: BigDecimal? = null,
    /** العيار للذهب (قيراط) أو النقاء بالألف للفضة. */
    val purity: Int? = null,
    /** عدد القطع (افتراضيًا 1). */
    val pieceCount: Int? = null,
)

@Serializable
@Entity(
    tableName = "savings_months",
    indices = [Index(value = ["monthKey"], unique = true), Index("deletedAt")]
)
data class SavingsMonthEntity(
    @PrimaryKey val id: String,
    /** yyyyMM */
    val monthKey: Int,
    val year: Int,
    val month: Int,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "monthly_balances",
    foreignKeys = [
        ForeignKey(entity = SavingsMonthEntity::class, parentColumns = ["id"], childColumns = ["monthId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LiquidityItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["monthId", "itemId"], unique = true),
        Index("itemId"), Index("monthKey"), Index("currencyCode"), Index("deletedAt"),
    ]
)
data class MonthlyBalanceEntity(
    @PrimaryKey val id: String,
    val monthId: String,
    /** نسخة من مفتاح الشهر لتسريع الاستعلامات والترتيب. */
    val monthKey: Int,
    val itemId: String,
    @Contextual val amountOriginal: BigDecimal,
    val currencyCode: String,
    /** سعر الصرف المستخدم (1 للعملة الأساسية). null إذا لم يتوفر سعر. */
    @Contextual val exchangeRate: BigDecimal?,
    /** القيمة المقابلة بالعملة الأساسية (Snapshot تاريخية). */
    @Contextual val valueBase: BigDecimal?,
    val rateSource: RateSource,
    /** هل السعر مثبت لهذا الشهر؟ */
    val rateLocked: Boolean = false,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(entity = LiquidityItemEntity::class, parentColumns = ["id"], childColumns = ["fromItemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LiquidityItemEntity::class, parentColumns = ["id"], childColumns = ["toItemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("fromItemId"), Index("toItemId"), Index("monthKey"), Index("deletedAt")]
)
data class TransferEntity(
    @PrimaryKey val id: String,
    val fromItemId: String,
    val toItemId: String,
    /** المبلغ بعملة العنصر المرسل. */
    @Contextual val amount: BigDecimal,
    val currencyCode: String,
    /** المبلغ المستلم بعملة العنصر المستلم. */
    @Contextual val receivedAmount: BigDecimal,
    @Contextual val fee: BigDecimal,
    val deductFee: Boolean,
    val transferDate: Long,
    val monthKey: Int,
    /** هل تم تطبيق التحويل على أرصدة الشهر؟ */
    val appliedToBalances: Boolean,
    val reference: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** آخر سعر معروف لكل عملة مقابل العملة الأساسية. لا يُحذف عند فشل التحديث. */
@Serializable
@Entity(tableName = "currency_rates", indices = [Index("fetchedAt")])
data class CurrencyRateEntity(
    @PrimaryKey val currencyCode: String,
    @Contextual val rateToBase: BigDecimal,
    val baseCurrency: String,
    /** معرف المصدر (open_er_api / fawaz_currency_api / manual). */
    val source: String,
    val isManual: Boolean,
    val fetchedAt: Long,
    val updatedAt: Long,
)

/** سعر مثبت لعملة داخل شهر معين. */
@Serializable
@Entity(
    tableName = "monthly_currency_snapshots",
    foreignKeys = [ForeignKey(entity = SavingsMonthEntity::class, parentColumns = ["id"], childColumns = ["monthId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["monthId", "currencyCode"], unique = true), Index("currencyCode"), Index("monthKey")]
)
data class MonthlyCurrencySnapshotEntity(
    @PrimaryKey val id: String,
    val monthId: String,
    val monthKey: Int,
    val currencyCode: String,
    @Contextual val rate: BigDecimal,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "debts",
    indices = [Index("direction"), Index("dueDate"), Index("deletedAt")]
)
data class DebtEntity(
    @PrimaryKey val id: String,
    val name: String,
    val direction: DebtDirection,
    /** null = لم يُدخل المبلغ بعد. */
    @Contextual val originalAmount: BigDecimal?,
    val currencyCode: String = "EGP",
    val startDate: Long? = null,
    val dueDate: Long? = null,
    val notes: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "debt_payments",
    foreignKeys = [ForeignKey(entity = DebtEntity::class, parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("debtId"), Index("paymentDate"), Index("deletedAt")]
)
data class DebtPaymentEntity(
    @PrimaryKey val id: String,
    val debtId: String,
    @Contextual val amount: BigDecimal,
    val paymentDate: Long,
    val method: String? = null,
    val reference: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** مرفق محلي (يُنسخ إلى مجلد خاص بالتطبيق). ownerType: DEBT أو PAYMENT. */
@Serializable
@Entity(tableName = "attachments", indices = [Index(value = ["ownerType", "ownerId"]), Index("deletedAt")])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val fileName: String,
    val mimeType: String?,
    val localPath: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val deletedAt: Long? = null,
)

/** إعدادات مرتبطة بالبيانات وتُنسخ احتياطيًا معها (مثل سياسة الرسوم والعملة الأساسية). */
@Serializable
@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long,
)

@Serializable
@Entity(tableName = "backup_history", indices = [Index("createdAt")])
data class BackupHistoryEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val fileName: String,
    val location: String,
    val sizeBytes: Long,
    /** FULL أو PERIOD أو AUTO_BEFORE_RESTORE أو RESTORE */
    val type: String,
    val success: Boolean,
    val notes: String? = null,
)

@Serializable
@Entity(tableName = "audit_log", indices = [Index("timestamp"), Index(value = ["entityType", "entityId"])])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val entityType: String,
    val entityId: String,
    /** CREATE / UPDATE / DELETE / RESTORE / ARCHIVE / PURGE ... */
    val action: String,
    val summary: String,
)
