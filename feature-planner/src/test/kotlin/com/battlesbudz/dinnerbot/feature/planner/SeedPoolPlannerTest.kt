package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.PriceBookFile
import com.battlesbudz.dinnerbot.core.model.RecipesFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The v0.1.2 promise, tested against the REAL canonical seed files in
 * repo-root seed-data/: with the library at 36 recipes, the planner fills a
 * full week of cook nights with no repeated dinner, across regenerate
 * variations, for both a 5-night and a 7-night week, with and without a
 * weekly cash cap.
 */
class SeedPoolPlannerTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun seedFile(name: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "seed-data/$name")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        fail("could not locate seed-data/$name from ${System.getProperty("user.dir")}")
        error("unreachable")
    }

    private val priceBook by lazy {
        json.decodeFromString<PriceBookFile>(seedFile("pricebook.json").readText())
    }
    private val recipesFile by lazy {
        json.decodeFromString<RecipesFile>(seedFile("recipes.json").readText())
    }
    private val pantryNames by lazy {
        recipesFile.recipes.flatMap { it.ingredients }.mapNotNull { it.pantryItem }.toSet()
    }

    private fun planWeek(settings: HouseholdSettings, variation: Int) =
        WeekPlanner.generate(
            settings = settings,
            recipes = recipesFile.recipes,
            priceItems = priceBook.items,
            stockOnHand = emptyMap(),
            pantryOwnedNames = pantryNames,
            weekStart = "2026-10-26",
            variation = variation,
        )

    @Test fun fullFiveNightWeekWithoutRepeatsAcrossVariations() {
        val settings = HouseholdSettings()
        assertEquals(5, settings.cookNights.size)
        for (variation in 0..9) {
            val plan = planWeek(settings, variation)
            assertEquals("variation $variation", 5, plan.nights.size)
            val ids = plan.nights.map { it.recipeId }
            assertEquals("variation $variation must not repeat a dinner", ids.size, ids.toSet().size)
        }
    }

    @Test fun fullSevenNightWeekWithoutRepeats() {
        val settings = HouseholdSettings(
            cookNights = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"),
        )
        for (variation in 0..4) {
            val plan = planWeek(settings, variation)
            assertEquals("variation $variation", 7, plan.nights.size)
            val ids = plan.nights.map { it.recipeId }
            assertEquals("variation $variation must not repeat a dinner", ids.size, ids.toSet().size)
        }
    }

    @Test fun cappedWeekStillFillsEveryNightWithoutRepeats() {
        val settings = HouseholdSettings(weeklyBudgetCents = 7500)
        for (variation in 0..4) {
            val plan = planWeek(settings, variation)
            assertEquals("variation $variation", 5, plan.nights.size)
            val ids = plan.nights.map { it.recipeId }
            assertEquals("variation $variation must not repeat a dinner", ids.size, ids.toSet().size)
            assertTrue("plan must cost something", plan.cashNeededCents > 0)
        }
    }

    @Test fun poolIsTheFull36() {
        assertEquals(36, recipesFile.recipes.size)
        assertEquals(31, priceBook.items.size)
    }
}
