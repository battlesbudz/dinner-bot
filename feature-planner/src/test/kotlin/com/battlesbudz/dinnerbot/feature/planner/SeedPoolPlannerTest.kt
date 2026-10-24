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
        // The household owns its staples: the names of the priced items
        // flagged staple (before v0.4.3 these came from the recipes'
        // free-floating pantry names).
        priceBook.items.filter { it.staple }.map { it.name }.toSet()
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
        // 31 original items plus the eleven staples priced in v0.4.3.
        assertEquals(42, priceBook.items.size)
    }

    @Test fun fortnightFromTheRealPoolFillsBothWeeksWithoutCrossWeekRepeats() {
        // Phase V2-3 against the real seed pool: two full weeks, no dinner
        // in both, and every top-up line is a perishable at a regular store.
        val settings = HouseholdSettings()
        val itemsById = priceBook.items.associateBy { it.id }
        for (variation in 0..4) {
            val plan = WeekPlanner.generateFortnight(
                settings = settings,
                recipes = recipesFile.recipes,
                priceItems = priceBook.items,
                stockOnHand = emptyMap(),
                pantryOwnedNames = pantryNames,
                week1Start = "2026-10-26",
                week2Start = "2026-11-02",
                variation = variation,
            )
            assertEquals("variation $variation: week 1 full", 5, plan.week1.nights.size)
            assertEquals("variation $variation: week 2 full", 5, plan.week2.nights.size)
            val week1Ids = plan.week1.nights.map { it.recipeId }.toSet()
            val week2Ids = plan.week2.nights.map { it.recipeId }.toSet()
            assertTrue(
                "variation $variation repeats across weeks",
                week1Ids.intersect(week2Ids).isEmpty(),
            )
            assertEquals(
                plan.week1.cashNeededCents + plan.week2.cashNeededCents,
                plan.tripCashCents,
            )
            plan.topUpLines.forEach { line ->
                val item = itemsById[line.itemId]
                assertTrue(
                    "top-up line ${line.itemId} must be perishable and not a Sam's purchase",
                    item != null &&
                        com.battlesbudz.dinnerbot.core.model.Perishables.isTopUpLine(line, item),
                )
            }
        }
    }
}
