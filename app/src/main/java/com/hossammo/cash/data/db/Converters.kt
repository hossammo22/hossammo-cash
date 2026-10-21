package com.hossammo.cash.data.db

import androidx.room.TypeConverter
import com.hossammo.cash.domain.model.AssetType
import com.hossammo.cash.domain.model.DebtDirection
import com.hossammo.cash.domain.model.LiquidityLevel
import com.hossammo.cash.domain.model.RateSource
import java.math.BigDecimal

/** المبالغ تُحفظ كنص للحفاظ على الدقة الكاملة لـ BigDecimal. */
class Converters {
    @TypeConverter fun fromBigDecimal(v: BigDecimal?): String? = v?.toPlainString()
    @TypeConverter fun toBigDecimal(v: String?): BigDecimal? = v?.let { BigDecimal(it) }

    @TypeConverter fun fromAssetType(v: AssetType): String = v.name
    @TypeConverter fun toAssetType(v: String): AssetType = runCatching { AssetType.valueOf(v) }.getOrDefault(AssetType.CUSTOM)

    @TypeConverter fun fromLevel(v: LiquidityLevel): String = v.name
    @TypeConverter fun toLevel(v: String): LiquidityLevel = runCatching { LiquidityLevel.valueOf(v) }.getOrDefault(LiquidityLevel.UNSPECIFIED)

    @TypeConverter fun fromRateSource(v: RateSource): String = v.name
    @TypeConverter fun toRateSource(v: String): RateSource = runCatching { RateSource.valueOf(v) }.getOrDefault(RateSource.SAVED)

    @TypeConverter fun fromDirection(v: DebtDirection): String = v.name
    @TypeConverter fun toDirection(v: String): DebtDirection = DebtDirection.valueOf(v)
}
