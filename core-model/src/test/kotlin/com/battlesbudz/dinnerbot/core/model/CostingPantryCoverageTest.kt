package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pantry-owned priced items (hotfix for the bug Battles reported
 * 2026-10-22: potatoes added to the pantry still showed on the shopping
 * list). An owned pantry entry that matches a priced item by name makes
 * the item already home: no shopping line, $0 cash, portion cost still
 * reported as information, exactly like Sam's stock on hand.
 */
class CostingPantryCoverageTest {

    private val potatoes = PriceItem(
        id = "russet-potatoes", name = "Russet potatoes", category = "Produce",
        homeStore = "aldi", packDescription = "5 lb bag",
        packQuantity = Quantity(5.0, MeasureUnit.POUND), packPriceCents = 349,
        pricePerUnitCents = 69.8, checkedDate = "2026-10-22", source = "test",
    )
    private val eggs = PriceItem(
        id = "eggs", name = "Eggs", category = "Dairy",
        homeStore = "aldi", packDescription = "12 ct",
        packQuantity = Quantity(12.0, MeasureUnit.COUNT), packPriceCents = 185,
        pricePerUnitCents = 15.4, checkedDate = "2026-10-22", source = "test",
    )
    private val spaghetti = PriceItem(
        id = "spaghetti", name = "Spaghetti", category = "Pasta",
        homeStore = "aldi", packDescription = "32 oz",
        packQuantity = Quantity(32.0, MeasureUnit.OUNCE), packPriceCents = 209,
        pricePerUnitCents = 6.53, checkedDate = "2026-10-22", source = "test",
    )
    private val book = listOf(potatoes, eggs, spaghetti)
    private val bookById = book.associateBy { it.id }

    private fun recipe(id: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    /** Potatoes 2 lb (portion 140c, pack 349c) + eggs 6 ct (portion 92c, pack 185c). */
    private val skillet = recipe(
        "potato-egg-skillet",
        priced("russet-potatoes", 2.0, MeasureUnit.POUND),
        priced("eggs", 6.0, MeasureUnit.COUNT),
    )

    // ---- The matching rule -------------------------------------------------

    @Test fun pantryNameMatchesItemNameEnding() {
        assertEquals(
            setOf("russet-potatoes"),
            Costing.pantryCoveredItemIds(setOf("Potatoes"), book),
        )
    }

    @Test fun singularPantryNameMatchesPluralItemName() {
        assertEquals(
            setOf("russet-potatoes"),
            Costing.pantryCoveredItemIds(setOf("Potato"), book),
        )
    }

    @Test fun matchingIgnoresCaseAndMatchesFullNames() {
        assertEquals(
            setOf("russet-potatoes"),
            Costing.pantryCoveredItemIds(setOf("russet POTATOES"), book),
        )
        assertEquals(
            setOf("russet-potatoes"),
            Costing.pantryCoveredItemIds(setOf("Russet potato"), book),
        )
        assertEquals(
            setOf("eggs"),
            Costing.pantryCoveredItemIds(setOf("Eggs"), book),
        )
    }

    @Test fun partialWordNeverMatches() {
        // "Pot" is only part of the word "potatoes": not a whole-word ending.
        assertEquals(emptySet<String>(), Costing.pantryCoveredItemIds(setOf("Pot"), book))
        // "Russet" is the item's first word, not its ending.
        assertEquals(emptySet<String>(), Costing.pantryCoveredItemIds(setOf("Russet"), book))
        // A different kind of potato is not this item.
        assertEquals(emptySet<String>(), Costing.pantryCoveredItemIds(setOf("Sweet potatoes"), book))
    }

    @Test fun nonMatchingStapleCoversNothing() {
        assertEquals(emptySet<String>(), Costing.pantryCoveredItemIds(setOf("Rice"), book))
        assertEquals(emptySet<String>(), Costing.pantryCoveredItemIds(emptySet(), book))
    }

    // ---- The costing behavior ----------------------------------------------

    @Test fun ownedPantryItemLeavesTheListAndCostsNoCash() {
        val covered = Costing.pantryCoveredItemIds(setOf("Potatoes"), book)
        val costed = Costing.costRecipes(listOf(skillet), 4, bookById, emptyMap(), covered)
        assertTrue(costed.lines.none { it.itemId == "russet-potatoes" })
        assertEquals(setOf("russet-potatoes"), costed.pantryCoveredItemIds)
        // Only the eggs pack is bought.
        assertEquals(185, costed.cashNeededCents)
        val eggsLine = costed.lines.single { it.itemId == "eggs" }
        assertEquals(1, eggsLine.packsToBuy)
        assertEquals(185, eggsLine.estimatedPackPriceCents)
        // Portion cost is still reported, like Sam's stock: 140 + 92 = 232.
        assertEquals(232, costed.foodCostCents)
    }

    @Test fun withoutCoverageTheItemIsBoughtAsBefore() {
        // The not-owned state: same recipes, nothing covered, the buy line
        // and its cash are exactly what they were before the fix.
        val costed = Costing.costRecipes(listOf(skillet), 4, bookById, emptyMap())
        val line = costed.lines.single { it.itemId == "russet-potatoes" }
        assertEquals(1, line.packsToBuy)
        assertEquals(349, line.estimatedPackPriceCents)
        assertEquals(349 + 185, costed.cashNeededCents)
        assertTrue(costed.pantryCoveredItemIds.isEmpty())
    }

    @Test fun partialWordStapleChangesNothingInCosting() {
        val covered = Costing.pantryCoveredItemIds(setOf("Pot"), book)
        val costed = Costing.costRecipes(listOf(skillet), 4, bookById, emptyMap(), covered)
        assertEquals(349 + 185, costed.cashNeededCents)
        assertTrue(costed.lines.any { it.itemId == "russet-potatoes" })
    }
}
