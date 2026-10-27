package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase RI-3: the weekly list's per-line state (covered by which pantry
 * entry, uncrossed for the week, the placeholder guess) rides the
 * stored grocery rows, so it must round-trip the mapper exactly — and
 * rows written before RI-3 (null/false defaults) read back plain.
 */
class PlanMapperRi3Test {

    @Test fun coveredLineRoundTripsThroughEntity() {
        val line = GroceryLine(
            itemId = "russet-potatoes",
            displayName = "Russet potatoes",
            store = "aldi",
            needed = Quantity(2.0, MeasureUnit.POUND),
            packsToBuy = 0,
            estimatedPackPriceCents = 349,
            portionCostCents = 140,
            coveredByPantryName = "Potatoes",
        )
        val entity = with(PlanMapper) { line.toEntity("plan-1") }
        assertEquals("Potatoes", entity.coveredByPantryName)
        assertFalse(entity.uncrossed)
        assertNull(entity.priceGuessCents)
        assertEquals(line, with(PlanMapper) { entity.toModel() })
    }

    @Test fun uncrossedGuessLineRoundTripsThroughEntity() {
        val line = GroceryLine(
            itemId = "aswritten:white-onion",
            displayName = "White onion",
            store = "aldi",
            needed = Quantity(1.0, MeasureUnit.POUND),
            packsToBuy = 1,
            estimatedPackPriceCents = 0,
            portionCostCents = null,
            uncrossed = true,
            priceGuessCents = 139,
        )
        val entity = with(PlanMapper) { line.toEntity("plan-1") }
        assertEquals(true, entity.uncrossed)
        assertEquals(139, entity.priceGuessCents)
        assertNull(entity.coveredByPantryName)
        assertEquals(line, with(PlanMapper) { entity.toModel() })
    }

    @Test fun entityDefaultsReadBackAsAPlainLine() {
        val entity = GroceryItemEntity(
            planId = "plan-1", itemId = "eggs", displayName = "Eggs",
            store = "ALDI", neededAmount = 6.0, neededUnit = "COUNT",
            packsToBuy = 1, packPriceCents = 185,
            portionCostCents = 92, checkedOff = false,
        )
        val line = with(PlanMapper) { entity.toModel() }
        assertNull(line.coveredByPantryName)
        assertFalse(line.uncrossed)
        assertNull(line.priceGuessCents)
    }
}
