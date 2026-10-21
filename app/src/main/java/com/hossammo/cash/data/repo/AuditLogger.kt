package com.hossammo.cash.data.repo

import com.hossammo.cash.data.db.dao.AuditLogDao
import com.hossammo.cash.data.db.entity.AuditLogEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** سجل تعديلات محلي داخل القاعدة المشفرة. */
@Singleton
class AuditLogger @Inject constructor(private val dao: AuditLogDao) {
    fun observe(limit: Int = 500) = dao.observeRecent(limit)

    suspend fun log(entityType: String, entityId: String, action: String, summary: String) {
        dao.insert(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                entityType = entityType,
                entityId = entityId,
                action = action,
                summary = summary.take(300),
            )
        )
    }

    object Type {
        const val ITEM = "ITEM"
        const val CATEGORY = "CATEGORY"
        const val MONTH = "MONTH"
        const val BALANCE = "BALANCE"
        const val TRANSFER = "TRANSFER"
        const val RATE = "RATE"
        const val DEBT = "DEBT"
        const val PAYMENT = "PAYMENT"
        const val ATTACHMENT = "ATTACHMENT"
        const val BACKUP = "BACKUP"
        const val SETTINGS = "SETTINGS"
    }

    object Action {
        const val CREATE = "CREATE"
        const val UPDATE = "UPDATE"
        const val DELETE = "DELETE"
        const val RESTORE = "RESTORE"
        const val PURGE = "PURGE"
        const val ARCHIVE = "ARCHIVE"
        const val UNARCHIVE = "UNARCHIVE"
        const val LOCK = "LOCK"
        const val UNLOCK = "UNLOCK"
        const val COPY = "COPY"
        const val REFRESH = "REFRESH"
        const val EXPORT = "EXPORT"
        const val IMPORT = "IMPORT"
    }
}
