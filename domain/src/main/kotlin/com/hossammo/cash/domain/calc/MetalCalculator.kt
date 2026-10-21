package com.hossammo.cash.domain.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** نوع المعدن الثمين. */
enum class MetalType { GOLD, SILVER }

/** شكل القطعة. GOLD_POUND = الجنيه الذهب المصري (8 جرام عيار 21). */
enum class MetalForm { BAR, GOLD_POUND, COIN, JEWELRY, OTHER }

/**
 * حسابات الذهب والفضة بدقة BigDecimal.
 * - العيار للذهب بالقيراط (24، 22، 21، 18، 14)، والنقاء = العيار ÷ 24.
 * - النقاء للفضة بالألف (999، 925، 900، 800)، والنقاء = القيمة ÷ 1000.
 * - السعر المرجعي دائمًا لجرام المعدن الخالص (ذهب 24 / فضة 999).
 */
object MetalCalculator {
    val GRAMS_PER_TROY_OUNCE: BigDecimal = BigDecimal("31.1034768")
    val GOLD_POUND_GRAMS: BigDecimal = BigDecimal("8")
    const val GOLD_POUND_KARAT = 21
    val GOLD_KARATS = listOf(24, 22, 21, 18, 14, 12)
    val SILVER_FINENESS = listOf(999, 958, 925, 900, 800)
    private val MC = MathContext(20, RoundingMode.HALF_UP)

    fun defaultPurity(type: MetalType): Int = if (type == MetalType.GOLD) 21 else 999

    /** نسبة النقاء (0..1). */
    fun purityFraction(type: MetalType, purity: Int): BigDecimal {
        require(purity > 0) { "purity" }
        return when (type) {
            MetalType.GOLD -> BigDecimal(purity.coerceAtMost(24)).divide(BigDecimal(24), MC)
            MetalType.SILVER -> BigDecimal(purity.coerceAtMost(1000)).divide(BigDecimal(1000), MC)
        }
    }

    /** سعر جرام الخالص من سعر الأوقية. */
    fun gramPriceFromOunce(ouncePrice: BigDecimal): BigDecimal = ouncePrice.divide(GRAMS_PER_TROY_OUNCE, MC)

    /** سعر جرام بعيار معين من سعر جرام الخالص. */
    fun gramPriceAt(type: MetalType, purity: Int, purePrice: BigDecimal): BigDecimal =
        purePrice.multiply(purityFraction(type, purity), MC).setScale(2, RoundingMode.HALF_UP)

    /** سعر جرام الخالص من سعر جرام بعيار معين (مثل إدخال سعر عيار 21 يدويًا). */
    fun purePriceFrom(type: MetalType, purity: Int, priceAtPurity: BigDecimal): BigDecimal =
        priceAtPurity.divide(purityFraction(type, purity), MC)

    /** القيمة الحالية = العدد × الوزن × النقاء × سعر جرام الخالص (مقربة لقرشين). */
    fun value(type: MetalType, weightGrams: BigDecimal, purity: Int, count: Int, purePricePerGram: BigDecimal): BigDecimal {
        require(weightGrams.signum() >= 0 && count >= 0) { "weight/count" }
        return weightGrams.multiply(BigDecimal(count), MC)
            .multiply(purityFraction(type, purity), MC)
            .multiply(purePricePerGram, MC)
            .setScale(2, RoundingMode.HALF_UP)
    }

    /** الوزن الخالص بالجرام. */
    fun pureWeight(type: MetalType, weightGrams: BigDecimal, purity: Int, count: Int): BigDecimal =
        weightGrams.multiply(BigDecimal(count), MC).multiply(purityFraction(type, purity), MC).setScale(3, RoundingMode.HALF_UP)
}
