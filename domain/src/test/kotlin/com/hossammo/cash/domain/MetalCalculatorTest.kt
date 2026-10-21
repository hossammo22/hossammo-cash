package com.hossammo.cash.domain

import com.hossammo.cash.domain.calc.MetalCalculator
import com.hossammo.cash.domain.calc.MetalType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MetalCalculatorTest {
    private fun bd(s: String) = BigDecimal(s)

    @Test fun goldPound21kValue() {
        // جنيه ذهب: 8 جرام عيار 21 وسعر جرام 24 = 4800 => جرام 21 = 4200 => 33600
        val v = MetalCalculator.value(MetalType.GOLD, MetalCalculator.GOLD_POUND_GRAMS, 21, 1, bd("4800"))
        assertEquals(bd("33600.00"), v)
    }

    @Test fun goldBar24kMultipleBars() {
        val v = MetalCalculator.value(MetalType.GOLD, bd("10"), 24, 3, bd("5000"))
        assertEquals(bd("150000.00"), v)
    }

    @Test fun silverBar250g999() {
        val v = MetalCalculator.value(MetalType.SILVER, bd("250"), 999, 1, bd("60"))
        assertEquals(bd("14985.00"), v)
    }

    @Test fun gramPriceFromOunce() {
        val g = MetalCalculator.gramPriceFromOunce(bd("31.1034768"))
        assertEquals(0, g.compareTo(BigDecimal.ONE))
    }

    @Test fun manual21kPriceRoundTrip() {
        val pure = MetalCalculator.purePriceFrom(MetalType.GOLD, 21, bd("4200"))
        assertEquals(bd("4800.00"), MetalCalculator.gramPriceAt(MetalType.GOLD, 24, pure))
        assertEquals(bd("3600.00"), MetalCalculator.gramPriceAt(MetalType.GOLD, 18, pure))
    }

    @Test fun pureWeight() {
        assertEquals(bd("7.000"), MetalCalculator.pureWeight(MetalType.GOLD, bd("8"), 21, 1))
    }

    @Test fun zeroCountIsZero() {
        assertEquals(bd("0.00"), MetalCalculator.value(MetalType.GOLD, bd("8"), 21, 0, bd("4800")))
    }
}
