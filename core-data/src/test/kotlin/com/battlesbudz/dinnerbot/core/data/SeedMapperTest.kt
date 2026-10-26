package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Test

class SeedMapperTest {
    @Test fun priceItemRoundTrips() {
        val item = PriceItem(
            id = "pork-tenderloin", name = "Pork tenderloin", category = "Meat",
            homeStore = "sams-club", packDescription = "about 9 lb",
            packQuantity = Quantity.pounds(9.0), packPriceCents = 2168,
            pricePerUnitCents = 240.9, checkedDate = "2026-10-22", source = "Battles",
        )
        val back = with(SeedMapper) { item.toEntity().toModel() }
        assertEquals(item, back)
    }

    @Test fun recipeRoundTrips() {
        val recipe = Recipe(
            id = "r", title = "T", servings = 4, activeMinutes = 5, cuisine = "Asian",
            mainProtein = "Egg", dietTags = listOf("Vegetarian"),
            ingredients = listOf(
                RecipeIngredient(priceItemId = "eggs", quantity = Quantity.count(6.0)),
                RecipeIngredient(pantryItem = "Soy sauce", quantity = Quantity(3.0, MeasureUnit.TABLESPOON)),
            ),
            steps = listOf("One.", "Two."),
        )
        val back = with(SeedMapper) { recipe.toEntity().toModel() }
        assertEquals(recipe, back)
    }

    @Test fun settingsRoundTrips() {
        val settings = HouseholdSettings(
            householdSize = 5, cookNights = listOf("MON", "WED"),
            weeklyBudgetCents = 10000, wontEat = listOf("mushrooms"),
            dietNotes = "no beef", pantryStaples = listOf("Rice"), onboardingComplete = true,
        )
        val back = with(SeedMapper) { settings.toEntity().toModel() }
        assertEquals(settings, back)
    }

    @Test fun priceItemFreezeNoteRoundTrips() {
        val item = PriceItem(
            id = "pork-tenderloin", name = "Pork tenderloin", category = "Meat",
            homeStore = "sams-club", packDescription = "about 9 lb",
            packQuantity = Quantity.pounds(9.0), packPriceCents = 2168,
            pricePerUnitCents = 240.9, checkedDate = "2026-10-22", source = "Battles",
            freezeNote = "Cut into meal-size portions, bag flat, and freeze.",
        )
        val back = with(SeedMapper) { item.toEntity().toModel() }
        assertEquals(item, back)
        assertEquals("Cut into meal-size portions, bag flat, and freeze.", back.freezeNote)
    }

    @Test fun settingsDietPresetsRoundTrip() {
        val settings = HouseholdSettings(
            householdSize = 4, cookNights = listOf("MON"),
            weeklyBudgetCents = 10000, dietPresets = listOf("dairy-free", "vegetarian"),
        )
        val back = with(SeedMapper) { settings.toEntity().toModel() }
        assertEquals(settings, back)
        assertEquals(listOf("dairy-free", "vegetarian"), back.dietPresets)
    }

    @Test fun priceItemGuessMarkRoundTrips() {
        // The guess mark is stored state (Battles's correction,
        // 2026-10-24): it must survive the entity round trip exactly.
        val item = PriceItem(
            id = "user-white-onion", name = "White onion", category = "Produce",
            homeStore = "aldi", packDescription = "2 lb (from a recipe)",
            packQuantity = Quantity.pounds(2.0), packPriceCents = 139,
            pricePerUnitCents = 69.5, checkedDate = "2026-10-24",
            source = "Recipe import — guessed price",
            guessPriced = true,
        )
        val back = with(SeedMapper) { item.toEntity().toModel() }
        assertEquals(item, back)
        assertEquals(true, back.guessPriced)
    }
}
