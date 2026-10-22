package com.battlesbudz.dinnerbot.dataseed

import com.battlesbudz.dinnerbot.core.model.PriceBookFile
import com.battlesbudz.dinnerbot.core.model.RecipesFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * v0.1.2 content expansion guarantees, checked against the REAL canonical
 * seed files in repo-root seed-data/: the library grew from 12 to 36 recipes,
 * the price book covers every ingredient the new recipes use (with matching
 * units), per-serving costs compute for every recipe, and the essential /
 * removable marks the won't-eat rule depends on are present and sane.
 */
class SeedExpansionTest {
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

    private val newRecipeIds = listOf(
        // Ground turkey
        "turkey-burgers", "turkey-chili-rotini", "turkey-bean-chili",
        "turkey-shepherds-pie", "turkey-meatballs-spaghetti",
        // Chicken
        "chicken-fajitas", "chicken-quesadillas", "cheesy-chicken-broccoli-rice",
        "chicken-noodle-skillet", "sloppy-chicken-sandwiches",
        // Pork
        "pork-fried-rice", "sheet-pan-pork-potatoes", "pork-fajitas",
        "saucy-pork-sandwiches", "cheesy-pork-rotini-skillet",
        // Egg
        "potato-egg-skillet", "breakfast-burritos", "veggie-frittata",
        "cheese-omelets-home-fries",
        // Cheese / vegetarian
        "rotini-mac-and-cheese", "cheese-quesadillas", "veggie-pasta-bake",
        "cheesy-potato-skillet", "bean-cheese-burritos",
    )

    private val newItemIds = listOf(
        "hamburger-buns", "rotini", "canned-black-beans", "canned-kidney-beans",
        "canned-diced-tomatoes", "bell-peppers", "frozen-broccoli", "egg-noodles",
    )

    @Test fun libraryGrewTo36RecipesAnd31PriceItems() {
        assertEquals(36, recipesFile.recipes.size)
        assertEquals(31, priceBook.items.size)
        val recipeIds = recipesFile.recipes.map { it.id }.toSet()
        for (id in newRecipeIds) assertTrue("missing new recipe $id", id in recipeIds)
        val itemIds = priceBook.items.map { it.id }.toSet()
        for (id in newItemIds) assertTrue("missing new price item $id", id in itemIds)
    }

    @Test fun proteinSpreadAcrossAll36() {
        val counts = recipesFile.recipes.groupingBy { it.mainProtein }.eachCount()
        assertEquals(10, counts["Ground turkey"])
        assertEquals(7, counts["Chicken"])
        assertEquals(8, counts["Pork"])
        assertEquals(5, counts["Egg"])
        assertEquals(6, counts["Cheese"])
    }

    @Test fun everyPricedIngredientResolvesWithMatchingUnit() {
        val byId = priceBook.items.associateBy { it.id }
        for (recipe in recipesFile.recipes) {
            for (ingredient in recipe.ingredients) {
                val itemId = ingredient.priceItemId ?: continue
                val item = byId[itemId]
                assertTrue("recipe ${recipe.id} references unknown item $itemId", item != null)
                assertEquals(
                    "recipe ${recipe.id}: unit for $itemId must match the price book pack unit",
                    item!!.packQuantity.unit, ingredient.quantity.unit,
                )
            }
        }
    }

    @Test fun perServingCostComputesForEveryRecipe() {
        val byId = priceBook.items.associateBy { it.id }
        for (recipe in recipesFile.recipes) {
            var totalCents = 0.0
            for (ingredient in recipe.ingredients) {
                val itemId = ingredient.priceItemId ?: continue
                totalCents += ingredient.quantity.amount * byId.getValue(itemId).perUnitCents
            }
            val perServing = totalCents / recipe.servings
            assertTrue(
                "recipe ${recipe.id} must have a positive per-serving cost, was $perServing",
                perServing > 0.0,
            )
        }
    }

    @Test fun essentialMarksFollowTheWontEatRule() {
        val byId = recipesFile.recipes.associateBy { it.id }
        fun essential(recipeId: String, itemId: String): Boolean {
            val recipe = byId.getValue(recipeId)
            return recipe.ingredients.single { it.priceItemId == itemId }.essential
        }
        // The defining starch and the protein are essential; the onion is not.
        assertTrue(essential("turkey-chili-rotini", "rotini"))
        assertTrue(essential("turkey-chili-rotini", "ground-turkey"))
        assertTrue(!essential("turkey-chili-rotini", "yellow-onions"))
        // Fried rice stands on its pork, eggs, and rice; peas and carrots are removable.
        assertTrue(essential("pork-fried-rice", "pork-tenderloin"))
        assertTrue(essential("pork-fried-rice", "eggs"))
        assertTrue(!essential("pork-fried-rice", "frozen-peas-carrots"))
        // A bean burrito without the beans is not the dish; the salad side of a
        // quesadilla plate is removable.
        assertTrue(essential("bean-cheese-burritos", "canned-black-beans"))
        assertTrue(!essential("cheese-quesadillas", "garden-salad"))
        // Mac and cheese needs its pasta, cheese, and milk.
        assertTrue(essential("rotini-mac-and-cheese", "rotini"))
        assertTrue(essential("rotini-mac-and-cheese", "shredded-cheddar"))
        assertTrue(essential("rotini-mac-and-cheese", "milk"))
    }

    @Test fun newRecipesHaveNoBeefAndFullSteps() {
        val byId = recipesFile.recipes.associateBy { it.id }
        for (id in newRecipeIds) {
            val recipe = byId.getValue(id)
            assertTrue(recipe.steps.size >= 4)
            assertEquals(4, recipe.servings)
            assertTrue(recipe.ingredients.none {
                (it.priceItemId ?: "").contains("beef", ignoreCase = true)
            })
        }
    }
}
