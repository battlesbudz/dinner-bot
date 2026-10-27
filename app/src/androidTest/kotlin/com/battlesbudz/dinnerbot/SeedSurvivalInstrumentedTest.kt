package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.SeedDataParser
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.EffectiveRecipes
import com.battlesbudz.dinnerbot.core.model.RecipeOverride
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * THE seed-survival test (Phase RI-1, plan section 5.1), Battles's rule
 * proven end to end: a household edit made on a populated v11 database
 * (migrated to v12 the way Andrea's phone upgrades) survives a seed
 * refresh from a CHANGED bundle — the edited recipe still cooks her
 * version, the bundle's new recipe arrives, an unedited recipe takes
 * its bundle update, a recipe the bundle drops leaves (its override
 * cleaned up with it), and every other kind of user data is intact.
 */
@RunWith(AndroidJUnit4::class)
class SeedSurvivalInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "seed-survival-test.db"

    @Before fun setUp() {
        PopulatedV11Database.create(context, dbName)
    }

    @After fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun openMigrated(): DinnerBotDatabase =
        Room.databaseBuilder(context, DinnerBotDatabase::class.java, dbName)
            .addMigrations(
                DinnerBotDatabase.MIGRATION_2_3,
                DinnerBotDatabase.MIGRATION_3_4,
                DinnerBotDatabase.MIGRATION_4_5,
                DinnerBotDatabase.MIGRATION_5_6,
                DinnerBotDatabase.MIGRATION_6_7,
                DinnerBotDatabase.MIGRATION_7_8,
                DinnerBotDatabase.MIGRATION_8_9,
                DinnerBotDatabase.MIGRATION_9_10,
                DinnerBotDatabase.MIGRATION_10_11,
                DinnerBotDatabase.MIGRATION_11_12,
                DinnerBotDatabase.MIGRATION_12_13,
                DinnerBotDatabase.MIGRATION_13_14,
                DinnerBotDatabase.MIGRATION_14_15,
                DinnerBotDatabase.MIGRATION_15_16,
                DinnerBotDatabase.MIGRATION_16_17,
            )
            .build()

    /** The shipped bundle with a seed "fix" to her recipe, a retitled unedited recipe, and one addition. */
    private fun changedBundle(recipesJson: String): String {
        val root = JSONObject(recipesJson)
        val recipes = root.getJSONArray("recipes")
        for (i in 0 until recipes.length()) {
            val recipe = recipes.getJSONObject(i)
            when (recipe.getString("id")) {
                "turkey-spaghetti" -> {
                    // A seed-side change to the recipe she has edited:
                    // the spaghetti amount moves 16 -> 24 oz.
                    val ingredients = recipe.getJSONArray("ingredients")
                    for (j in 0 until ingredients.length()) {
                        val ingredient = ingredients.getJSONObject(j)
                        if (ingredient.optString("priceItemId") == "spaghetti") {
                            ingredient.getJSONObject("quantity").put("amount", 24.0)
                        }
                    }
                }
                "egg-fried-rice" -> recipe.put("title", "Egg Fried Rice Deluxe")
            }
        }
        val addition = JSONObject()
            .put("id", "brand-new-dinner")
            .put("title", "Brand New Dinner")
            .put("servings", 4)
            .put("activeMinutes", 15)
            .put("cuisine", "Test")
            .put("mainProtein", "Egg")
            .put("dietTags", JSONArray())
            .put(
                "ingredients",
                JSONArray().put(
                    JSONObject()
                        .put("essential", true)
                        .put("priceItemId", "eggs")
                        .put(
                            "quantity",
                            JSONObject().put("amount", 6.0).put("unit", "COUNT"),
                        ),
                ),
            )
            .put("steps", JSONArray().put("Cook."))
        recipes.put(addition)
        return root.toString()
    }

    /** The shipped bundle with two recipes dropped entirely. */
    private fun removalBundle(recipesJson: String): String {
        val root = JSONObject(recipesJson)
        val recipes = root.getJSONArray("recipes")
        val kept = JSONArray()
        for (i in 0 until recipes.length()) {
            val recipe = recipes.getJSONObject(i)
            if (recipe.getString("id") !in setOf("turkey-spaghetti", "egg-fried-rice")) {
                kept.put(recipe)
            }
        }
        root.put("recipes", kept)
        return root.toString()
    }

    @Test fun householdEditSurvivesMigrationAndChangedBundleRefresh() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = openMigrated()
        try {
            assertEquals(17, db.openHelper.readableDatabase.version)
            val priceJson = appContext.assets.open("pricebook.json").bufferedReader().use { it.readText() }
            val recipesJson = appContext.assets.open("recipes.json").bufferedReader().use { it.readText() }
            val loader = SeedLoader(db)

            // The library as an installed v0.4.4 phone holds it: the
            // shipped bundle's recipes stored whole. (The fixture's
            // one-row recipe table stands in for it until here.)
            db.recipeDao().deleteAll()
            loader.seedIfEmpty(priceJson, recipesJson)
            assertEquals(100, db.recipeDao().count())

            // Her household edit on Turkey Spaghetti, made the way the
            // editor saves it: onions and garlic toast out, turkey doubled.
            val seedSpaghetti = SeedDataParser.parseRecipes(recipesJson)
                .single { it.id == "turkey-spaghetti" }
            val herList = seedSpaghetti.ingredients
                .filter { it.priceItemId !in setOf("yellow-onions", "garlic-texas-toast") }
                .map {
                    if (it.priceItemId == "ground-turkey") {
                        it.copy(quantity = it.quantity.copy(amount = 2.0))
                    } else {
                        it
                    }
                }
            assertEquals(8, herList.size)
            db.recipeOverrideDao().upsert(
                with(SeedMapper) {
                    RecipeOverride("turkey-spaghetti", herList, 500L).toEntity()
                },
            )

            // The app updates with a changed bundle and the refresh runs.
            val written = loader.refreshSeedRecipes(changedBundle(recipesJson))
            assertTrue("the refresh wrote the updated set", written > 0)

            val storedById = db.recipeDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
                .associateBy { it.id }

            // Her recipe's STORED row was not refreshed at all: still the
            // bundle version she edited (16 oz of spaghetti), because a
            // changed recipe is never refreshed, in whole or in part.
            val storedSpaghetti = storedById.getValue("turkey-spaghetti")
            assertEquals(
                16.0,
                storedSpaghetti.ingredients.single { it.priceItemId == "spaghetti" }
                    .quantity.amount,
                0.001,
            )

            // And the version that cooks is hers.
            val overrides = db.recipeOverrideDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
                .associateBy { it.recipeId }
            assertEquals(herList, overrides.getValue("turkey-spaghetti").ingredients)
            val effective = EffectiveRecipes.applyOverrides(
                storedById.values.toList(), overrides,
            ).associateBy { it.id }
            val cooks = effective.getValue("turkey-spaghetti")
            assertEquals(herList, cooks.ingredients)
            assertFalse(cooks.ingredients.any { it.priceItemId == "yellow-onions" })
            assertEquals(
                2.0,
                cooks.ingredients.single { it.priceItemId == "ground-turkey" }.quantity.amount,
                0.001,
            )

            // Additions and unedited updates flow in normally.
            assertEquals("Egg Fried Rice Deluxe", storedById.getValue("egg-fried-rice").title)
            assertTrue("brand-new-dinner" in storedById)
            assertEquals(101, storedById.size)

            // Every other kind of user data is intact after it all.
            assertEquals(799, db.priceItemDao().getById("chicken-breast")!!.packPriceCents)
            assertEquals(499, db.priceItemDao().getById("user-chicken-drumsticks")!!.packPriceCents)
            assertEquals("UP", db.recipeRatingDao().getAll().single().rating)
            assertEquals(
                "chicken-breast",
                db.barcodeLinkDao().getByBarcode("012345678905")!!.itemId,
            )
            assertEquals("Salt", db.pantryDao().getAll().single().name)
            assertEquals(4.5, db.bulkStockDao().getAll().single().amount, 0.001)
            assertEquals("r1.jpg", db.recipePhotoDao().getAll().single().fileName)
            assertEquals(1, db.scanSubstitutionDao().getAll().size)
            assertEquals(
                "chicken-breast",
                db.groceryDao().getForPlan("plan-1")
                    .single { it.itemId == "user-chicken-drumsticks" }.substitutedForItemId,
            )
            assertEquals(1, db.ranOutDao().getAll().size)
            assertTrue(db.cookCheckoffDao().getForPlan("plan-1").single().checked)

            // A bundle that drops recipes retires them, and her override
            // for the dropped recipe is cleaned up rather than orphaned.
            loader.refreshSeedRecipes(removalBundle(recipesJson))
            val afterRemoval = db.recipeDao().getAll().map { it.id }.toSet()
            assertFalse("turkey-spaghetti" in afterRemoval)
            assertFalse("egg-fried-rice" in afterRemoval)
            assertNull(db.recipeOverrideDao().getByRecipeId("turkey-spaghetti"))
            assertTrue(db.recipeOverrideDao().getAll().isEmpty())
        } finally {
            db.close()
        }
    }
}
