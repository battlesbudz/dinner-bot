package com.battlesbudz.dinnerbot.feature.grocerylist

import com.battlesbudz.dinnerbot.core.model.MealPlan
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryListBuilderTest {

    private fun item(id: String, store: Store, packAmount: Double, unit: MeasureUnit, packPrice: Int) =
        PriceItem(
            id = id, name = id, category = "Test", homeStore = store, packDescription = "pack",
            packQuantity = Quantity(packAmount, unit), packPriceCents = packPrice,
            pricePerUnitCents = packPrice / packAmount, checkedDate = "2026-10-22", source = "test",
        )

    private val turkey = item("ground-turkey", Store.WALMART, 1.0, MeasureUnit.POUND, 198)
    private val tenderloin = item("pork-tenderloin", Store.SAMS_CLUB, 9.0, MeasureUnit.POUND, 2168)
    private val spaghetti = item("spaghetti", Store.ALDI, 32.0, MeasureUnit.OUNCE, 209)
    private val sauce = item("pasta-sauce", Store.ALDI, 24.0, MeasureUnit.OUNCE, 185)
    private val eggs = item("eggs", Store.ALDI, 12.0, MeasureUnit.COUNT, 185)
    private val book = listOf(turkey, tenderloin, spaghetti, sauce, eggs)

    private fun recipe(id: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    private val spaghettiNight = recipe(
        "spaghetti-night",
        priced("ground-turkey", 1.0, MeasureUnit.POUND),
        priced("spaghetti", 16.0, MeasureUnit.OUNCE),
        priced("pasta-sauce", 24.0, MeasureUnit.OUNCE),
        RecipeIngredient(pantryItem = "Salt", quantity = Quantity(1.0, MeasureUnit.TEASPOON)),
    )
    private val eggNight = recipe(
        "egg-night",
        priced("eggs", 6.0, MeasureUnit.COUNT),
        priced("spaghetti", 16.0, MeasureUnit.OUNCE),
    )
    private val porkNight = recipe(
        "pork-night",
        priced("pork-tenderloin", 1.25, MeasureUnit.POUND),
    )

    private fun planOf(vararg recipeIds: String) = MealPlan(
        id = "p1", weekStart = "2026-10-19", budgetCents = 100_00,
        nights = recipeIds.mapIndexed { i, id -> PlannedNight("D$i", id) },
    )

    private val recipesById = listOf(spaghettiNight, eggNight, porkNight).associateBy { it.id }

    @Test fun listSplitsByStoreWithSubtotalsAndTotal() {
        val built = GroceryListBuilder.build(
            planOf("spaghetti-night", "egg-night"), recipesById, book, emptyMap(), servings = 4,
        )
        // The spaghetti dinner's as-written Salt line has no book item
        // in this fixture and no pantry cover was passed, so Phase RI-3
        // keeps it on the list as a guess line: the whole-book median
        // of 185, 185, 198, 209, 2168 is 198, shelved at Aldi.
        val salt = built.lines.single { it.displayName.contains("Salt") }
        assertEquals(198, salt.priceGuessCents)
        // Aldi: spaghetti 32 oz total = 1 pack 209, sauce 185, eggs 185 -> 579, plus the salt guess.
        assertEquals(579 + 198, built.cashByStoreCents[Store.ALDI])
        // Walmart: turkey 1 lb = 198.
        assertEquals(198, built.cashByStoreCents[Store.WALMART])
        assertEquals(579 + 198 + 198, built.totalCashCents)
    }

    @Test fun scalingDoublesPackCounts() {
        val built = GroceryListBuilder.build(
            planOf("spaghetti-night", "egg-night"), recipesById, book, emptyMap(), servings = 8,
        )
        val spaghettiLine = built.lines.single { it.itemId == "spaghetti" }
        assertEquals(2, spaghettiLine.packsToBuy)
        val turkeyLine = built.lines.single { it.itemId == "ground-turkey" }
        assertEquals(2, turkeyLine.packsToBuy)
    }

    @Test fun bulkStockSubtractsFromTheList() {
        val stock = mapOf("pork-tenderloin" to Quantity.pounds(9.0))
        val built = GroceryListBuilder.build(
            planOf("pork-night"), recipesById, book, stock, servings = 4,
        )
        assertTrue(built.lines.none { it.itemId == "pork-tenderloin" })
        assertEquals(0, built.totalCashCents)
    }

    // ---- Pantry-owned priced items (hotfix, Battles 2026-10-22) ------------
    // The reported bug: "Potatoes" added to the pantry, and the list still
    // said to buy russet potatoes. An owned pantry entry matching a priced
    // item means the item is already home: $0 cash. Phase RI-3 changed
    // only the presentation: the item now stays ON the list as a covered
    // line (crossed out), instead of leaving it.

    private val russetPotatoes = PriceItem(
        id = "russet-potatoes", name = "Russet potatoes", category = "Produce",
        homeStore = Store.ALDI, packDescription = "5 lb bag",
        packQuantity = Quantity(5.0, MeasureUnit.POUND), packPriceCents = 349,
        pricePerUnitCents = 69.8, checkedDate = "2026-10-22", source = "test",
    )
    private val potatoNight = recipe(
        "potato-night",
        priced("russet-potatoes", 2.0, MeasureUnit.POUND),
        priced("eggs", 6.0, MeasureUnit.COUNT),
    )
    private val bookWithPotatoes = book + russetPotatoes
    private val recipesWithPotatoes = recipesById + ("potato-night" to potatoNight)

    @Test fun ownedPotatoesStayOnTheListCrossedOut() {
        val built = GroceryListBuilder.build(
            planOf("potato-night"), recipesWithPotatoes, bookWithPotatoes, emptyMap(),
            servings = 4, pantryOwnedNames = setOf("Potatoes"),
        )
        val covered = built.lines.single { it.itemId == "russet-potatoes" }
        assertEquals("Potatoes", covered.coveredByPantryName)
        assertEquals(0, covered.packsToBuy)
        assertEquals(0, GroceryListBuilder.lineCashCents(covered))
        // The eggs are unaffected: one pack at 185, all of the cash.
        assertEquals(185, built.totalCashCents)
        assertEquals(185, built.cashByStoreCents[Store.ALDI])
        assertEquals(setOf("eggs", "russet-potatoes"), built.lines.map { it.itemId }.toSet())
    }

    @Test fun singularPotatoEntryAlsoCovers() {
        val built = GroceryListBuilder.build(
            planOf("potato-night"), recipesWithPotatoes, bookWithPotatoes, emptyMap(),
            servings = 4, pantryOwnedNames = setOf("Potato"),
        )
        val covered = built.lines.single { it.itemId == "russet-potatoes" }
        assertEquals("Potato", covered.coveredByPantryName)
        assertEquals(185, built.totalCashCents)
    }

    @Test fun potatoesNotOwnedAreBoughtAgain() {
        val built = GroceryListBuilder.build(
            planOf("potato-night"), recipesWithPotatoes, bookWithPotatoes, emptyMap(),
            servings = 4, pantryOwnedNames = emptySet(),
        )
        val line = built.lines.single { it.itemId == "russet-potatoes" }
        assertEquals(1, line.packsToBuy)
        assertEquals(349 + 185, built.totalCashCents)
    }

    @Test fun partialWordStapleDoesNotSuppressTheItem() {
        val built = GroceryListBuilder.build(
            planOf("potato-night"), recipesWithPotatoes, bookWithPotatoes, emptyMap(),
            servings = 4, pantryOwnedNames = setOf("Pot"),
        )
        assertTrue(built.lines.any { it.itemId == "russet-potatoes" })
        assertEquals(349 + 185, built.totalCashCents)
    }

    @Test fun unrelatedStapleChangesNothing() {
        val built = GroceryListBuilder.build(
            planOf("potato-night"), recipesWithPotatoes, bookWithPotatoes, emptyMap(),
            servings = 4, pantryOwnedNames = setOf("Rice"),
        )
        assertTrue(built.lines.any { it.itemId == "russet-potatoes" })
        assertEquals(349 + 185, built.totalCashCents)
    }
}
