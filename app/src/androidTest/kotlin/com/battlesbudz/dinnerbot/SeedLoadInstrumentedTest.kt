package com.battlesbudz.dinnerbot

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.SeedLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The bundled seed assets load into Room on a device exactly as on the JVM. */
@RunWith(AndroidJUnit4::class)
class SeedLoadInstrumentedTest {

    @Test fun seedAssetsLoadIntoDatabase() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DinnerBotDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val priceJson = appContext.assets.open("pricebook.json").bufferedReader().use { it.readText() }
            val recipesJson = appContext.assets.open("recipes.json").bufferedReader().use { it.readText() }
            val result = SeedLoader(db).seedIfEmpty(priceJson, recipesJson)
            assertTrue("prices inserted: ${result.priceItemsInserted}", result.priceItemsInserted >= 20)
            assertTrue("recipes inserted: ${result.recipesInserted}", result.recipesInserted >= 10)
            assertEquals(result.priceItemsInserted, db.priceItemDao().count())
            assertEquals(result.recipesInserted, db.recipeDao().count())
            val tenderloin = db.priceItemDao().getById("pork-tenderloin")!!
            assertEquals("sams-club", tenderloin.homeStore)
        } finally {
            db.close()
        }
    }
}
