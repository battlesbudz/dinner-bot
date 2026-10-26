package com.battlesbudz.dinnerbot.feature.barcode

import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.6.3: the conservative OFF quantity reader. The formats are the
 * real ones OFF publishes; anything the reader cannot state honestly
 * must come back null so the form's pack fields stay manual.
 */
class OffPackParserTest {

    @Test fun plainGrams() {
        val pack = OffPackParser.parse("500 g", null, null, null)
        assertEquals(500.0, pack.amount!!, 0.001)
        assertEquals(MeasureUnit.GRAM, pack.unit)
        assertEquals("500 g", pack.description)
    }

    @Test fun plainLiters() {
        val pack = OffPackParser.parse("1 L", null, null, null)
        assertEquals(1.0, pack.amount!!, 0.001)
        assertEquals(MeasureUnit.LITER, pack.unit)
    }

    @Test fun multipackTotalsItsPacks() {
        val pack = OffPackParser.parse("12 x 355 ml", null, null, null)
        assertEquals(4260.0, pack.amount!!, 0.001)
        assertEquals(MeasureUnit.MILLILITER, pack.unit)
        assertEquals("12 x 355 ml", pack.description)
    }

    @Test fun netWeightPrefersTheParenthesizedMetric() {
        val pack = OffPackParser.parse("NET WT 16 OZ (453g)", null, null, null)
        assertEquals(453.0, pack.amount!!, 0.001)
        assertEquals(MeasureUnit.GRAM, pack.unit)
    }

    @Test fun structuredQuantityWinsOverText() {
        val pack = OffPackParser.parse("about a pound", 500.0, "g", null)
        assertEquals(500.0, pack.amount!!, 0.001)
        assertEquals(MeasureUnit.GRAM, pack.unit)
    }

    @Test fun structuredQuantityBuildsADescriptionWithoutText() {
        val pack = OffPackParser.parse(null, 500.0, "g", null)
        assertEquals("500 g", pack.description)
        assertEquals(500.0, pack.amount!!, 0.001)
    }

    @Test fun servingFoldsIntoTheDescription() {
        val pack = OffPackParser.parse("500 g", 500.0, "g", "80 g")
        assertEquals("500 g · serving 80 g", pack.description)
    }

    @Test fun unparseableLeavesEverythingToManualEntry() {
        val pack = OffPackParser.parse("family size", null, null, null)
        assertNull(pack.amount)
        assertNull(pack.unit)
        // The words themselves still suggest the description field.
        assertEquals("family size", pack.description)
    }

    @Test fun nothingAtAllParsesToNothing() {
        val pack = OffPackParser.parse(null, null, null, null)
        assertNull(pack.amount)
        assertNull(pack.unit)
        assertNull(pack.description)
    }

    @Test fun unknownStructuredUnitFallsBackToTheText() {
        val pack = OffPackParser.parse("750 ml", 0.75, "cl-ish", null)
        assertEquals(750.0, pack.amount!!, 0.001)
        assertEquals(MeasureUnit.MILLILITER, pack.unit)
    }

    @Test fun fluidOuncesReadAsFluidOunces() {
        val pack = OffPackParser.parse("16 fl oz", null, null, null)
        assertEquals(16.0, pack.amount!!, 0.001)
        assertEquals(MeasureUnit.FLUID_OUNCE, pack.unit)
    }
}
