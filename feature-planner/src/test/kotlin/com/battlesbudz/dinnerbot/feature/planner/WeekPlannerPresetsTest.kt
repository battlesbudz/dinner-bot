package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.DietPreset
import com.battlesbudz.dinnerbot.core.model.DietPresets
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeAdaptation
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase V2-2: the app wiring for presets, exercised at planner level the
 * same way MainActivity composes it: presets join the won't-eat list before
 * adaptation, then the selected presets' tag rules narrow the adapted pool.
 */
class WeekPlannerPresetsTest {

    private fun item(id: String, name: String, price: Int, pack: Quantity) = PriceItem(
        id = id, name = name, category = "Test", homeStore = Store.ALDI,
        packDescription = "pack", packQuantity = pack,
        packPriceCents = price, pricePerUnitCents = price / pack.amount,
        checkedDate = "2026-10-22", source = "test",
    )

    private val eggs = item("eggs", "Eggs", 185, Quantity.count(12.0))
    private val cheddar = item("cheddar", "Shredded cheddar cheese", 195, Quantity(8.0, MeasureUnit.OUNCE))
    private val turkey = item("ground-turkey", "Ground turkey", 198, Quantity.count(12.0))
    private val book = listOf(eggs, cheddar, turkey)
    private val itemsById = book.associateBy { it.id }

    private fun recipe(id: String, tags: List<String>, vararg ingredients: RecipeIngredient) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", dietTags = tags,
        ingredients = ingredients.toList(), steps = listOf("Cook."),
    )

    private val cheeseOmelet = recipe(
        "cheese-omelet", listOf("Vegetarian"),
        RecipeIngredient(priceItemId = "cheddar", quantity = Quantity(4.0, MeasureUnit.OUNCE)),
        RecipeIngredient(priceItemId = "eggs", quantity = Quantity.count(6.0)),
    )
    private val plainEggs = recipe(
        "plain-eggs", listOf("Vegetarian"),
        RecipeIngredient(priceItemId = "eggs", quantity = Quantity.count(6.0)),
    )
    private val turkeyDinner = recipe(
        "turkey-dinner", emptyList(),
        RecipeIngredient(priceItemId = "ground-turkey", quantity = Quantity.count(12.0)),
    )
    private val library = listOf(cheeseOmelet, plainEggs, turkeyDinner)

    private val dairyFree = DietPreset(
        id = "dairy-free", name = "Dairy-free", summary = "s",
        wontEatEntries = listOf("cheese"),
    )
    private val vegetarian = DietPreset(
        id = "vegetarian", name = "Vegetarian", summary = "s", requiredTag = "Vegetarian",
    )
    private val presets = listOf(dairyFree, vegetarian)

    private fun poolFor(settings: HouseholdSettings): List<Recipe> {
        val adapted = RecipeAdaptation.adapt(
            library, DietPresets.effectiveWontEat(settings, presets), itemsById,
        )
        return DietPresets.applyTagFilters(adapted.plannable, DietPresets.selected(settings, presets))
    }

    private fun settings(presetIds: List<String>) = HouseholdSettings(
        householdSize = 4, cookNights = listOf("MON", "TUE"),
        weeklyBudgetCents = 100_00, dietPresets = presetIds,
    )

    @Test fun dairyFreePresetBlocksTheCheeseDinnerFromGeneration() {
        val household = settings(listOf("dairy-free"))
        val plan = WeekPlanner.generate(
            household, poolFor(household), book, emptyMap(), emptySet(), "2026-10-19",
        )
        assertEquals(setOf("plain-eggs", "turkey-dinner"), plan.nights.map { it.recipeId }.toSet())
    }

    @Test fun vegetarianPresetGeneratesOnlyVegetarianTaggedDinners() {
        val household = settings(listOf("vegetarian"))
        val plan = WeekPlanner.generate(
            household, poolFor(household), book, emptyMap(), emptySet(), "2026-10-19",
        )
        assertEquals(setOf("cheese-omelet", "plain-eggs"), plan.nights.map { it.recipeId }.toSet())
    }

    @Test fun noPresetGeneratesFromTheWholeLibrary() {
        val household = settings(emptyList())
        val plan = WeekPlanner.generate(
            household, poolFor(household), book, emptyMap(), emptySet(), "2026-10-19",
        )
        // Cheapest two of the full library, nothing filtered out.
        assertEquals(setOf("plain-eggs", "cheese-omelet"), plan.nights.map { it.recipeId }.toSet())
    }

    @Test fun householdWontEatStillAppliesAlongsideAPreset() {
        val household = HouseholdSettings(
            householdSize = 4, cookNights = listOf("MON", "TUE"),
            weeklyBudgetCents = 100_00, wontEat = listOf("turkey"),
            dietPresets = listOf("dairy-free"),
        )
        val plan = WeekPlanner.generate(
            household, poolFor(household), book, emptyMap(), emptySet(), "2026-10-19",
        )
        // Turkey is out from the household's own list AND cheese is out from
        // the preset: only the plain egg dinner remains plannable.
        assertEquals(listOf("plain-eggs"), plan.nights.map { it.recipeId })
    }
}
