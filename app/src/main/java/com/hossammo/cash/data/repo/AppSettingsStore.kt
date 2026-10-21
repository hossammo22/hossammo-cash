package com.hossammo.cash.data.repo

import com.hossammo.cash.data.db.dao.AppSettingDao
import com.hossammo.cash.data.db.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** إعدادات مرتبطة بالبيانات المالية (تُحفظ في جدول AppSettings وتُنسخ احتياطيًا). */
@Singleton
class AppSettingsStore @Inject constructor(private val dao: AppSettingDao) {

    val baseCurrency: Flow<String> = dao.observeAll().map { list ->
        list.firstOrNull { it.key == KEY_BASE_CURRENCY }?.value ?: DEFAULT_BASE
    }

    val deductTransferFees: Flow<Boolean> = dao.observeAll().map { list ->
        list.firstOrNull { it.key == KEY_DEDUCT_FEES }?.value?.toBooleanStrictOrNull() ?: true
    }

    suspend fun baseCurrency(): String = dao.get(KEY_BASE_CURRENCY) ?: DEFAULT_BASE
    suspend fun deductTransferFees(): Boolean = dao.get(KEY_DEDUCT_FEES)?.toBooleanStrictOrNull() ?: true

    suspend fun setBaseCurrency(code: String) = put(KEY_BASE_CURRENCY, code.uppercase())
    suspend fun setDeductTransferFees(v: Boolean) = put(KEY_DEDUCT_FEES, v.toString())

    private suspend fun put(key: String, value: String) =
        dao.upsert(AppSettingEntity(key, value, System.currentTimeMillis()))

    companion object {
        const val DEFAULT_BASE = "EGP"
        const val KEY_BASE_CURRENCY = "base_currency"
        const val KEY_DEDUCT_FEES = "deduct_transfer_fees"
        const val KEY_SEEDED = "seeded_v1"
    }
}
