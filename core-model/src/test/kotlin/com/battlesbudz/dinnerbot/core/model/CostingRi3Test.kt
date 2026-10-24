package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weekly list shows everything (Phase RI-3): pantry-covered items
 * stay on the list as $0 covered lines naming the covering entry,
 * uncrossed items cost as buy lines, and unpriced as-written
 * ingredients carry labeled placeholder guesses that count in the
 * week's cash. Money-only callers see exactly what they saw before.
 */
class CostingRi3Test {

    private fun item(
        id: String, name: String, packAmount: Double, unit: MeasureUnit,
        packPrice: Int, perUnit: Double,
    ) = PriceItem(
        id = id, name = name, category = "Test", homeStore = Store.ALDI,
        packDescription = "pack", packQuantity = Quantity(packAmount, unit),
        packPriceCents = packPrice, pricePerUnitCents = perUnit,
        checkedDate = "2026-10-24", source = "test",
    )

    private val potatoes = item("russet-potatoes", "Russet potatoes", 5.0, MeasureUnit.POUND, 349, 69.8)
    private val eggs = item("eggs", "Eggs", 12.0, MeasureUnit.COUNT, 185, 15.4)
    private val yellowOnion = item("yellow-onion", "Yellow onion", 1.0, MeasureUnit.POUND, 129, 129.0)
    private val redOnion = item("red-onion", "Red onion", 1.0, MeasureUnit.POUND, 149, 149.0)
    private val book = listOf(potatoes, eggs, yellowOnion, redOnion).associateBy { it.id }

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    private fun asWritten(name: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(pantryItem = name, quantity = Quantity(amount, unit))

    private fun recipe(id: String, origin: RecipeOrigin, vararg ingredients: RecipeIngredient) =
        Recipe(
            id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
            mainProtein = "Test", ingredients = ingredients.toList(),
            steps = listOf("Cook."), origin = origin,
        )

    private val potatoDinner = recipe(
        "potato-dinner", RecipeOrigin.LIBRARY,
        priced("russet-potatoes", 2.0, MeasureUnit.POUND),
        priced("eggs", 6.0, MeasureUnit.COUNT),
    )

    @Test fun coveredItemStaysOnTheListAsAZeroCashLineNamingTheCover() {
        val costed = Costing.costRecipes(
            listOf(potatoDinner), 4, book, emptyMap(),
            pantryCoveredItemIds = setOf("russet-potatoes"),
            pantryOwnedNames = setOf("Potatoes"),
            forList = true,
        )
        val covered = costed.lines.single { it.itemId == "russet-potatoes" }
        assertEquals("Potatoes", covered.coveredByPantryName)
        assertEquals(0, covered.packsToBuy)
        assertEquals(349, covered.estimatedPackPriceCents)
        // The portion still counts in the food cost; only the eggs cost cash.
        assertEquals(185, costed.cashNeededCents)
        assertTrue(costed.foodCostCents >= 140)
        assertTrue(costed.lines.any { it.itemId == "eggs" && it.coveredByPantryName == null })
    }

    @Test fun moneyOnlyCallersGetNoCoveredLines() {
        val costed = Costing.costRecipes(
            listOf(potatoDinner), 4, book, emptyMap(),
            pantryCoveredItemIds = setOf("russet-potatoes"),
            forList = true,
        )
        assertTrue(costed.lines.none { it.itemId == "russet-potatoes" })
        assertEquals(185, costed.cashNeededCents)
    }

    @Test fun uncrossedCoveredItemCostsAsAFlaggedBuyLine() {
        val costed = Costing.costRecipes(
            listOf(potatoDinner), 4, book, emptyMap(),
            pantryCoveredItemIds = setOf("russet-potatoes"),
            pantryOwnedNames = setOf("Potatoes"),
            uncrossedItemIds = setOf("russet-potatoes"),
            forList = true,
        )
        val line = costed.lines.single { it.itemId == "russet-potatoes" }
        assertNull(line.coveredByPantryName)
        assertTrue(line.uncrossed)
        assertEquals(1, line.packsToBuy)
        assertEquals(349 + 185, costed.cashNeededCents)
    }

    @Test fun unpricedIngredientGetsALabeledGuessLineCountedInCash() {
        val imported = recipe(
            "imported", RecipeOrigin.IMPORTED,
            priced("eggs", 6.0, MeasureUnit.COUNT),
            asWritten("White onion", 1.0, MeasureUnit.POUND),
        )
        val costed = Costing.costRecipes(listOf(imported), 4, book, emptyMap(), forList = true)
        val guess = costed.lines.single { it.itemId == "aswritten:white-onion" }
        // Median of the two onions in the book: (129 + 149 + 1) / 2.
        assertEquals(139, guess.priceGuessCents)
        assertEquals(0, guess.estimatedPackPriceCents)
        assertEquals(1, guess.packsToBuy)
        assertEquals(185 + 139, costed.cashNeededCents)
        assertEquals(139, costed.guessTotalCents)
    }

    @Test fun unpricedIngredientStaysInvisibleToMoneyOnlyCallers() {
        val imported = recipe(
            "imported", RecipeOrigin.IMPORTED,
            asWritten("White onion", 1.0, MeasureUnit.POUND),
        )
        val costed = Costing.costRecipes(listOf(imported), 4, book, emptyMap())
        assertTrue(costed.lines.isEmpty())
        assertEquals(0, costed.cashNeededCents)
        assertEquals(0, costed.guessTotalCents)
    }

    @Test fun asWrittenIngredientCoveredByPantryNameIsACoveredLine() {
        val imported = recipe(
            "imported", RecipeOrigin.IMPORTED,
            asWritten("Russet potatoes", 2.0, MeasureUnit.POUND),
        )
        val costed = Costing.costRecipes(
            listOf(imported), 4, book, emptyMap(),
            pantryOwnedNames = setOf("Potatoes"),
            forList = true,
        )
        val line = costed.lines.single()
        assertEquals("Potatoes", line.coveredByPantryName)
        assertEquals("Russet potatoes", line.displayName)
        assertNull(line.priceGuessCents)
        assertEquals(0, costed.cashNeededCents)
    }

    @Test fun guessIsReplacedTheMomentTheBookPricesTheName() {
        val imported = recipe(
            "imported", RecipeOrigin.IMPORTED,
            asWritten("White onion", 1.0, MeasureUnit.POUND),
        )
        val grown = book + ("white-onion" to
            item("white-onion", "White onion", 1.0, MeasureUnit.POUND, 139, 139.0))
        val costed = Costing.costRecipes(listOf(imported), 4, grown, emptyMap(), forList = true)
        val line = costed.lines.single()
        assertEquals("white-onion", line.itemId)
        assertNull(line.priceGuessCents)
        assertEquals(139, line.estimatedPackPriceCents)
        assertEquals(139, costed.cashNeededCents)
        assertEquals(0, costed.guessTotalCents)
        assertFalse(costed.lines.any { it.itemId.startsWith("aswritten:") })
    }

    @Test fun twoDinnersSharingAnUnpricedNameShareOneGuessLine() {
        val a = recipe("a", RecipeOrigin.IMPORTED, asWritten("Saffron", 0.5, MeasureUnit.OUNCE))
        val b = recipe("b", RecipeOrigin.IMPORTED, asWritten("Saffron", 0.5, MeasureUnit.OUNCE))
        val costed = Costing.costRecipes(listOf(a, b), 4, book, emptyMap(), forList = true)
        val lines = costed.lines.filter { it.itemId == "aswritten:saffron" }
        assertEquals(1, lines.size)
        // Whole-book median of 349, 185, 129, 149 -> (149 + 185 + 1) / 2 = 167.
        assertEquals(167, lines.single().priceGuessCents)
        assertEquals(167, costed.guessTotalCents)
        assertEquals(167, costed.cashNeededCents)
    }
}
