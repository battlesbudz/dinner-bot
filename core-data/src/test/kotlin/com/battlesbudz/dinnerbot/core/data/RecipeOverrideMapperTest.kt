package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOverride
import com.battlesbudz.dinnerbot.core.model.TonightOverrides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase RI-1: the override row round-trips through its entity, and a
 * plan's nights JSON carries tonight-only edits without changing shape
 * for plans written before the field existed.
 */
class RecipeOverrideMapperTest {

    private val herList = listOf(
        RecipeIngredient(
            priceItemId = "ground-turkey", quantity = Quantity(2.0, MeasureUnit.POUND),
            note = "her way", essential = true,
        ),
        RecipeIngredient(
            pantryItem = "Soy sauce", quantity = Quantity(1.0, MeasureUnit.TABLESPOON),
            essential = false,
        ),
    )

    @Test fun overrideRoundTripsThroughItsEntity() {
        val override = RecipeOverride("turkey-spaghetti", herList, updatedAtMillis = 1234L)
        val entity = with(SeedMapper) { override.toEntity() }
        assertEquals("turkey-spaghetti", entity.recipeId)
        assertEquals(1234L, entity.updatedAtMillis)
        val back = with(SeedMapper) { entity.toModel() }
        assertEquals(override, back)
    }

    @Test fun planNightsRoundTripTonightEdits() {
        val stored = PlanMapper.StoredPlan(
            id = "plan-1", weekStart = "2026-10-19", budgetCents = 8000,
            cashNeededCents = 100, foodCostCents = 100, samsMonthlySavingsCents = 0,
            nights = listOf(
                PlannedNight(
                    day = "MON", recipeId = "turkey-spaghetti",
                    tonightEdits = TonightOverrides(herList),
                ),
                PlannedNight(day = "TUE", recipeId = "egg-fried-rice"),
            ),
            notes = emptyList(), createdAtMillis = 7L,
        )
        val entity = with(PlanMapper) { stored.toEntity() }
        val back = with(PlanMapper) { entity.toStoredPlan() }
        assertEquals(herList, back.nights[0].tonightEdits?.ingredients)
        assertNull(back.nights[1].tonightEdits)
        assertEquals(stored.nights, back.nights)
    }

    @Test fun legacyNightsJsonWithoutTheFieldDecodesWithNoTonightEdits() {
        // A plan written by v0.4.4: nights JSON has no tonightEdits key.
        val entity = PlanEntity(
            id = "plan-old", weekStart = "2026-10-12", budgetCents = 8000,
            cashNeededCents = 0, foodCostCents = 0, samsMonthlySavingsCents = 0,
            nightsJson = "[{\"day\":\"MON\",\"recipeId\":\"turkey-spaghetti\"}," +
                "{\"day\":\"TUE\",\"recipeId\":\"egg-fried-rice\",\"notCooking\":true," +
                "\"notCookingReason\":\"leftovers\"}]",
            notesJson = "[]", createdAtMillis = 1L,
        )
        val back = with(PlanMapper) { entity.toStoredPlan() }
        assertEquals(2, back.nights.size)
        assertNull(back.nights[0].tonightEdits)
        assertNull(back.nights[1].tonightEdits)
        assertEquals(true, back.nights[1].notCooking)
    }
}
