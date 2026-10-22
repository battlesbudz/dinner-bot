package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase V2-2: diet presets compose with household rules, never over them. */
class DietPresetsTest {

    private val dairyFree = DietPreset(
        id = "dairy-free", name = "Dairy-free", summary = "s",
        wontEatEntries = listOf("cheese", "milk"),
    )
    private val vegetarian = DietPreset(
        id = "vegetarian", name = "Vegetarian", summary = "s",
        requiredTag = "Vegetarian",
    )
    private val noFishSauce = DietPreset(
        id = "no-fish-sauce", name = "No fish sauce", summary = "s",
        excludedTags = listOf("Fish sauce"),
    )
    private val all = listOf(dairyFree, vegetarian, noFishSauce)

    private fun settings(wontEat: List<String> = emptyList(), presets: List<String> = emptyList()) =
        HouseholdSettings(
            householdSize = 4, cookNights = listOf("MON"),
            wontEat = wontEat, dietPresets = presets,
        )

    private fun recipe(id: String, tags: List<String> = emptyList()) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10, cuisine = "Test",
        mainProtein = "Test", dietTags = tags,
        ingredients = listOf(
            RecipeIngredient(priceItemId = "eggs", quantity = Quantity.count(6.0)),
        ),
        steps = listOf("Cook."),
    )

    @Test fun selectedPresetsResolveByIdInDefinitionOrder() {
        val picked = DietPresets.selected(settings(presets = listOf("vegetarian", "dairy-free")), all)
        assertEquals(listOf("dairy-free", "vegetarian"), picked.map { it.id })
    }

    @Test fun unknownPresetIdInSettingsIsIgnored() {
        val picked = DietPresets.selected(settings(presets = listOf("not-a-preset")), all)
        assertTrue(picked.isEmpty())
    }

    @Test fun effectiveWontEatUnionsHouseholdFirstThenPresets() {
        val merged = DietPresets.effectiveWontEat(
            settings(wontEat = listOf("Cilantro", "cheese"), presets = listOf("dairy-free")), all,
        )
        // Household entries keep their place and their spelling; the preset's
        // duplicate "cheese" does not repeat, and its "milk" is appended.
        assertEquals(listOf("Cilantro", "cheese", "milk"), merged)
    }

    @Test fun presetsNeverRemoveHouseholdEntries() {
        val merged = DietPresets.effectiveWontEat(
            settings(wontEat = listOf("mushrooms", "olives"), presets = listOf("dairy-free", "vegetarian")), all,
        )
        assertTrue(merged.containsAll(listOf("mushrooms", "olives")))
    }

    @Test fun noPresetsSelectedLeavesWontEatUntouched() {
        val merged = DietPresets.effectiveWontEat(settings(wontEat = listOf("peanuts")), all)
        assertEquals(listOf("peanuts"), merged)
    }

    @Test fun requiredTagKeepsOnlyTaggedRecipes() {
        val picked = listOf(vegetarian)
        assertTrue(DietPresets.tagEligible(recipe("a", listOf("Vegetarian")), picked))
        assertFalse(DietPresets.tagEligible(recipe("b"), picked))
    }

    @Test fun excludedTagBlocksTaggedRecipes() {
        val picked = listOf(noFishSauce)
        assertFalse(DietPresets.tagEligible(recipe("a", listOf("Fish sauce")), picked))
        assertTrue(DietPresets.tagEligible(recipe("b", listOf("Vegetarian")), picked))
    }

    @Test fun multipleRequiredTagsMustAllBePresent() {
        val glutenFree = DietPreset(
            id = "gf", name = "GF", summary = "s", requiredTag = "Gluten-free",
        )
        val picked = listOf(vegetarian, glutenFree)
        assertTrue(DietPresets.tagEligible(recipe("a", listOf("Vegetarian", "Gluten-free")), picked))
        assertFalse(DietPresets.tagEligible(recipe("b", listOf("Vegetarian")), picked))
        assertFalse(DietPresets.tagEligible(recipe("c", listOf("Gluten-free")), picked))
    }

    @Test fun tagMatchingIgnoresCase() {
        assertTrue(DietPresets.tagEligible(recipe("a", listOf("vegetarian")), listOf(vegetarian)))
    }

    @Test fun presetWontEatEntriesFlowThroughAdaptationEssentialAndRemovable() {
        val cheddar = PriceItem(
            id = "cheddar", name = "Shredded cheddar cheese", category = "Dairy",
            homeStore = Store.ALDI, packDescription = "8 oz bag",
            packQuantity = Quantity(8.0, MeasureUnit.OUNCE), packPriceCents = 195,
            pricePerUnitCents = 24.4, checkedDate = "2026-10-22", source = "test",
        )
        val eggs = PriceItem(
            id = "eggs", name = "Eggs", category = "Dairy",
            homeStore = Store.ALDI, packDescription = "12 ct",
            packQuantity = Quantity.count(12.0), packPriceCents = 185,
            pricePerUnitCents = 15.4, checkedDate = "2026-10-22", source = "test",
        )
        val itemsById = mapOf("cheddar" to cheddar, "eggs" to eggs)
        val needsCheese = recipe("needs-cheese").copy(
            ingredients = listOf(
                RecipeIngredient(priceItemId = "cheddar", quantity = Quantity(4.0, MeasureUnit.OUNCE)),
                RecipeIngredient(priceItemId = "eggs", quantity = Quantity.count(6.0)),
            ),
        )
        val optionalCheese = recipe("optional-cheese").copy(
            ingredients = listOf(
                RecipeIngredient(
                    priceItemId = "cheddar", quantity = Quantity(2.0, MeasureUnit.OUNCE),
                    essential = false,
                ),
                RecipeIngredient(priceItemId = "eggs", quantity = Quantity.count(6.0)),
            ),
        )
        val household = settings(presets = listOf("dairy-free"))
        val result = RecipeAdaptation.adapt(
            listOf(needsCheese, optionalCheese),
            DietPresets.effectiveWontEat(household, all),
            itemsById,
        )
        // The preset's "cheese" entry behaves exactly like a household entry:
        // essential cheese blocks the dish, removable cheese is stripped.
        assertEquals(setOf("needs-cheese"), result.blockedFromPlanning)
        assertEquals(listOf("optional-cheese"), result.plannable.map { it.id })
        assertTrue(result.plannable.single().ingredients.none { it.priceItemId == "cheddar" })
    }

    @Test fun applyTagFiltersNarrowsThePool() {
        val pool = listOf(recipe("a", listOf("Vegetarian")), recipe("b"), recipe("c", listOf("Vegetarian")))
        val kept = DietPresets.applyTagFilters(pool, listOf(vegetarian))
        assertEquals(listOf("a", "c"), kept.map { it.id })
    }
}
