package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CostingTest {

    private fun item(
        id: String,
        store: Store,
        packAmount: Double,
        unit: MeasureUnit,
        packPrice: Int,
        perUnit: Double? = null,
        alternates: List<StorePrice> = emptyList(),
    ) = PriceItem(
        id = id,
        name = id,
        category = "Test",
        homeStore = store,
        packDescription = "pack",
        packQuantity = Quantity(packAmount, unit),
        packPriceCents = packPrice,
        pricePerUnitCents = perUnit ?: packPrice / packAmount,
        checkedDate = "2026-10-22",
        source = "test",
        alternates = alternates,
    )

    private val turkey = item("ground-turkey", Store.WALMART, 1.0, MeasureUnit.POUND, 198)
    private val beef = item("ground-beef", Store.WALMART, 1.0, MeasureUnit.POUND, 500)
    private val tenderloin = item(
        "pork-tenderloin", Store.SAMS_CLUB, 9.0, MeasureUnit.POUND, 2168,
        perUnit = 240.9,
        alternates = listOf(StorePrice(Store.ALDI, 297.0), StorePrice(Store.WALMART, 417.0)),
    )
    private val spaghetti = item("spaghetti", Store.ALDI, 32.0, MeasureUnit.OUNCE, 209, perUnit = 6.53)
    private val eggs = item("eggs", Store.ALDI, 12.0, MeasureUnit.COUNT, 185, perUnit = 15.4)

    private fun recipe(id: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    private val book = listOf(turkey, beef, tenderloin, spaghetti, eggs).associateBy { it.id }

    @Test fun groundBeefIsAlwaysSubstitutedWithTurkey() {
        assertEquals("ground-turkey", Costing.substituteItemId("ground-beef"))
        val dinner = recipe("meatloaf", priced("ground-beef", 1.0, MeasureUnit.POUND))
        val costed = Costing.costRecipes(listOf(dinner), 4, book, emptyMap())
        assertEquals(1, costed.lines.size)
        assertEquals("ground-turkey", costed.lines[0].itemId)
        assertEquals(198, costed.cashNeededCents)
    }

    @Test fun bulkStockCoversNeedAtZeroCash() {
        val dinner = recipe("pork-skillet", priced("pork-tenderloin", 1.25, MeasureUnit.POUND))
        val stock = mapOf("pork-tenderloin" to Quantity.pounds(9.0))
        val costed = Costing.costRecipes(listOf(dinner), 4, book, stock)
        assertTrue(costed.lines.none { it.itemId == "pork-tenderloin" })
        assertEquals(0, costed.cashNeededCents)
        assertTrue(costed.stockCoveredItemIds.contains("pork-tenderloin"))
        // Portion cost is still reported as information: 1.25 lb x 240.9c.
        assertEquals(301, costed.foodCostCents)
    }

    @Test fun samsPackChargesFullPriceInBuyWeekWhenGateIsMet() {
        // 7 lb used this week -> 28 lb/month x (297 - 240.9)c = $15.71 >= $15 gate.
        val dinner = recipe("pork-feast", priced("pork-tenderloin", 7.0, MeasureUnit.POUND))
        val costed = Costing.costRecipes(listOf(dinner), 4, book, emptyMap())
        assertTrue(costed.gate.useSams)
        val line = costed.lines.single { it.itemId == "pork-tenderloin" }
        assertEquals(Store.SAMS_CLUB, line.store)
        assertEquals(1, line.packsToBuy)
        assertEquals(2168, costed.cashNeededCents)
    }

    @Test fun gateOffReroutesSamsItemToCheapestRegularStore() {
        // 1.25 lb this week -> 5 lb/month x 56.1c = $2.80 < $15 gate.
        val dinner = recipe("pork-skillet", priced("pork-tenderloin", 1.25, MeasureUnit.POUND))
        val costed = Costing.costRecipes(listOf(dinner), 4, book, emptyMap())
        assertFalse(costed.gate.useSams)
        // 5 lb/month x 56.1c = about $2.80.
        assertTrue(costed.gate.projectedMonthlySavingsCents in 278..283)
        val line = costed.lines.single { it.itemId == "pork-tenderloin" }
        assertEquals(Store.ALDI, line.store)
        // Bought by weight at Aldi's $2.97/lb: 1.25 x 297 = 371c.
        assertEquals(371, costed.cashNeededCents)
    }

    @Test fun partialStockCoverBuysOnlyTheRemainder() {
        // Gate-met usage (7 lb) with 5 lb on hand: still buys one full pack.
        val dinner = recipe("pork-feast", priced("pork-tenderloin", 7.0, MeasureUnit.POUND))
        val stock = mapOf("pork-tenderloin" to Quantity.pounds(5.0))
        val costed = Costing.costRecipes(listOf(dinner), 4, book, stock)
        assertTrue(costed.stockCoveredItemIds.contains("pork-tenderloin"))
        val line = costed.lines.single { it.itemId == "pork-tenderloin" }
        assertEquals(Store.SAMS_CLUB, line.store)
        assertEquals(2168, line.estimatedPackPriceCents)
    }

    @Test fun packsRoundUpAndScalingMultipliesNeeds() {
        val dinner = recipe(
            "pasta-night",
            priced("spaghetti", 16.0, MeasureUnit.OUNCE),
            priced("eggs", 6.0, MeasureUnit.COUNT),
        )
        val forFour = Costing.costRecipes(listOf(dinner), 4, book, emptyMap())
        assertEquals(209 + 185, forFour.cashNeededCents)
        val forEight = Costing.costRecipes(listOf(dinner), 8, book, emptyMap())
        // 32 oz spaghetti = 1 pack still; 12 eggs = 1 pack still.
        assertEquals(209 + 185, forEight.cashNeededCents)
        val doubleDinner = Costing.costRecipes(listOf(dinner, dinner), 8, book, emptyMap())
        // 64 oz spaghetti = 2 packs; 24 eggs = 2 packs.
        assertEquals(2 * 209 + 2 * 185, doubleDinner.cashNeededCents)
    }

    @Test fun pantryIngredientsNeverProduceLines() {
        val dinner = recipe(
            "rice-night",
            RecipeIngredient(pantryItem = "Long grain white rice", quantity = Quantity.cups(2.0)),
            priced("eggs", 6.0, MeasureUnit.COUNT),
        )
        val (needs, pantry) = Costing.aggregateNeeds(listOf(dinner), 4, book)
        assertEquals(listOf("Long grain white rice"), pantry)
        assertEquals(listOf("eggs"), needs.map { it.item.id })
    }
}
