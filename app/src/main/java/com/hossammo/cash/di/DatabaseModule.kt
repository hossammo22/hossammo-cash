package com.hossammo.cash.di

import android.content.Context
import androidx.room.Room
import com.hossammo.cash.data.db.AppDatabase
import com.hossammo.cash.data.security.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideKeyManager(@ApplicationContext context: Context): DatabaseKeyManager = DatabaseKeyManager(context)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, keyManager: DatabaseKeyManager): AppDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(keyManager.getOrCreatePassphrase())
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(factory)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
    }

    @Provides fun categoryDao(db: AppDatabase) = db.categoryDao()
    @Provides fun locationDao(db: AppDatabase) = db.locationDao()
    @Provides fun itemDao(db: AppDatabase) = db.itemDao()
    @Provides fun monthDao(db: AppDatabase) = db.monthDao()
    @Provides fun balanceDao(db: AppDatabase) = db.balanceDao()
    @Provides fun transferDao(db: AppDatabase) = db.transferDao()
    @Provides fun rateDao(db: AppDatabase) = db.rateDao()
    @Provides fun snapshotDao(db: AppDatabase) = db.snapshotDao()
    @Provides fun debtDao(db: AppDatabase) = db.debtDao()
    @Provides fun paymentDao(db: AppDatabase) = db.paymentDao()
    @Provides fun attachmentDao(db: AppDatabase) = db.attachmentDao()
    @Provides fun settingDao(db: AppDatabase) = db.settingDao()
    @Provides fun backupDao(db: AppDatabase) = db.backupDao()
    @Provides fun auditDao(db: AppDatabase) = db.auditDao()
    @Provides fun maintenanceDao(db: AppDatabase) = db.maintenanceDao()
}
