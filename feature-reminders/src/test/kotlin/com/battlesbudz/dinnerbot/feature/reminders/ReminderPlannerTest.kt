package com.battlesbudz.dinnerbot.feature.reminders

import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.ReminderSettings
import com.battlesbudz.dinnerbot.core.model.Store
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scheduling logic, pinned case by case (Scope A6 / Plan 5.6). Weeks
 * start Monday 2026-10-19; the stored horizon is that week plus the
 * fortnight partner starting 2026-10-26.
 */
class ReminderPlannerTest {

    private val pork = PriceItem(
        id = "pork-tenderloin",
        name = "Pork tenderloin",
        category = "Meat",
        homeStore = Store.SAMS_CLUB,
        packDescription = "about 9 lb",
        packQuantity = Quantity(9.0, MeasureUnit.POUND),
        packPriceCents = 2168,
        checkedDate = "2026-10-22",
        source = "Test",
        freezeNote = "Freeze what this week will not use.",
    )

    /** A Sam's item with NO freeze guidance: stock-tracked but never a defrost candidate. */
    private val bulkRice = PriceItem(
        id = "bulk-rice",
        name = "Bulk rice",
        category = "Pantry",
        homeStore = Store.SAMS_CLUB,
        packDescription = "10 lb bag",
        packQuantity = Quantity(10.0, MeasureUnit.POUND),
        packPriceCents = 800,
        checkedDate = "2026-10-22",
        source = "Test",
    )

    private val turkey = PriceItem(
        id = "ground-turkey",
        name = "Ground turkey",
        category = "Meat",
        homeStore = Store.WALMART,
        packDescription = "1 lb",
        packQuantity = Quantity(1.0, MeasureUnit.POUND),
        packPriceCents = 198,
        checkedDate = "2026-10-22",
        source = "Test",
    )

    private fun recipe(id: String, title: String, item: PriceItem, pounds: Double) = Recipe(
        id = id,
        title = title,
        servings = 4,
        activeMinutes = 20,
        cuisine = "Test",
        mainProtein = "Test",
        ingredients = listOf(RecipeIngredient(priceItemId = item.id, quantity = Quantity(pounds, MeasureUnit.POUND))),
        steps = listOf("Cook."),
    )

    private val porkDinner = recipe("pork-dinner", "Pork stir fry", pork, 1.5)
    private val turkeyDinner = recipe("turkey-dinner", "Turkey spaghetti", turkey, 1.0)
    private val riceDinner = recipe("rice-dinner", "Rice bowls", bulkRice, 2.0)

    private val recipesById = listOf(porkDinner, turkeyDinner, riceDinner).associateBy { it.id }
    private val itemsById = listOf(pork, bulkRice, turkey).associateBy { it.id }

    private val week1Start: LocalDate = LocalDate.of(2026, 10, 19) // Monday
    private val week2Start: LocalDate = LocalDate.of(2026, 10, 26) // Monday

    private fun plan(
        settings: ReminderSettings,
        weeks: List<ReminderWeek>,
        stock: Map<String, Quantity> = emptyMap(),
        now: LocalDateTime,
        granted: Boolean = true,
    ): List<PlannedReminder> = ReminderPlanner.plan(
        settings = settings,
        householdSize = 4,
        weeks = weeks,
        recipesById = recipesById,
        itemsById = itemsById,
        stockOnHand = stock,
        now = now,
        notificationsGranted = granted,
    )

    private fun ofType(reminders: List<PlannedReminder>, type: ReminderType) =
        reminders.filter { it.type == type }

    // ---- Dinner ----

    @Test fun dinnerReminderNamesTonightsDinnerOnEachCookNight() {
        val weeks = listOf(
            ReminderWeek(
                week1Start,
                listOf(
                    PlannedNight("MON", porkDinner.id),
                    PlannedNight("TUE", turkeyDinner.id),
                ),
            ),
        )
        val reminders = plan(
            settings = ReminderSettings(dinnerEnabled = true, dinnerTime = "16:30"),
            weeks = weeks,
            now = LocalDateTime.of(2026, 10, 19, 8, 0),
        )
        val dinners = ofType(reminders, ReminderType.DINNER)
        assertEquals(2, dinners.size)
        assertEquals(LocalDateTime.of(2026, 10, 19, 16, 30), dinners[0].triggerAt)
        assertEquals("Tonight: Pork stir fry", dinners[0].text)
        assertEquals(LocalDateTime.of(2026, 10, 20, 16, 30), dinners[1].triggerAt)
        assertEquals("Tonight: Turkey spaghetti", dinners[1].text)
    }

    @Test fun dinnerReminderSkipsNightsMarkedNotCooking() {
        val weeks = listOf(
            ReminderWeek(
                week1Start,
                listOf(
                    PlannedNight("MON", porkDinner.id),
                    PlannedNight("TUE", turkeyDinner.id, notCooking = true, notCookingReason = "leftovers"),
                ),
            ),
        )
        val reminders = plan(
            settings = ReminderSettings(dinnerEnabled = true),
            weeks = weeks,
            now = LocalDateTime.of(2026, 10, 19, 8, 0),
        )
        val dinners = ofType(reminders, ReminderType.DINNER)
        assertEquals(1, dinners.size)
        assertEquals("Tonight: Pork stir fry", dinners.single().text)
    }

    @Test fun dinnerReminderWhoseTimeHasPassedIsNotScheduled() {
        val weeks = listOf(
            ReminderWeek(
                week1Start,
                listOf(
                    PlannedNight("MON", porkDinner.id),
                    PlannedNight("TUE", turkeyDinner.id),
                ),
            ),
        )
        val reminders = plan(
            settings = ReminderSettings(dinnerEnabled = true, dinnerTime = "16:30"),
            weeks = weeks,
            now = LocalDateTime.of(2026, 10, 19, 17, 0), // Monday's 16:30 is gone
        )
        val dinners = ofType(reminders, ReminderType.DINNER)
        assertEquals(1, dinners.size)
        assertEquals(LocalDateTime.of(2026, 10, 20, 16, 30), dinners.single().triggerAt)
    }

    // ---- Defrost ----

    @Test fun defrostFiresTheNightBeforeNamingTheFrozenItem() {
        val weeks = listOf(ReminderWeek(week1Start, listOf(PlannedNight("MON", porkDinner.id))))
        val reminders = plan(
            settings = ReminderSettings(defrostEnabled = true, defrostTime = "20:00"),
            weeks = weeks,
            stock = mapOf(pork.id to Quantity(4.5, MeasureUnit.POUND)),
            now = LocalDateTime.of(2026, 10, 17, 10, 0), // Saturday before
        )
        val defrosts = ofType(reminders, ReminderType.DEFROST)
        assertEquals(1, defrosts.size)
        assertEquals(LocalDateTime.of(2026, 10, 18, 20, 0), defrosts.single().triggerAt) // Sunday evening
        assertEquals("Move the Pork tenderloin to the fridge tonight.", defrosts.single().text)
    }

    @Test fun defrostSkippedWhenTheWeekBuysTheItemFresh() {
        val weeks = listOf(ReminderWeek(week1Start, listOf(PlannedNight("MON", porkDinner.id))))
        val reminders = plan(
            settings = ReminderSettings(defrostEnabled = true),
            weeks = weeks,
            stock = emptyMap(), // nothing in the freezer: the pack is bought this week
            now = LocalDateTime.of(2026, 10, 17, 10, 0),
        )
        assertTrue(ofType(reminders, ReminderType.DEFROST).isEmpty())
    }

    @Test fun defrostSkippedForStockedItemWithoutFreezeGuidance() {
        val weeks = listOf(ReminderWeek(week1Start, listOf(PlannedNight("WED", riceDinner.id))))
        val reminders = plan(
            settings = ReminderSettings(defrostEnabled = true),
            weeks = weeks,
            stock = mapOf(bulkRice.id to Quantity(5.0, MeasureUnit.POUND)),
            now = LocalDateTime.of(2026, 10, 17, 10, 0),
        )
        assertTrue(ofType(reminders, ReminderType.DEFROST).isEmpty())
    }

    @Test fun defrostFiresOnAPartialStockCover() {
        val weeks = listOf(ReminderWeek(week1Start, listOf(PlannedNight("MON", porkDinner.id))))
        val reminders = plan(
            settings = ReminderSettings(defrostEnabled = true),
            weeks = weeks,
            stock = mapOf(pork.id to Quantity(1.0, MeasureUnit.POUND)), // dinner needs 1.5 lb
            now = LocalDateTime.of(2026, 10, 17, 10, 0),
        )
        assertEquals(1, ofType(reminders, ReminderType.DEFROST).size)
    }

    @Test fun stockDrawnByAnEarlierNightLeavesNothingForALaterNight() {
        val weeks = listOf(
            ReminderWeek(
                week1Start,
                listOf(
                    PlannedNight("MON", porkDinner.id),
                    PlannedNight("WED", porkDinner.id),
                    PlannedNight("FRI", porkDinner.id),
                ),
            ),
        )
        val reminders = plan(
            settings = ReminderSettings(defrostEnabled = true),
            weeks = weeks,
            stock = mapOf(pork.id to Quantity(3.0, MeasureUnit.POUND)), // covers Mon + Wed exactly
            now = LocalDateTime.of(2026, 10, 17, 10, 0),
        )
        val defrosts = ofType(reminders, ReminderType.DEFROST)
        assertEquals(2, defrosts.size)
        assertEquals(LocalDate.of(2026, 10, 18), defrosts[0].triggerAt.toLocalDate()) // before Mon
        assertEquals(LocalDate.of(2026, 10, 20), defrosts[1].triggerAt.toLocalDate()) // before Wed
    }

    @Test fun stockDrawdownCarriesAcrossTheFortnight() {
        val weeks = listOf(
            ReminderWeek(week1Start, listOf(PlannedNight("MON", porkDinner.id))),
            ReminderWeek(week2Start, listOf(PlannedNight("MON", porkDinner.id))),
        )
        val reminders = plan(
            settings = ReminderSettings(defrostEnabled = true),
            weeks = weeks,
            stock = mapOf(pork.id to Quantity(1.5, MeasureUnit.POUND)), // only week 1's dinner is covered
            now = LocalDateTime.of(2026, 10, 17, 10, 0),
        )
        val defrosts = ofType(reminders, ReminderType.DEFROST)
        assertEquals(1, defrosts.size)
        assertEquals(LocalDate.of(2026, 10, 18), defrosts.single().triggerAt.toLocalDate())
    }

    @Test fun nightOffConsumesNoStockAndGetsNoDefrost() {
        val weeks = listOf(
            ReminderWeek(
                week1Start,
                listOf(
                    PlannedNight("MON", porkDinner.id, notCooking = true, notCookingReason = "eating out"),
                    PlannedNight("WED", porkDinner.id),
                ),
            ),
        )
        val reminders = plan(
            settings = ReminderSettings(defrostEnabled = true),
            weeks = weeks,
            stock = mapOf(pork.id to Quantity(1.5, MeasureUnit.POUND)), // exactly one dinner's worth
            now = LocalDateTime.of(2026, 10, 17, 10, 0),
        )
        val defrosts = ofType(reminders, ReminderType.DEFROST)
        assertEquals(1, defrosts.size)
        assertEquals(LocalDate.of(2026, 10, 20), defrosts.single().triggerAt.toLocalDate()) // before Wed
    }

    // ---- Shopping ----

    @Test fun shoppingReminderFiresOnTheShoppingDayAndRepeatsWeekly() {
        val reminders = plan(
            settings = ReminderSettings(shoppingEnabled = true, shoppingDay = "SUN", shoppingTime = "09:00"),
            weeks = emptyList(),
            now = LocalDateTime.of(2026, 10, 17, 10, 0), // Saturday
        )
        val shopping = ofType(reminders, ReminderType.SHOPPING)
        assertEquals(2, shopping.size)
        assertEquals(LocalDateTime.of(2026, 10, 18, 9, 0), shopping[0].triggerAt)
        assertEquals(LocalDateTime.of(2026, 10, 25, 9, 0), shopping[1].triggerAt)
    }

    @Test fun shoppingReminderLaterTodayStillCounts() {
        val reminders = plan(
            settings = ReminderSettings(shoppingEnabled = true, shoppingDay = "SUN", shoppingTime = "09:00"),
            weeks = emptyList(),
            now = LocalDateTime.of(2026, 10, 18, 8, 0), // Sunday, an hour before
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 18, 9, 0),
            ofType(reminders, ReminderType.SHOPPING).first().triggerAt,
        )
    }

    @Test fun shoppingReminderRollsAWeekWhenTodaysTimeHasPassed() {
        val reminders = plan(
            settings = ReminderSettings(shoppingEnabled = true, shoppingDay = "SUN", shoppingTime = "09:00"),
            weeks = emptyList(),
            now = LocalDateTime.of(2026, 10, 18, 10, 0), // Sunday, an hour after
        )
        val shopping = ofType(reminders, ReminderType.SHOPPING)
        assertEquals(LocalDateTime.of(2026, 10, 25, 9, 0), shopping[0].triggerAt)
        assertEquals(LocalDateTime.of(2026, 11, 1, 9, 0), shopping[1].triggerAt)
    }

    // ---- Gates ----

    @Test fun noPermissionProducesNoRemindersAtAll() {
        val weeks = listOf(ReminderWeek(week1Start, listOf(PlannedNight("MON", porkDinner.id))))
        val reminders = plan(
            settings = ReminderSettings(
                dinnerEnabled = true,
                shoppingEnabled = true,
                defrostEnabled = true,
            ),
            weeks = weeks,
            stock = mapOf(pork.id to Quantity(4.5, MeasureUnit.POUND)),
            now = LocalDateTime.of(2026, 10, 17, 10, 0),
            granted = false,
        )
        assertTrue(reminders.isEmpty())
    }

    @Test fun everySwitchOffProducesNoReminders() {
        val weeks = listOf(ReminderWeek(week1Start, listOf(PlannedNight("MON", porkDinner.id))))
        val reminders = plan(
            settings = ReminderSettings(),
            weeks = weeks,
            stock = mapOf(pork.id to Quantity(4.5, MeasureUnit.POUND)),
            now = LocalDateTime.of(2026, 10, 17, 10, 0),
        )
        assertTrue(reminders.isEmpty())
    }

    @Test fun combinedPlanIsSortedByTriggerTime() {
        val weeks = listOf(
            ReminderWeek(
                week1Start,
                listOf(
                    PlannedNight("MON", porkDinner.id),
                    PlannedNight("TUE", turkeyDinner.id),
                ),
            ),
        )
        val reminders = plan(
            settings = ReminderSettings(
                dinnerEnabled = true,
                shoppingEnabled = true,
                shoppingDay = "SUN",
                shoppingTime = "09:00",
                defrostEnabled = true,
            ),
            weeks = weeks,
            stock = mapOf(pork.id to Quantity(4.5, MeasureUnit.POUND)),
            now = LocalDateTime.of(2026, 10, 17, 10, 0),
        )
        assertTrue(reminders.isNotEmpty())
        assertEquals(reminders.sortedBy { it.triggerAt }, reminders)
        // Sunday shopping 09:00, Sunday defrost 20:00, Monday dinner 16:30...
        assertEquals(ReminderType.SHOPPING, reminders.first().type)
        assertEquals(ReminderType.DEFROST, reminders[1].type)
        assertEquals(ReminderType.DINNER, reminders[2].type)
    }
}
