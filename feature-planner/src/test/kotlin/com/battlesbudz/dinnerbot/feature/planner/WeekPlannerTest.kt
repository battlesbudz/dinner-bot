package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.core.model.StorePrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPlannerTest {

    private fun item(
        id: String, store: Store, packAmount: Double, unit: MeasureUnit,
        packPrice: Int, perUnit: Double? = null, alternates: List<StorePrice> = emptyList(),
    ) = PriceItem(
        id = id, name = id, category = "Test", homeStore = store, packDescription = "pack",
        packQuantity = Quantity(packAmount, unit), packPriceCents = packPrice,
        pricePerUnitCents = perUnit ?: packPrice / packAmount,
        checkedDate = "2026-10-22", source = "test", alternates = alternates,
    )

    private val turkey = item("ground-turkey", Store.WALMART, 1.0, MeasureUnit.POUND, 198)
    private val tenderloin = item(
        "pork-tenderloin", Store.SAMS_CLUB, 9.0, MeasureUnit.POUND, 2168,
        perUnit = 240.9, alternates = listOf(StorePrice(Store.ALDI, 297.0)),
    )
    private val spaghetti = item("spaghetti", Store.ALDI, 32.0, MeasureUnit.OUNCE, 209, perUnit = 6.53)
    private val eggs = item("eggs", Store.ALDI, 12.0, MeasureUnit.COUNT, 185, perUnit = 15.4)
    private val book = listOf(turkey, tenderloin, spaghetti, eggs)

    private fun recipe(id: String, title: String, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = title, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private fun priced(itemId: String, amount: Double, unit: MeasureUnit) =
        RecipeIngredient(priceItemId = itemId, quantity = Quantity(amount, unit))

    /** Cheap dinner: one pack of eggs + half a pack of spaghetti = 394c cash. */
    private fun cheapDinner(id: String) = recipe(
        id, id,
        priced("eggs", 6.0, MeasureUnit.COUNT),
        priced("spaghetti", 16.0, MeasureUnit.OUNCE),
    )

    private val porkFeast = recipe(
        "pork-feast", "Pork Feast",
        priced("pork-tenderloin", 7.0, MeasureUnit.POUND),
    )
    private val porkSkillet = recipe(
        "pork-skillet", "Pork Skillet",
        priced("pork-tenderloin", 1.25, MeasureUnit.POUND),
        priced("eggs", 6.0, MeasureUnit.COUNT),
    )
    private val turkeyDinner = recipe(
        "turkey-dinner", "Turkey Dinner",
        priced("ground-turkey", 1.0, MeasureUnit.POUND),
        priced("spaghetti", 16.0, MeasureUnit.OUNCE),
    )

    private fun settings(budget: Int, nights: List<String>) = HouseholdSettings(
        householdSize = 4, cookNights = nights, weeklyBudgetCents = budget,
    )

    @Test fun planFillsEveryCookNightAndStaysUnderCap() {
        val recipes = listOf(cheapDinner("c1"), cheapDinner("c2"), cheapDinner("c3"),
            cheapDinner("c4"), cheapDinner("c5"), cheapDinner("c6"))
        val plan = WeekPlanner.generate(
            settings(100_00, listOf("MON", "TUE", "WED", "THU", "FRI")),
            recipes, book, emptyMap(), emptySet(), "2026-10-19",
        )
        assertEquals(5, plan.nights.size)
        assertTrue(plan.cashNeededCents <= 100_00)
        assertTrue(plan.cashNeededCents > 0)
    }

    @Test fun stockedBulkDinnersArePreferredAndCostNoCashForTheBulkItem() {
        val stock = mapOf("pork-tenderloin" to Quantity.pounds(9.0))
        val plan = WeekPlanner.generate(
            settings(100_00, listOf("MON")),
            listOf(turkeyDinner, porkSkillet), book, stock, emptySet(), "2026-10-19",
        )
        assertEquals("pork-skillet", plan.nights.single().recipeId)
        assertTrue(plan.lines.none { it.itemId == "pork-tenderloin" })
    }

    @Test fun restockThatCannotFitIsDeferredAndNamed() {
        // Ranked cheapest-first the feast is last; variation 1 rotates it in.
        val recipes = listOf(
            cheapDinner("c1"), cheapDinner("c2"), cheapDinner("c3"),
            cheapDinner("c4"), cheapDinner("c5"), porkFeast,
        )
        val plan = WeekPlanner.generate(
            settings(2500, listOf("MON", "TUE", "WED", "THU", "FRI")),
            recipes, book, emptyMap(), emptySet(), "2026-10-19", variation = 1,
        )
        assertTrue("deferred: ${plan.deferredRestocks}", plan.deferredRestocks.contains("pork-tenderloin"))
        assertTrue(plan.notes.any { it.contains("Pork tenderloin") || it.contains("pork-tenderloin") })
        assertTrue("cash ${plan.cashNeededCents} <= 2500", plan.cashNeededCents <= 2500)
        assertTrue(plan.nights.none { it.recipeId == "pork-feast" })
    }

    @Test fun generatedWeekNeverRepeatsAcrossManyVariations() {
        val recipes = (1..8).map { cheapDinner("c$it") }
        val nights = listOf("MON", "TUE", "WED", "THU", "FRI")
        for (variation in 0..40) {
            val plan = WeekPlanner.generate(
                settings(100_00, nights), recipes, book, emptyMap(), emptySet(),
                "2026-10-19", variation = variation,
            )
            assertEquals("variation $variation fills the week", 5, plan.nights.size)
            assertEquals(
                "variation $variation repeats a dinner: ${plan.nights.map { it.recipeId }}",
                plan.nights.size, plan.nights.map { it.recipeId }.distinct().size,
            )
        }
    }

    @Test fun poolSmallerThanCookNightsPlansWhatItCanAndSaysSo() {
        val recipes = listOf(cheapDinner("c1"), cheapDinner("c2"), cheapDinner("c3"))
        val plan = WeekPlanner.generate(
            settings(100_00, listOf("MON", "TUE", "WED", "THU", "FRI")),
            recipes, book, emptyMap(), emptySet(), "2026-10-19",
        )
        assertEquals(3, plan.nights.size)
        assertEquals(3, plan.nights.map { it.recipeId }.distinct().size)
        assertTrue(
            "notes should say plainly the week could not be filled: ${plan.notes}",
            plan.notes.any { it.contains("not enough different dinners") },
        )
    }

    @Test fun v010RegressionOnionWontEatNoRepeatsAndSwapWorks() {
        // The exact v0.1.0 failure: most recipes share one removable
        // ingredient (onion). The old code filtered those recipes out whole,
        // leaving fewer dinners than cook nights: generation wrapped the
        // short list into repeats and swap had no candidates left at all.
        val onionBook = book + item("yellow-onions", Store.ALDI, 3.0, MeasureUnit.POUND, 150)
        val recipes = (1..6).map { i ->
            recipe(
                "d$i", "Dinner $i",
                priced("eggs", 6.0, MeasureUnit.COUNT),
                priced("spaghetti", 16.0, MeasureUnit.OUNCE),
                RecipeIngredient(
                    priceItemId = "yellow-onions",
                    quantity = Quantity(0.5, MeasureUnit.POUND),
                    essential = false,
                ),
            )
        }
        val s = settings(100_00, listOf("MON", "TUE", "WED", "THU", "FRI"))
            .copy(wontEat = listOf("onion"))
        val adapted = com.battlesbudz.dinnerbot.core.model.RecipeAdaptation
            .adapt(recipes, s.wontEat, onionBook.associateBy { it.id })
        assertTrue(adapted.blockedFromPlanning.isEmpty())
        val plan = WeekPlanner.generate(
            s, adapted.plannable, onionBook, emptyMap(), emptySet(), "2026-10-19",
        )
        assertEquals(5, plan.nights.size)
        assertEquals(5, plan.nights.map { it.recipeId }.distinct().size)
        assertTrue(plan.lines.none { it.itemId == "yellow-onions" })
        val firstNight = plan.nights.first()
        val swapped = WeekPlanner.swapDinner(
            s, plan, firstNight.day, adapted.plannable, onionBook, emptyMap(), emptySet(),
        )
        assertTrue(
            "swap must change the dinner",
            swapped.nights.first { it.day == firstNight.day }.recipeId != firstNight.recipeId,
        )
    }

    @Test fun essentialBlockedRecipeNeverAppearsInGeneratedWeeks() {
        val eggBook = book
        val eggRecipe = recipe(
            "egg-night", "Egg Night",
            RecipeIngredient(
                priceItemId = "eggs",
                quantity = Quantity(6.0, MeasureUnit.COUNT),
                essential = true,
            ),
        )
        val others = (1..5).map { cheapDinner("c$it") }
        val s = settings(100_00, listOf("MON", "TUE", "WED", "THU", "FRI"))
            .copy(wontEat = listOf("eggs"))
        val adapted = com.battlesbudz.dinnerbot.core.model.RecipeAdaptation
            .adapt(others + eggRecipe, s.wontEat, eggBook.associateBy { it.id })
        assertEquals(setOf("egg-night"), adapted.blockedFromPlanning)
        for (variation in 0..10) {
            val plan = WeekPlanner.generate(
                s, adapted.plannable, eggBook, emptyMap(), emptySet(),
                "2026-10-19", variation = variation,
            )
            assertTrue(plan.nights.none { it.recipeId == "egg-night" })
        }
    }

    @Test fun swapChangesTheDinnerAndStaysUnderCap() {
        val recipes = listOf(cheapDinner("c1"), cheapDinner("c2"), cheapDinner("c3"), turkeyDinner)
        val s = settings(100_00, listOf("MON", "TUE"))
        val plan = WeekPlanner.generate(s, recipes, book, emptyMap(), emptySet(), "2026-10-19")
        val firstNight = plan.nights.first()
        val swapped = WeekPlanner.swapDinner(
            s, plan, firstNight.day, recipes, book, emptyMap(), emptySet(),
        )
        assertTrue(swapped.nights.first { it.day == firstNight.day }.recipeId != firstNight.recipeId)
        assertTrue(swapped.cashNeededCents <= 100_00)
    }

    @Test fun cashAndFoodCostAreSeparateNumbers() {
        // A Sam's buy week: cash is the full pack, food cost is only what dinners use.
        val recipes = listOf(porkFeast, cheapDinner("c1"))
        val plan = WeekPlanner.generate(
            settings(100_00, listOf("MON", "TUE")), recipes, book, emptyMap(), emptySet(), "2026-10-19",
        )
        assertTrue(plan.cashNeededCents >= 2168)
        assertTrue("food ${plan.foodCostCents} < cash ${plan.cashNeededCents}",
            plan.foodCostCents < plan.cashNeededCents)
    }
}
