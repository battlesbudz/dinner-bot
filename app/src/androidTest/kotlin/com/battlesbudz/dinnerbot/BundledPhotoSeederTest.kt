package com.battlesbudz.dinnerbot

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase C2: the bundled starter photos land on a fresh install — one
 * record per seed recipe, the credited text intact, the image file in
 * local storage — and the one-time marker keeps a photo the user
 * removed from ever coming back.
 */
@RunWith(AndroidJUnit4::class)
class BundledPhotoSeederTest {

    @Test fun bundledPhotosSeedOnceAndRemovalsStick() = kotlinx.coroutines.runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DinnerBotDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val photosDir = RecipePhotoStore.directory(appContext)
            photosDir.listFiles()?.forEach { it.delete() }

            val priceJson = appContext.assets.open("pricebook.json").bufferedReader().use { it.readText() }
            val recipesJson = appContext.assets.open("recipes.json").bufferedReader().use { it.readText() }
            val seeded = SeedLoader(db).seedIfEmpty(priceJson, recipesJson)
            assertEquals(100, seeded.recipesInserted)

            val openAsset = { name: String -> appContext.assets.open(name) }
            val inserted = BundledPhotoSeeder.seedIfNeeded(appContext, db, openAsset)
            assertEquals(100, inserted)
            val records = db.recipePhotoDao().getAll()
            assertEquals(100, records.size)
            val spaghetti = records.single { it.recipeId == "turkey-spaghetti" }
            assertTrue("bundled photo carries a credit", spaghetti.credit.isNotBlank())
            val spaghettiFile = File(photosDir, spaghetti.fileName)
            assertTrue("bundled photo file was copied", spaghettiFile.isFile && spaghettiFile.length() > 5_000)

            // A second run is a no-op: the marker says the copy ran.
            assertEquals(0, BundledPhotoSeeder.seedIfNeeded(appContext, db, openAsset))

            // The user removes a bundled photo (record and file, as the
            // remove flow does): it stays removed on later starts.
            db.recipePhotoDao().delete("turkey-spaghetti")
            spaghettiFile.delete()
            assertEquals(0, BundledPhotoSeeder.seedIfNeeded(appContext, db, openAsset))
            assertEquals(99, db.recipePhotoDao().getAll().size)
            assertTrue(db.recipePhotoDao().getByRecipeId("turkey-spaghetti") == null)
        } finally {
            db.close()
        }
    }
}
