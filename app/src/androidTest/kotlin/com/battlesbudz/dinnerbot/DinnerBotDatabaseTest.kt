package com.battlesbudz.dinnerbot

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.core.data.DinnerBotDatabase
import com.battlesbudz.dinnerbot.core.data.GroceryItemEntity
import com.battlesbudz.dinnerbot.core.data.PantryItemEntity
import com.battlesbudz.dinnerbot.core.data.PlanEntity
import com.battlesbudz.dinnerbot.core.data.PriceItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room DAO tests on a real (in-memory) SQLite database. */
@RunWith(AndroidJUnit4::class)
class DinnerBotDatabaseTest {

    private lateinit var db: DinnerBotDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DinnerBotDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        db.close()
    }

    private fun priceItem(id: String, price: Int) = PriceItemEntity(
        id = id, name = id, category = "Test", homeStore = "ALDI",
        packDescription = "pack", packAmount = 1.0, packUnit = "POUND",
        packPriceCents = price, pricePerUnitCents = price.toDouble(),
        checkedDate = "2026-10-22", source = "test", userCorrected = false,
        alternatesJson = "[]",
    )

    @Test fun priceCorrectionUpdatesRow() = runBlocking {
        db.priceItemDao().insertAll(listOf(priceItem("onions", 219)))
        db.priceItemDao().updatePrice("onions", 249, 83.0)
        val updated = db.priceItemDao().getById("onions")!!
        assertEquals(249, updated.packPriceCents)
        assertTrue(updated.userCorrected)
    }

    @Test fun pantryTogglePersists() = runBlocking {
        db.pantryDao().upsert(PantryItemEntity("salt", "Salt", owned = true))
        val salt = db.pantryDao().getAll().single()
        db.pantryDao().upsert(salt.copy(owned = false))
        assertEquals(false, db.pantryDao().getAll().single().owned)
    }

    @Test fun planAndGroceryCheckoffPersist() = runBlocking {
        db.planDao().insert(
            PlanEntity(
                id = "plan-1", weekStart = "2026-10-19", budgetCents = 7500,
                cashNeededCents = 396, foodCostCents = 396, samsMonthlySavingsCents = 0,
                nightsJson = "[{\"day\":\"MON\",\"recipeId\":\"r1\"}]",
                notesJson = "[]", createdAtMillis = 1L,
            ),
        )
        db.groceryDao().insertAll(
            listOf(
                GroceryItemEntity(
                    planId = "plan-1", itemId = "ground-turkey", displayName = "Ground turkey",
                    store = "WALMART", neededAmount = 1.0, neededUnit = "POUND",
                    packsToBuy = 1, packPriceCents = 198, portionCostCents = 198,
                    checkedOff = false,
                ),
            ),
        )
        val rows = db.groceryDao().getForPlan("plan-1")
        assertEquals(1, rows.size)
        db.groceryDao().setChecked(rows[0].rowId, true)
        assertTrue(db.groceryDao().getForPlan("plan-1")[0].checkedOff)
        assertEquals("plan-1", db.planDao().getLatest()!!.id)
    }
}
