package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.core.model.TonightOverrides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase RI-1: tonight-only edits inside the planner. A night's edited
 * list is the version costed, repriced, and projected; the saved recipe
 * and the week's other nights are untouched; and the edits leave with
 * the dinner when the night's dinner is swapped or replaced.
 */
class WeekPlannerTonightEditsTest {

    private fun item(id: String, store: Store, packAmount: Double, unit: MeasureUnit, packPrice: Int) =
        PriceItem(
            id = id, name = id, category = "Test", homeStore = store, packDescription = "pack",
            packQuantity = Quantity(packAmount, unit), packPriceCents = packPrice,
            pricePerUnitCents = packPrice / packAmount,
            checkedDate = "2026-10-23", source = "test",
        )

    private val eggs = item("eggs", Store.ALDI, 12.0, MeasureUnit.COUNT, 185)
    private val spaghetti = item("spaghetti", Store.ALDI, 32.0, MeasureUnit.OUNCE, 209)
    private val broccoli = item("broccoli", Store.ALDI, 16.0, MeasureUnit.OUNCE, 130)
    private val pork = item("pork-tenderloin", Store.SAMS_CLUB, 9.0, MeasureUnit.POUND, 2168)
    private val book = listOf(eggs, spaghetti, broccoli, pork)

    private fun recipe(id: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    /** Spaghetti + broccoli: cash 209 + 130 = 339. The only broccoli user. */
    private val pastaBroccoli = recipe(
        "pasta-broccoli",
        priced("spaghetti", 16.0, MeasureUnit.OUNCE),
        priced("broccoli", 8.0, MeasureUnit.OUNCE),
    )

    /** Eggs only: cash 185. */
    private val eggsOnly = recipe(
        "eggs-only",
        priced("eggs", 6.0, MeasureUnit.COUNT),
    )

    /** A Sam's dinner: 4 lb of the 9 lb pork pack, plus eggs. */
    private val porkDinner = recipe(
        "pork-dinner",
        priced("pork-tenderloin", 4.0, MeasureUnit.POUND),
        priced("eggs", 6.0, MeasureUnit.COUNT),
    )

    private val pool = listOf(pastaBroccoli, eggsOnly, porkDinner)

    private val settings = HouseholdSettings(
        householdSize = 4, cookNights = listOf("MON", "TUE"), weeklyBudgetCents = 100_00,
    )

    private fun weekWith(nights: List<PlannedNight>) = WeekPlanner.WeekPlan(
        weekStart = "2026-10-19", nights = nights, lines = emptyList(),
        cashNeededCents = 0, foodCostCents = 0, samsMonthlySavingsCents = 0,
        samsGateMet = false, deferredRestocks = emptyList(), notes = emptyList(),
    )

    private fun withoutBroccoli(recipe: Recipe) = TonightOverrides(
        recipe.ingredients.filter { it.priceItemId != "broccoli" },
    )

    @Test fun recostPricesTheTonightEditedListAndLeavesTheSavedRecipeAlone() {
        val week = weekWith(
            listOf(
                PlannedNight(
                    day = "MON", recipeId = "pasta-broccoli",
                    tonightEdits = withoutBroccoli(pastaBroccoli),
                ),
                PlannedNight(day = "TUE", recipeId = "eggs-only"),
            ),
        )
        val repriced = WeekPlanner.recost(settings, week, pool, book, emptyMap(), emptySet())
        assertFalse(
            "tonight's broccoli is off the list",
            repriced.lines.any { it.itemId == "broccoli" },
        )
        assertEquals(209 + 185, repriced.cashNeededCents)

        // The same week without the tonight edit buys the broccoli.
        val plain = weekWith(
            listOf(
                PlannedNight(day = "MON", recipeId = "pasta-broccoli"),
                PlannedNight(day = "TUE", recipeId = "eggs-only"),
            ),
        )
        val plainRepriced = WeekPlanner.recost(settings, plain, pool, book, emptyMap(), emptySet())
        assertTrue(plainRepriced.lines.any { it.itemId == "broccoli" })
        assertEquals(209 + 130 + 185, plainRepriced.cashNeededCents)

        // The saved recipe still has its broccoli for tomorrow.
        assertTrue(pastaBroccoli.ingredients.any { it.priceItemId == "broccoli" })
    }

    @Test fun recostOtherNightsAreNotTouchedByOneNightsEdits() {
        val both = listOf(
            PlannedNight(
                day = "MON", recipeId = "pasta-broccoli",
                tonightEdits = withoutBroccoli(pastaBroccoli),
            ),
            PlannedNight(day = "TUE", recipeId = "pasta-broccoli"),
        )
        val repriced = WeekPlanner.recost(
            settings, weekWith(both), pool, book, emptyMap(), emptySet(),
        )
        // Only Tuesday's 8 oz of broccoli remains on the list.
        val broccoliLine = repriced.lines.single { it.itemId == "broccoli" }
        assertEquals(8.0, broccoliLine.needed.amount, 0.001)
        // Spaghetti is still both nights' worth: 32 oz, one 32 oz pack.
        val spaghettiLine = repriced.lines.single { it.itemId == "spaghetti" }
        assertEquals(32.0, spaghettiLine.needed.amount, 0.001)
    }

    @Test fun recostHonorsATonightSubstitution() {
        val substituted = TonightOverrides(
            listOf(
                priced("spaghetti", 16.0, MeasureUnit.OUNCE),
                priced("eggs", 6.0, MeasureUnit.COUNT),
            ),
        )
        val week = weekWith(
            listOf(
                PlannedNight(day = "MON", recipeId = "pasta-broccoli", tonightEdits = substituted),
                PlannedNight(day = "TUE", recipeId = "eggs-only"),
            ),
        )
        val repriced = WeekPlanner.recost(settings, week, pool, book, emptyMap(), emptySet())
        assertFalse(repriced.lines.any { it.itemId == "broccoli" })
        // Eggs: 6 (tonight's substitute) + 6 (Tuesday) = 12 = one pack.
        val eggsLine = repriced.lines.single { it.itemId == "eggs" }
        assertEquals(1, eggsLine.packsToBuy)
        assertEquals(209 + 185, repriced.cashNeededCents)
    }

    @Test fun swapClearsTheSwappedNightsEditsAndKeepsTheOtherNights() {
        val generated = WeekPlanner.generate(
            settings, pool, book, emptyMap(), emptySet(), "2026-10-19",
        )
        assertEquals(2, generated.nights.size)
        val edited = generated.copy(
            nights = generated.nights.map {
                it.copy(
                    tonightEdits = TonightOverrides(
                        pool.first { r -> r.id == it.recipeId }.ingredients,
                    ),
                )
            },
        )
        val first = edited.nights[0]
        val swapped = WeekPlanner.swapDinner(
            settings, edited, first.day, pool, book, emptyMap(), emptySet(),
        )
        val swappedNight = swapped.nights.first { it.day == first.day }
        assertTrue("the dinner changed", swappedNight.recipeId != first.recipeId)
        assertNull("the edits leave with the old dinner", swappedNight.tonightEdits)
        val other = swapped.nights.first { it.day != first.day }
        assertTrue("the other night keeps its edits", other.tonightEdits != null)
    }

    @Test fun nightOffThenBackOnClearsTheNightsEdits() {
        val generated = WeekPlanner.generate(
            settings, pool, book, emptyMap(), emptySet(), "2026-10-19",
        )
        val day = generated.nights[0].day
        val edited = generated.copy(
            nights = generated.nights.map {
                if (it.day == day) {
                    it.copy(tonightEdits = TonightOverrides(
                        pool.first { r -> r.id == it.recipeId }.ingredients,
                    ))
                } else {
                    it
                }
            },
        )
        val off = WeekPlanner.setNightCooking(
            settings, edited, day, cooking = false, reason = "leftovers",
            recipes = pool, priceItems = book, stockOnHand = emptyMap(),
            pantryOwnedNames = emptySet(),
        )
        assertTrue(off.nights.first { it.day == day }.notCooking)
        val backOn = WeekPlanner.setNightCooking(
            settings, off, day, cooking = true,
            recipes = pool, priceItems = book, stockOnHand = emptyMap(),
            pantryOwnedNames = emptySet(),
        )
        val restored = backOn.nights.first { it.day == day }
        assertFalse(restored.notCooking)
        assertNull("a replacement dinner starts with no tonight edits", restored.tonightEdits)
    }

    @Test fun stockProjectionUsesTheTonightEditedDinner() {
        val editedWeek = weekWith(
            listOf(
                PlannedNight(
                    day = "MON", recipeId = "pork-dinner",
                    tonightEdits = TonightOverrides(
                        porkDinner.ingredients.filter { it.priceItemId != "pork-tenderloin" },
                    ),
                ),
            ),
        )
        val projected = WeekPlanner.projectedStockAfter(
            settings, editedWeek, pool, book, emptyMap(), emptySet(),
        )
        assertFalse(
            "no pork is bought for a dinner edited to not use it",
            projected.containsKey("pork-tenderloin"),
        )

        val plainWeek = weekWith(listOf(PlannedNight(day = "MON", recipeId = "pork-dinner")))
        val plainProjected = WeekPlanner.projectedStockAfter(
            settings, plainWeek, pool, book, emptyMap(), emptySet(),
        )
        // One 9 lb pack bought, 4 lb used: 5 lb left in the projection.
        assertEquals(5.0, plainProjected.getValue("pork-tenderloin").amount, 0.001)
    }
}
