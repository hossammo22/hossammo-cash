package com.hossammo.cash.data.backup

import com.hossammo.cash.data.db.entity.AppSettingEntity
import com.hossammo.cash.data.db.entity.AttachmentEntity
import com.hossammo.cash.data.db.entity.AuditLogEntity
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.math.BigDecimal

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}

val BackupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    serializersModule = SerializersModule { contextual(BigDecimalSerializer) }
}

@Serializable
data class BackupMeta(
    val format: Int = BackupFormat.FORMAT_VERSION,
    val schemaVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    /** FULL أو PERIOD */
    val type: String,
    val periodFrom: Int? = null,
    val periodTo: Int? = null,
    val counts: Map<String, Int> = emptyMap(),
    /** SHA-256 لمحتوى الجداول للتحقق من السلامة. */
    val checksum: String = "",
)

@Serializable
data class BackupAttachmentFile(val id: String, val base64: String)

@Serializable
data class BackupTables(
    val categories: List<SavingsCategoryEntity> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val items: List<LiquidityItemEntity> = emptyList(),
    val months: List<SavingsMonthEntity> = emptyList(),
    val balances: List<MonthlyBalanceEntity> = emptyList(),
    val transfers: List<TransferEntity> = emptyList(),
    val rates: List<CurrencyRateEntity> = emptyList(),
    val snapshots: List<MonthlyCurrencySnapshotEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val payments: List<DebtPaymentEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val settings: List<AppSettingEntity> = emptyList(),
    val audit: List<AuditLogEntity> = emptyList(),
    val attachmentFiles: List<BackupAttachmentFile> = emptyList(),
)

@Serializable
data class BackupPayload(val meta: BackupMeta, val tables: BackupTables)

object BackupFormat {
    const val FORMAT_VERSION = 1
    val MAGIC = byteArrayOf('H'.code.toByte(), 'M'.code.toByte(), 'C'.code.toByte(), 'B'.code.toByte())
    const val FILE_EXTENSION = "hmcbak"
    const val MIME = "application/octet-stream"
}

sealed interface BackupInspectResult {
    data class Valid(val meta: BackupMeta, val payload: BackupPayload) : BackupInspectResult
    data object WrongPasswordOrCorrupt : BackupInspectResult
    data object NotABackup : BackupInspectResult
    data object NewerVersion : BackupInspectResult
    data object ChecksumMismatch : BackupInspectResult
}
