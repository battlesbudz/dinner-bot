package com.battlesbudz.dinnerbot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.PlanEntity
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import com.battlesbudz.dinnerbot.core.data.SeedMapper
import com.battlesbudz.dinnerbot.core.data.SettingsEntity
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Epic issue #10 flow proof: the photo store copies a picked photo into
 * app-local storage; a recipe with a stored photo shows it (with its
 * credit controls) on the recipe screen and as a thumbnail on its
 * planned night in the week view; a recipe with no photo keeps the
 * exact pre-photo rendering, with the add entry instead.
 */
@RunWith(AndroidJUnit4::class)
class RecipePhotoInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context
    private lateinit var photoRecipeId: String
    private lateinit var photoRecipeTitle: String
    private lateinit var plainRecipeTitle: String

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("dinner-bot.db")
        RecipePhotoStore.directory(context).deleteRecursively()
        val db = Room.databaseBuilder(context, DinnerBotDatabase::class.java, "dinner-bot.db")
            .allowMainThreadQueries()
            .build()
        try {
            val priceJson = context.assets.open("pricebook.json").bufferedReader().use { it.readText() }
            val recipesJson = context.assets.open("recipes.json").bufferedReader().use { it.readText() }
            SeedLoader(db).seedIfEmpty(priceJson, recipesJson)
            db.settingsDao().upsert(
                SettingsEntity(
                    id = 1,
                    householdSize = 4,
                    cookNightsJson = "[\"MON\",\"TUE\",\"WED\",\"THU\",\"FRI\"]",
                    weeklyBudgetCents = 100_00,
                    wontEatJson = "[]",
                    dietNotes = "",
                    pantryStaplesJson = "[]",
                    onboardingComplete = true,
                ),
            )
            val recipes = db.recipeDao().getAll()
            val photoRecipe = recipes.first()
            photoRecipeId = photoRecipe.id
            photoRecipeTitle = photoRecipe.title
            plainRecipeTitle = recipes[1].title

            // Since Phase C2 every library recipe ships with a bundled
            // photo, which MainActivity seeds on launch; that would hand
            // the "no photo" recipe below a photo before the test can
            // look at it. Pre-mark the bundled seeding as done so this
            // test keeps covering the picked-photo and no-photo paths it
            // was written for (BundledPhotoSeederTest covers the seeding
            // itself).
            val photoDir = RecipePhotoStore.directory(context)
            photoDir.mkdirs()
            File(photoDir, ".bundled-photos-seeded").writeText("seeded")

            // A "picked" photo: a real JPEG the store copies into place.
            val source = File(context.cacheDir, "picked-source.jpg")
            FileOutputStream(source).use { out ->
                Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.rgb(180, 90, 40))
                }.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            val saved = RecipePhotoStore.savePickedPhoto(
                context, photoRecipeId, Uri.fromFile(source), "Household photo", 1L,
            )
            assertNotNull("the photo store must accept a readable picked image", saved)
            assertTrue(RecipePhotoStore.fileFor(context, saved!!).exists())
            db.recipePhotoDao().upsert(with(SeedMapper) { saved.toEntity() })

            // This week's plan has the photo dinner on Monday.
            val monday = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
            db.planDao().insert(
                PlanEntity(
                    id = "plan-photo",
                    weekStart = monday,
                    budgetCents = 100_00,
                    cashNeededCents = 0,
                    foodCostCents = 0,
                    samsMonthlySavingsCents = 0,
                    nightsJson = "[{\"day\":\"MON\",\"recipeId\":\"$photoRecipeId\"}]",
                    notesJson = "[]",
                    createdAtMillis = 1L,
                ),
            )
        } finally {
            db.close()
        }
    }

    @After fun tearDown() {
        context.deleteDatabase("dinner-bot.db")
        RecipePhotoStore.directory(context).deleteRecursively()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * A picked camera JPEG whose pixels are landscape but whose EXIF
     * orientation says rotate 90 must be stored portrait: the store
     * applies the orientation to the pixels, because the JPEG it writes
     * carries no EXIF of its own. A successful save also leaves no
     * temporary file behind (the write is temp-then-move, so a failed
     * replacement can never truncate the previous photo).
     */
    @Test fun exifOrientationIsAppliedAndNoTempFileRemains() {
        val source = File(context.cacheDir, "picked-exif.jpg")
        FileOutputStream(source).use { out ->
            Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(40, 120, 200))
            }.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        val exif = android.media.ExifInterface(source.path)
        exif.setAttribute(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_ROTATE_90.toString(),
        )
        exif.saveAttributes()

        val saved = RecipePhotoStore.savePickedPhoto(
            context, "exif-recipe", Uri.fromFile(source), "Household photo", 2L,
        )
        assertNotNull(saved)
        val stored = RecipePhotoStore.fileFor(context, saved!!)
        assertTrue(stored.exists())
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(stored.path, bounds)
        assertTrue(
            "stored photo must be portrait (was ${bounds.outWidth}x${bounds.outHeight})",
            bounds.outHeight == 240 && bounds.outWidth == 160,
        )
        assertTrue(
            File(RecipePhotoStore.directory(context), "${saved.fileName}.tmp").exists().not(),
        )
    }

    @Test fun photoDisplaysOnRecipeScreenAndWeekViewAndAbsentRecipeIsUnchanged() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Dinner Bot recipes")

            // Detail with a photo: image, credit field, change/remove.
            composeRule.onNodeWithText(photoRecipeTitle).performClick()
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithTag("recipe-photo").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("recipe-photo").assertIsDisplayed()
            composeRule.onNodeWithText("Remove photo").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Change photo").assertIsDisplayed()

            // Detail without a photo: the add entry, no image node.
            composeRule.onNodeWithText("Back to recipes").performClick()
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithText(plainRecipeTitle).performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithTag("add-recipe-photo").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("add-recipe-photo").assertIsDisplayed()
            assertTrue(
                composeRule.onAllNodesWithTag("recipe-photo").fetchSemanticsNodes().isEmpty(),
            )

            // Week view: Monday's night card carries the photo thumbnail.
            composeRule.onNodeWithText("Back to recipes").performClick()
            waitForText("Dinner Bot recipes")
            composeRule.onNodeWithTag("tab-Plan").performClick()
            waitForText("This week's dinners")
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithContentDescription("Photo of $photoRecipeTitle")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("Photo of $photoRecipeTitle")
                .assertIsDisplayed()
        }
    }
}
