package com.hossammo.cash.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.hossammo.cash.data.db.dao.AppSettingDao
import com.hossammo.cash.data.db.dao.AttachmentDao
import com.hossammo.cash.data.db.dao.AuditLogDao
import com.hossammo.cash.data.db.dao.BackupHistoryDao
import com.hossammo.cash.data.db.dao.CategoryDao
import com.hossammo.cash.data.db.dao.CurrencyRateDao
import com.hossammo.cash.data.db.dao.DebtDao
import com.hossammo.cash.data.db.dao.DebtPaymentDao
import com.hossammo.cash.data.db.dao.LiquidityItemDao
import com.hossammo.cash.data.db.dao.LocationDao
import com.hossammo.cash.data.db.dao.MaintenanceDao
import com.hossammo.cash.data.db.dao.MonthlyBalanceDao
import com.hossammo.cash.data.db.dao.MonthlyCurrencySnapshotDao
import com.hossammo.cash.data.db.dao.SavingsMonthDao
import com.hossammo.cash.data.db.dao.TransferDao
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

@Database(
    entities = [
        SavingsCategoryEntity::class,
        LocationEntity::class,
        LiquidityItemEntity::class,
        SavingsMonthEntity::class,
        MonthlyBalanceEntity::class,
        TransferEntity::class,
        CurrencyRateEntity::class,
        MonthlyCurrencySnapshotEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        AttachmentEntity::class,
        AppSettingEntity::class,
        BackupHistoryEntity::class,
        AuditLogEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun locationDao(): LocationDao
    abstract fun itemDao(): LiquidityItemDao
    abstract fun monthDao(): SavingsMonthDao
    abstract fun balanceDao(): MonthlyBalanceDao
    abstract fun transferDao(): TransferDao
    abstract fun rateDao(): CurrencyRateDao
    abstract fun snapshotDao(): MonthlyCurrencySnapshotDao
    abstract fun debtDao(): DebtDao
    abstract fun paymentDao(): DebtPaymentDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun settingDao(): AppSettingDao
    abstract fun backupDao(): BackupHistoryDao
    abstract fun auditDao(): AuditLogDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        const val VERSION = 2
        const val NAME = "hossammo_cash.db"

        /**
         * سجل الترحيلات (Migrations). عند تغيير بنية القاعدة:
         * 1) ارفع VERSION.
         * 2) أضف Migration(n, n+1) هنا بأوامر SQL تحافظ على البيانات.
         * لا يُستخدم fallbackToDestructiveMigration مطلقًا حتى لا تُحذف بيانات المستخدم.
         */
        /** v2: حقول المعادن الثمينة (الوزن، العيار، الشكل، العدد) لعناصر السيولة. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE liquidity_items ADD COLUMN metalType TEXT")
                db.execSQL("ALTER TABLE liquidity_items ADD COLUMN metalForm TEXT")
                db.execSQL("ALTER TABLE liquidity_items ADD COLUMN weightGrams TEXT")
                db.execSQL("ALTER TABLE liquidity_items ADD COLUMN purity INTEGER")
                db.execSQL("ALTER TABLE liquidity_items ADD COLUMN pieceCount INTEGER")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
