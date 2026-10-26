package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanMapper
import com.battlesbudz.dinnerbot.core.data.ReconciliationMapper
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.model.EffectiveRecipes
import com.battlesbudz.dinnerbot.core.model.ImportedRecipe
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.RanOutState
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin
import com.battlesbudz.dinnerbot.core.model.RecipeOverride
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v0.5.1 upgrade proof (Phase RI-2): a populated version-12 database
 * (the v0.5.0 install base: everything the v11 fixture carried plus a
 * household recipe override) opens under version 13 through the real
 * MIGRATION_12_13 with every row intact. The migration only creates
 * the imported_recipes table, empty. An imported recipe written
 * through the new DAO merges into the effective recipe set on the load
 * path, and a seed refresh from a changed bundle (which delivers
 * additions and updates only) leaves both the imported recipe and the
 * household override row for row untouched — Battles's seed rule:
 * her recipes are never refreshed.
 */
@RunWith(AndroidJUnit4::class)
class Migration1213Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-12-13-test.db"

    @Before fun setUp() {
        PopulatedV12Database.create(context, dbName)
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

    private val herList = listOf(
        RecipeIngredient(
            priceItemId = "chicken-breast",
            quantity = Quantity(2.0, MeasureUnit.POUND),
            essential = true,
        ),
    )

    @Test fun upgradeFromV12KeepsEveryKindOfUserData() = runBlocking {
        val db = openMigrated()
        try {
            assertEquals(16, db.openHelper.readableDatabase.version)

            // Prices: the corrected one, the staple flag, the scan-added item.
            val breast = db.priceItemDao().getById("chicken-breast")!!
            assertEquals(799, breast.packPriceCents)
            assertTrue(breast.userCorrected)
            assertTrue(db.priceItemDao().getById("salt")!!.staple)
            assertEquals(499, db.priceItemDao().getById("user-chicken-drumsticks")!!.packPriceCents)

            // The v0.4.4 barcode rework state, whole.
            val swap = db.scanSubstitutionDao().getAll().single()
            assertEquals("chicken-breast", swap.originalItemId)
            assertEquals("user-chicken-drumsticks", swap.substituteItemId)
            assertEquals(499, swap.substitutePriceCents)
            val rows = db.groceryDao().getForPlan("plan-1")
            assertEquals(2, rows.size)
            assertEquals(
                "chicken-breast",
                rows.single { it.itemId == "user-chicken-drumsticks" }.substitutedForItemId,
            )
            assertNull(rows.single { it.itemId == "chicken-breast" }.substitutedForItemId)

            // Everything else, as in every migration test before this one.
            assertEquals("UP", db.recipeRatingDao().getAll().single().rating)
            assertEquals(
                "chicken-breast",
                db.barcodeLinkDao().getByBarcode("012345678905")!!.itemId,
            )
            assertEquals(189544L, db.ingredientNutritionDao().getAll().single().fdcId)
            val saltPantry = db.pantryDao().getAll().single()
            assertEquals("Salt", saltPantry.name)
            assertTrue(saltPantry.owned)
            assertEquals(4.5, db.bulkStockDao().getAll().single().amount, 0.001)
            val settings = with(SeedMapper) { db.settingsDao().get()!!.toModel() }
            assertEquals(4, settings.householdSize)
            assertEquals(listOf("plan-1"), settings.reconciledPlanIds)
            assertEquals("r1.jpg", db.recipePhotoDao().getAll().single().fileName)
            val event = with(ReconciliationMapper) { db.ranOutDao().getAll().single().toModel() }
            assertEquals(RanOutState.PENDING, event.state)
            assertTrue(db.cookCheckoffDao().getForPlan("plan-1").single().checked)
            val plan = with(PlanMapper) { db.planDao().getLatest()!!.toStoredPlan() }
            assertEquals("plan-1", plan.id)
            assertEquals("r1", plan.nights.single().recipeId)

            // The RI-1 override row survived the migration, and still
            // merges over the stored recipe on the load path.
            val overrides = db.recipeOverrideDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
                .associateBy { it.recipeId }
            assertEquals(herList, overrides.getValue("r1").ingredients)
            val stored = db.recipeDao().getAll().map { with(SeedMapper) { it.toModel() } }
            assertEquals(
                herList,
                EffectiveRecipes.applyOverrides(stored, overrides).single().ingredients,
            )

            // The new table starts empty: no recipe is anyone's own
            // until Andrea imports or writes one.
            assertTrue(db.importedRecipeDao().getAll().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test fun importedRecipeSurvivesSeedRefreshUntouched() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = openMigrated()
        try {
            val priceJson = appContext.assets.open("pricebook.json").bufferedReader().use { it.readText() }
            val recipesJson = appContext.assets.open("recipes.json").bufferedReader().use { it.readText() }
            val loader = SeedLoader(db)
            db.recipeDao().deleteAll()
            loader.seedIfEmpty(priceJson, recipesJson)
            assertEquals(36, db.recipeDao().count())

            val hers = ImportedRecipe(
                recipe = Recipe(
                    id = "imported-9-lemon-chicken", title = "Lemon Chicken",
                    servings = 4, activeMinutes = 35, cuisine = "Mediterranean",
                    mainProtein = "Chicken",
                    ingredients = listOf(
                        RecipeIngredient(
                            pantryItem = "Spring onions",
                            quantity = Quantity(2.0, MeasureUnit.COUNT),
                            essential = true,
                        ),
                    ),
                    steps = listOf("Sear.", "Serve."),
                    origin = RecipeOrigin.IMPORTED,
                ),
                sourceLink = "https://example.com/lemon-chicken",
                createdAtMillis = 42L,
            )
            db.importedRecipeDao().upsert(with(SeedMapper) { hers.toEntity() })

            // Her override on a library recipe the bundle still
            // carries (the fixture's r1 override is separate: r1 is
            // not a bundle recipe, so RI-1's orphan cleanup owns it).
            db.recipeOverrideDao().upsert(
                with(SeedMapper) { RecipeOverride("turkey-spaghetti", herList, 100L).toEntity() },
            )

            // A changed bundle: one library recipe retitled.
            val root = JSONObject(recipesJson)
            val recipes = root.getJSONArray("recipes")
            for (i in 0 until recipes.length()) {
                val recipe = recipes.getJSONObject(i)
                if (recipe.getString("id") == "egg-fried-rice") {
                    recipe.put("title", "Egg Fried Rice Deluxe")
                }
            }
            loader.refreshSeedRecipes(root.toString())

            // The library took its update...
            assertEquals(
                "Egg Fried Rice Deluxe",
                db.recipeDao().getAll().first { it.id == "egg-fried-rice" }.title,
            )
            // ...and her recipe is row for row what she saved, still
            // merging into the effective set on the load path.
            val storedImported = db.importedRecipeDao().getAll()
                .map { with(SeedMapper) { it.toModel() } }
            assertEquals(listOf(hers), storedImported)
            val library = db.recipeDao().getAll().map { with(SeedMapper) { it.toModel() } }
            val effective = EffectiveRecipes.mergeUserRecipes(
                library, storedImported.map { it.recipe },
            )
            assertEquals(37, effective.size)
            assertEquals(hers.recipe, effective.last())
            // Her override on the bundle recipe is untouched too.
            assertEquals(
                herList,
                db.recipeOverrideDao().getAll()
                    .map { with(SeedMapper) { it.toModel() } }
                    .single { it.recipeId == "turkey-spaghetti" }.ingredients,
            )
        } finally {
            db.close()
        }
    }
}
