package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UnitsTest {
    @Test fun poundsToOunces() {
        assertEquals(32.0, Quantity.pounds(2.0).convertTo(MeasureUnit.OUNCE).amount, 0.001)
    }

    @Test fun cupsToTablespoons() {
        assertEquals(16.0, Quantity.cups(1.0).convertTo(MeasureUnit.TABLESPOON).amount, 0.001)
    }

    @Test fun kilogramsToPounds() {
        assertEquals(2.2046, Quantity(1.0, MeasureUnit.KILOGRAM).convertTo(MeasureUnit.POUND).amount, 0.01)
    }

    @Test fun crossDimensionConversionFails() {
        assertThrows(IllegalArgumentException::class.java) {
            Quantity.pounds(1.0).convertTo(MeasureUnit.CUP)
        }
    }

    @Test fun additionConvertsUnits() {
        val total = Quantity.pounds(1.0) + Quantity.ounces(8.0)
        assertEquals(1.5, total.amount, 0.001)
        assertEquals(MeasureUnit.POUND, total.unit)
    }
}
