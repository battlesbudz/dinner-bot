package com.battlesbudz.dinnerbot.feature.pantry

import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryLogicTest {

    private val tenderloin = PriceItem(
        id = "pork-tenderloin", name = "Pork tenderloin", category = "Meat",
        homeStore = "sams-club", packDescription = "9 lb",
        packQuantity = Quantity(9.0, MeasureUnit.POUND), packPriceCents = 2168,
        pricePerUnitCents = 240.9, checkedDate = "2026-10-22", source = "test",
        freezeNote = "Portioned into 1.25 lb bags.",
    )
    private val turkey = tenderloin.copy(
        id = "ground-turkey", name = "Ground turkey", homeStore = "walmart",
        packQuantity = Quantity(1.0, MeasureUnit.POUND), freezeNote = null,
    )

    @Test fun toggleFlipsOwned() {
        val items = listOf(PantryItem("salt", "Salt", owned = true))
        val toggled = PantryLogic.toggle(items, "salt")
        assertFalse(toggled.single().owned)
        assertTrue(PantryLogic.toggle(toggled, "salt").single().owned)
    }

    @Test fun slugAndNewStaple() {
        assertEquals("olive-oil", PantryLogic.slug("Olive oil"))
        val staple = PantryLogic.newStaple("  Taco seasoning ")
        assertEquals("taco-seasoning", staple.id)
        assertEquals("Taco seasoning", staple.name)
        assertTrue(staple.owned)
    }

    @Test fun stockSteppersUseQuarterPoundsAndClampAtZero() {
        val up = PantryLogic.adjustStock(tenderloin, Quantity.pounds(1.0), +1)
        assertEquals(1.25, up.onHand.amount, 0.001)
        val down = PantryLogic.adjustStock(tenderloin, Quantity.pounds(0.25), -2)
        assertEquals(0.0, down.onHand.amount, 0.001)
        val fromNothing = PantryLogic.adjustStock(tenderloin, null, +4)
        assertEquals(1.0, fromNothing.onHand.amount, 0.001)
    }

    @Test fun bulkItemsAreFreezeNoteItemsOrStockedItems() {
        // Phase C1: bulk is a property of the item (its freeze note) or of
        // its tracked stock, never of the store it comes from.
        assertEquals(listOf(tenderloin), PantryLogic.bulkItems(listOf(tenderloin, turkey)))
        val stockedTurkey = com.battlesbudz.dinnerbot.core.model.BulkStock(
            "ground-turkey", Quantity.pounds(2.0),
        )
        assertEquals(
            listOf(turkey),
            PantryLogic.bulkItems(listOf(turkey), listOf(stockedTurkey)),
        )
    }
}
