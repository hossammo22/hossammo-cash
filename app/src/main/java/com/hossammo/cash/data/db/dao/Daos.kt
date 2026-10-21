package com.hossammo.cash.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.hossammo.cash.data.db.entity.AppSettingEntity
import com.hossammo.cash.data.db.entity.AttachmentEntity
import com.hossammo.cash.data.db.entity.AuditLogEntity
import com.hossammo.cash.data.db.entity.BackupHistoryEntity
import com.hossammo.cash.data.db.entity.CurrencyRateEntity
import com.hossammo.cash.data.db.entity.DebtEntity
import com.hossammo.cash.data.db.entity.DebtPaymentEntity
import com.hossammo.cash.data.db.entity.LiquidityItemEntity
import com.hossammo.cash.data.db.entity.LocationEntity
import com.hossammo.cash.data.db.entity.MonthlyBalanceEntity
import com.hossammo.cash.data.db.entity.MonthlyCurrencySnapshotEntity
import com.hossammo.cash.data.db.entity.SavingsCategoryEntity
import com.hossammo.cash.data.db.entity.SavingsMonthEntity
import com.hossammo.cash.data.db.entity.TransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM savings_categories WHERE deletedAt IS NULL ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<SavingsCategoryEntity>>

    @Query("SELECT * FROM savings_categories WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<SavingsCategoryEntity>>

    @Query("SELECT * FROM savings_categories WHERE id = :id")
    suspend fun byId(id: String): SavingsCategoryEntity?

    @Query("SELECT * FROM savings_categories WHERE systemKey = :key LIMIT 1")
    suspend fun bySystemKey(key: String): SavingsCategoryEntity?

    @Query("SELECT COUNT(*) FROM savings_categories")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM savings_categories")
    suspend fun maxSort(): Int

    @Upsert suspend fun upsert(e: SavingsCategoryEntity)
    @Query("SELECT * FROM savings_categories") suspend fun all(): List<SavingsCategoryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<SavingsCategoryEntity>)
    @Query("DELETE FROM savings_categories WHERE id = :id") suspend fun hardDelete(id: String)
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations WHERE deletedAt IS NULL ORDER BY name")
    fun observeActive(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE name = :name AND deletedAt IS NULL LIMIT 1")
    suspend fun byName(name: String): LocationEntity?

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun byId(id: String): LocationEntity?

    @Upsert suspend fun upsert(e: LocationEntity)
    @Query("SELECT * FROM locations") suspend fun all(): List<LocationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<LocationEntity>)
}

@Dao
interface LiquidityItemDao {
    @Query("SELECT * FROM liquidity_items WHERE deletedAt IS NULL ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<LiquidityItemEntity>>

    @Query("SELECT * FROM liquidity_items WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<LiquidityItemEntity>>

    @Query("SELECT * FROM liquidity_items WHERE id = :id")
    suspend fun byId(id: String): LiquidityItemEntity?

    @Query("SELECT * FROM liquidity_items WHERE id = :id")
    fun observeById(id: String): Flow<LiquidityItemEntity?>

    @Query("SELECT * FROM liquidity_items WHERE deletedAt IS NULL AND isArchived = 0 ORDER BY sortOrder, name")
    suspend fun activeNotArchived(): List<LiquidityItemEntity>

    @Query("SELECT * FROM liquidity_items WHERE systemKey = :key LIMIT 1")
    suspend fun bySystemKey(key: String): LiquidityItemEntity?

    @Query("SELECT COUNT(*) FROM liquidity_items WHERE deletedAt IS NULL AND categoryId = :categoryId")
    suspend fun countInCategory(categoryId: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM liquidity_items")
    suspend fun maxSort(): Int

    @Upsert suspend fun upsert(e: LiquidityItemEntity)
    @Query("SELECT * FROM liquidity_items") suspend fun all(): List<LiquidityItemEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<LiquidityItemEntity>)
    @Query("DELETE FROM liquidity_items WHERE id = :id") suspend fun hardDelete(id: String)
}

@Dao
interface SavingsMonthDao {
    @Query("SELECT * FROM savings_months WHERE deletedAt IS NULL ORDER BY monthKey")
    fun observeActive(): Flow<List<SavingsMonthEntity>>

    @Query("SELECT * FROM savings_months WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<SavingsMonthEntity>>

    @Query("SELECT * FROM savings_months WHERE monthKey = :key LIMIT 1")
    suspend fun byKey(key: Int): SavingsMonthEntity?

    @Query("SELECT * FROM savings_months WHERE id = :id")
    suspend fun byId(id: String): SavingsMonthEntity?

    @Query("SELECT * FROM savings_months WHERE deletedAt IS NULL AND monthKey < :key ORDER BY monthKey DESC LIMIT 1")
    suspend fun previousActive(key: Int): SavingsMonthEntity?

    @Query("SELECT * FROM savings_months WHERE deletedAt IS NULL ORDER BY monthKey DESC LIMIT 1")
    suspend fun latestActive(): SavingsMonthEntity?

    @Upsert suspend fun upsert(e: SavingsMonthEntity)
    @Query("SELECT * FROM savings_months") suspend fun all(): List<SavingsMonthEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<SavingsMonthEntity>)
    @Query("DELETE FROM savings_months WHERE id = :id") suspend fun hardDelete(id: String)
}

@Dao
interface MonthlyBalanceDao {
    @Query("SELECT COUNT(*) FROM monthly_balances WHERE itemId = :itemId AND deletedAt IS NULL")
    suspend fun countForItem(itemId: String): Int

    /** كل الأرصدة النشطة لأشهر نشطة ولعناصر غير محذوفة. */
    @Query(
        """SELECT b.* FROM monthly_balances b
           JOIN savings_months m ON m.id = b.monthId
           JOIN liquidity_items i ON i.id = b.itemId
           WHERE b.deletedAt IS NULL AND m.deletedAt IS NULL AND i.deletedAt IS NULL
           ORDER BY b.monthKey"""
    )
    fun observeActive(): Flow<List<MonthlyBalanceEntity>>

    @Query("SELECT * FROM monthly_balances WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<MonthlyBalanceEntity>>

    @Query("SELECT * FROM monthly_balances WHERE monthId = :monthId AND deletedAt IS NULL")
    suspend fun activeForMonth(monthId: String): List<MonthlyBalanceEntity>

    @Query("SELECT * FROM monthly_balances WHERE monthId = :monthId AND itemId = :itemId LIMIT 1")
    suspend fun byMonthAndItem(monthId: String, itemId: String): MonthlyBalanceEntity?

    @Query("SELECT * FROM monthly_balances WHERE id = :id")
    suspend fun byId(id: String): MonthlyBalanceEntity?

    @Query("SELECT * FROM monthly_balances WHERE monthId = :monthId AND currencyCode = :currency AND deletedAt IS NULL")
    suspend fun forMonthAndCurrency(monthId: String, currency: String): List<MonthlyBalanceEntity>

    @Query("SELECT * FROM monthly_balances WHERE rateLocked = 0 AND currencyCode = :currency AND deletedAt IS NULL")
    suspend fun unlockedForCurrency(currency: String): List<MonthlyBalanceEntity>

    @Upsert suspend fun upsert(e: MonthlyBalanceEntity)
    @Upsert suspend fun upsertAll(list: List<MonthlyBalanceEntity>)
    @Query("SELECT * FROM monthly_balances") suspend fun all(): List<MonthlyBalanceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<MonthlyBalanceEntity>)
    @Query("DELETE FROM monthly_balances WHERE id = :id") suspend fun hardDelete(id: String)
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers WHERE deletedAt IS NULL ORDER BY transferDate DESC, createdAt DESC")
    fun observeActive(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun byId(id: String): TransferEntity?

    @Upsert suspend fun upsert(e: TransferEntity)
    @Query("SELECT * FROM transfers") suspend fun all(): List<TransferEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<TransferEntity>)
    @Query("DELETE FROM transfers WHERE id = :id") suspend fun hardDelete(id: String)
}

@Dao
interface CurrencyRateDao {
    @Query("SELECT * FROM currency_rates ORDER BY currencyCode")
    fun observeAll(): Flow<List<CurrencyRateEntity>>

    @Query("SELECT * FROM currency_rates WHERE currencyCode = :code")
    suspend fun byCode(code: String): CurrencyRateEntity?

    @Upsert suspend fun upsert(e: CurrencyRateEntity)
    @Query("SELECT * FROM currency_rates") suspend fun all(): List<CurrencyRateEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<CurrencyRateEntity>)
}

@Dao
interface MonthlyCurrencySnapshotDao {
    @Query("SELECT * FROM monthly_currency_snapshots ORDER BY monthKey")
    fun observeAll(): Flow<List<MonthlyCurrencySnapshotEntity>>

    @Query("SELECT * FROM monthly_currency_snapshots WHERE monthId = :monthId AND currencyCode = :code LIMIT 1")
    suspend fun get(monthId: String, code: String): MonthlyCurrencySnapshotEntity?

    @Upsert suspend fun upsert(e: MonthlyCurrencySnapshotEntity)
    @Query("DELETE FROM monthly_currency_snapshots WHERE monthId = :monthId AND currencyCode = :code")
    suspend fun delete(monthId: String, code: String)
    @Query("SELECT * FROM monthly_currency_snapshots") suspend fun all(): List<MonthlyCurrencySnapshotEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<MonthlyCurrencySnapshotEntity>)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts WHERE deletedAt IS NULL ORDER BY direction, sortOrder, name")
    fun observeActive(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun byId(id: String): DebtEntity?

    @Query("SELECT * FROM debts WHERE id = :id")
    fun observeById(id: String): Flow<DebtEntity?>

    @Query("SELECT COUNT(*) FROM debts")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM debts")
    suspend fun maxSort(): Int

    @Upsert suspend fun upsert(e: DebtEntity)
    @Query("SELECT * FROM debts") suspend fun all(): List<DebtEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<DebtEntity>)
    @Query("DELETE FROM debts WHERE id = :id") suspend fun hardDelete(id: String)
}

@Dao
interface DebtPaymentDao {
    @Query(
        """SELECT p.* FROM debt_payments p JOIN debts d ON d.id = p.debtId
           WHERE p.deletedAt IS NULL AND d.deletedAt IS NULL ORDER BY p.paymentDate DESC, p.createdAt DESC"""
    )
    fun observeActive(): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debt_payments WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId AND deletedAt IS NULL")
    suspend fun activeForDebt(debtId: String): List<DebtPaymentEntity>

    @Query("SELECT * FROM debt_payments WHERE id = :id")
    suspend fun byId(id: String): DebtPaymentEntity?

    @Upsert suspend fun upsert(e: DebtPaymentEntity)
    @Query("SELECT * FROM debt_payments") suspend fun all(): List<DebtPaymentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<DebtPaymentEntity>)
    @Query("DELETE FROM debt_payments WHERE id = :id") suspend fun hardDelete(id: String)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE ownerType = :type AND ownerId = :ownerId AND deletedAt IS NULL ORDER BY createdAt")
    fun observeFor(type: String, ownerId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun byId(id: String): AttachmentEntity?

    @Upsert suspend fun upsert(e: AttachmentEntity)
    @Query("SELECT * FROM attachments") suspend fun all(): List<AttachmentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<AttachmentEntity>)
    @Query("DELETE FROM attachments WHERE id = :id") suspend fun hardDelete(id: String)
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSettingEntity>>

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert suspend fun upsert(e: AppSettingEntity)
    @Query("SELECT * FROM app_settings") suspend fun all(): List<AppSettingEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<AppSettingEntity>)
}

@Dao
interface BackupHistoryDao {
    @Query("SELECT * FROM backup_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BackupHistoryEntity>>

    @Query("SELECT * FROM backup_history WHERE success = 1 AND type IN ('FULL','PERIOD') ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastSuccessful(): BackupHistoryEntity?

    @Query("SELECT * FROM backup_history WHERE success = 1 AND type IN ('FULL','PERIOD') ORDER BY createdAt DESC LIMIT 1")
    fun observeLastSuccessful(): Flow<BackupHistoryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(e: BackupHistoryEntity)
    @Query("SELECT * FROM backup_history") suspend fun all(): List<BackupHistoryEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<BackupHistoryEntity>)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<AuditLogEntity>>

    @Insert suspend fun insert(e: AuditLogEntity)
    @Query("SELECT * FROM audit_log") suspend fun all(): List<AuditLogEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<AuditLogEntity>)
}

/** عمليات على مستوى القاعدة كاملة (للاستعادة وحذف الكل). */
@Dao
interface MaintenanceDao {
    @Query("DELETE FROM attachments") suspend fun clearAttachments()
    @Query("DELETE FROM debt_payments") suspend fun clearPayments()
    @Query("DELETE FROM debts") suspend fun clearDebts()
    @Query("DELETE FROM transfers") suspend fun clearTransfers()
    @Query("DELETE FROM monthly_currency_snapshots") suspend fun clearSnapshots()
    @Query("DELETE FROM monthly_balances") suspend fun clearBalances()
    @Query("DELETE FROM savings_months") suspend fun clearMonths()
    @Query("DELETE FROM liquidity_items") suspend fun clearItems()
    @Query("DELETE FROM locations") suspend fun clearLocations()
    @Query("DELETE FROM savings_categories") suspend fun clearCategories()
    @Query("DELETE FROM currency_rates") suspend fun clearRates()
    @Query("DELETE FROM app_settings") suspend fun clearSettings()
    @Query("DELETE FROM audit_log") suspend fun clearAudit()
    @Query("DELETE FROM backup_history") suspend fun clearBackupHistory()
}
