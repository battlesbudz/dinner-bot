package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase RI-3 at the planner level: the one-unpriced-imported cap on
 * generated weeks (and on swaps), the disclaimer bookkeeping on the
 * plan, and uncrossed lines surviving a recost.
 */
class WeekPlannerRi3Test {

    private fun item(id: String, name: String, packPrice: Int) = PriceItem(
        id = id, name = name, category = "Test", homeStore = Store.ALDI,
        packDescription = "pack", packQuantity = Quantity(1.0, MeasureUnit.POUND),
        packPriceCents = packPrice, pricePerUnitCents = packPrice.toDouble(),
        checkedDate = "2026-10-24", source = "test",
    )

    private val eggs = item("eggs", "Eggs", 185)
    private val rice = item("rice", "Rice", 210)
    private val potatoes = PriceItem(
        id = "russet-potatoes", name = "Russet potatoes", category = "Produce",
        homeStore = Store.ALDI, packDescription = "5 lb bag",
        packQuantity = Quantity(5.0, MeasureUnit.POUND), packPriceCents = 349,
        pricePerUnitCents = 69.8, checkedDate = "2026-10-24", source = "test",
    )
    private val book = listOf(eggs, rice, potatoes)

    private fun priced(itemId: String, amount: Double = 1.0) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, MeasureUnit.POUND))

    private fun asWritten(name: String) =
        RecipeIngredient(pantryItem = name, quantity = Quantity(1.0, MeasureUnit.POUND))

    private fun recipe(id: String, origin: RecipeOrigin, vararg ingredients: RecipeIngredient) =
        Recipe(
            id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
            mainProtein = "Test", ingredients = ingredients.toList(),
            steps = listOf("Cook."), origin = origin,
        )

    private val importA = recipe("import-a", RecipeOrigin.IMPORTED, asWritten("Saffron"))
    private val importB = recipe("import-b", RecipeOrigin.IMPORTED, asWritten("Truffle oil"))
    private val libEggs = recipe("lib-eggs", RecipeOrigin.LIBRARY, priced("eggs"))
    private val libRice = recipe("lib-rice", RecipeOrigin.LIBRARY, priced("rice"))
    private val libEggs2 = recipe("lib-eggs-2", RecipeOrigin.LIBRARY, priced("eggs"))
    private val libRice2 = recipe("lib-rice-2", RecipeOrigin.LIBRARY, priced("rice"))

    private fun settings(nights: List<String>) = HouseholdSettings(
        householdSize = 4, cookNights = nights, weeklyBudgetCents = 100_00,
    )

    @Test fun generatedWeekCarriesAtMostOneUnpricedImport() {
        val plan = WeekPlanner.generate(
            settings = settings(listOf("MON", "TUE", "WED")),
            recipes = listOf(importA, importB, libEggs, libRice),
            priceItems = book,
            stockOnHand = emptyMap(),
            pantryOwnedNames = emptySet(),
            weekStart = "2026-10-26",
        )
        val planned = plan.nights.map { it.recipeId }
        assertEquals(3, planned.size)
        val unpricedPlanned = planned.filter { it == "import-a" || it == "import-b" }
        assertEquals(1, unpricedPlanned.size)
        assertEquals(unpricedPlanned.toSet(), plan.unpricedImportedRecipeIds)
        // The displaced dinner is named in a note, and waits.
        val displaced = if (unpricedPlanned.single() == "import-a") "import-b" else "import-a"
        assertTrue(plan.notes.any { it.contains(displaced) && it.contains("waits for another week") })
        assertFalse(planned.contains(displaced))
    }

    @Test fun eachWeekOfAFortnightCarriesAtMostOneUnpricedImport() {
        val fortnight = WeekPlanner.generateFortnight(
            settings = settings(listOf("MON", "TUE")),
            recipes = listOf(importA, importB, libEggs, libRice, libEggs2, libRice2),
            priceItems = book,
            stockOnHand = emptyMap(),
            pantryOwnedNames = emptySet(),
            week1Start = "2026-10-26",
            week2Start = "2026-11-02",
        )
        assertTrue(fortnight.week1.unpricedImportedRecipeIds.size <= 1)
        assertTrue(fortnight.week2.unpricedImportedRecipeIds.size <= 1)
        assertEquals(
            fortnight.week1.nights.count { it.recipeId in fortnight.week1.unpricedImportedRecipeIds },
            fortnight.week1.unpricedImportedRecipeIds.size,
        )
    }

    @Test fun pricedWeekHasNoUnpricedBookkeeping() {
        val plan = WeekPlanner.generate(
            settings = settings(listOf("MON", "TUE")),
            recipes = listOf(libEggs, libRice),
            priceItems = book,
            stockOnHand = emptyMap(),
            pantryOwnedNames = emptySet(),
            weekStart = "2026-10-26",
        )
        assertTrue(plan.unpricedImportedRecipeIds.isEmpty())
    }

    @Test fun swapCannotBringInASecondUnpricedImport() {
        val base = WeekPlanner.generate(
            settings = settings(listOf("MON", "TUE")),
            recipes = listOf(importA, libEggs),
            priceItems = book,
            stockOnHand = emptyMap(),
            pantryOwnedNames = emptySet(),
            weekStart = "2026-10-26",
        )
        assertEquals(setOf("import-a"), base.unpricedImportedRecipeIds)
        val tueDay = base.nights.first { it.recipeId == "lib-eggs" }.day
        val swapped = WeekPlanner.swapDinner(
            settings = settings(listOf("MON", "TUE")),
            current = base,
            day = tueDay,
            recipes = listOf(importA, importB, libEggs, libRice),
            priceItems = book,
            stockOnHand = emptyMap(),
            pantryOwnedNames = emptySet(),
            candidatePool = listOf(importA, importB, libEggs, libRice),
        )
        val newTue = swapped.nights.first { it.day == tueDay }.recipeId
        assertFalse(newTue == "import-b")
        assertEquals("lib-rice", newTue)
    }

    @Test fun recostKeepsAnUncrossedCoveredItemBought() {
        val potatoDinner = recipe(
            "potato-dinner", RecipeOrigin.LIBRARY,
            priced("russet-potatoes", 2.0), priced("eggs"),
        )
        val s = settings(listOf("MON"))
        val plan = WeekPlanner.generate(
            settings = s,
            recipes = listOf(potatoDinner),
            priceItems = book,
            stockOnHand = emptyMap(),
            pantryOwnedNames = setOf("Potatoes"),
            weekStart = "2026-10-26",
        )
        // The generated list shows the covered potatoes, crossed out, $0.
        val covered = plan.lines.single { it.itemId == "russet-potatoes" }
        assertEquals("Potatoes", covered.coveredByPantryName)
        assertEquals(185, plan.cashNeededCents)

        // Uncross: an explicit recost buys them at the book price.
        val uncrossed = WeekPlanner.recost(
            settings = s,
            current = plan,
            recipes = listOf(potatoDinner),
            priceItems = book,
            stockOnHand = emptyMap(),
            pantryOwnedNames = setOf("Potatoes"),
            uncrossedItemIds = setOf("russet-potatoes"),
        )
        val bought = uncrossed.lines.single { it.itemId == "russet-potatoes" }
        assertTrue(bought.uncrossed)
        assertEquals(1, bought.packsToBuy)
        assertEquals(349 + 185, uncrossed.cashNeededCents)

        // A later plain recost derives the uncrossed set from the
        // current lines' flags, so the buy survives repricing.
        val repriced = WeekPlanner.recost(
            settings = s,
            current = uncrossed,
            recipes = listOf(potatoDinner),
            priceItems = book,
            stockOnHand = emptyMap(),
            pantryOwnedNames = setOf("Potatoes"),
        )
        val stillBought = repriced.lines.single { it.itemId == "russet-potatoes" }
        assertTrue(stillBought.uncrossed)
        assertEquals(349 + 185, repriced.cashNeededCents)
    }
}
