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
            homeStore = Store.SAMS_CLUB, packDescription = "about 9 lb",
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
}
