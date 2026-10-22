package com.battlesbudz.dinnerbot.feature.reminders

import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.ReminderSettings
import com.battlesbudz.dinnerbot.core.model.ReminderTimes
import com.battlesbudz.dinnerbot.core.model.Store
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class ReminderType { DINNER, SHOPPING, DEFROST }

/** One reminder that should fire: what it says and exactly when. */
data class PlannedReminder(
    val type: ReminderType,
    val triggerAt: LocalDateTime,
    val title: String,
    val text: String,
) {
    /** Stable identity across reschedules, so re-planning replaces rather than duplicates. */
    val uniqueName: String get() = "reminder-${type.name}-${triggerAt.toLocalDate()}"
}

/** One stored week plan, reduced to what reminder planning reads. */
data class ReminderWeek(
    val weekStart: LocalDate,
    val nights: List<PlannedNight>,
)

/**
 * Which reminders a given state of the world produces. Pure Kotlin over
 * core-model types: no Android, no database, unit-tested on the JVM. The
 * Android scheduler in this package only carries out what this planner
 * decides.
 *
 * Rules (Scope A6):
 * - DINNER fires on each cook night of the stored plans at the dinner
 *   time, naming that night's dinner. A night marked not cooking is
 *   skipped, exactly like the planner skips it.
 * - DEFROST fires on the evening before a cook night whose dinner draws
 *   on frozen Sam's Club bulk stock, naming the item to move. "Draws on
 *   frozen stock" mirrors [Costing] precisely: Sam's items are drawn
 *   from tracked stock first, in night order across the whole stored
 *   horizon, and only items carrying freeze guidance count as frozen.
 *   An item the week buys fresh draws nothing and gets no reminder, and
 *   stock an earlier night used up leaves nothing for a later night.
 * - SHOPPING fires on the household's shopping day at the shopping time,
 *   the next two occurrences, so a weekly rhythm survives a week in
 *   which the app is never opened.
 * - Only reminders still in the future are returned, sorted by time.
 * - With the notification permission missing, nothing is produced at
 *   all: declined permission means reminders are unavailable, and the
 *   settings screen says so plainly.
 */
object ReminderPlanner {
    private const val EPSILON = 1e-9

    fun plan(
        settings: ReminderSettings,
        householdSize: Int,
        weeks: List<ReminderWeek>,
        recipesById: Map<String, Recipe>,
        itemsById: Map<String, PriceItem>,
        stockOnHand: Map<String, Quantity>,
        now: LocalDateTime,
        notificationsGranted: Boolean,
    ): List<PlannedReminder> {
        if (!notificationsGranted) return emptyList()
        val reminders = mutableListOf<PlannedReminder>()

        // Every cooking night of the horizon, in date order, paired with
        // its date. Both DINNER and DEFROST read from this one walk.
        val cookNights: List<Pair<LocalDate, PlannedNight>> = weeks
            .sortedBy { it.weekStart }
            .flatMap { week -> week.nights.mapNotNull { night -> dateOf(week.weekStart, night.day)?.let { it to night } } }
            .filter { !it.second.notCooking }
            .sortedBy { it.first }

        if (settings.dinnerEnabled) {
            val time = ReminderTimes.parseOrDefault(settings.dinnerTime, LocalTime.of(16, 30))
            for ((date, night) in cookNights) {
                val recipe = recipesById[night.recipeId] ?: continue
                val trigger = date.atTime(time)
                if (trigger.isAfter(now)) {
                    reminders += PlannedReminder(
                        type = ReminderType.DINNER,
                        triggerAt = trigger,
                        title = "Tonight's dinner",
                        text = "Tonight: ${recipe.title}",
                    )
                }
            }
        }

        if (settings.defrostEnabled) {
            val time = ReminderTimes.parseOrDefault(settings.defrostTime, LocalTime.of(20, 0))
            // Tracked bulk stock in base units, drawn down night by night
            // exactly the way Costing draws it for the week's costing.
            val stockBase = stockOnHand.mapValues { it.value.toBase() }.toMutableMap()
            for ((date, night) in cookNights) {
                val recipe = recipesById[night.recipeId] ?: continue
                val (needs, _) = Costing.aggregateNeeds(listOf(recipe), householdSize, itemsById)
                val frozenDrawn = mutableListOf<PriceItem>()
                for (need in needs) {
                    val item = need.item
                    if (item.homeStore != Store.SAMS_CLUB) continue
                    val onHand = stockBase[item.id] ?: 0.0
                    if (onHand <= 0.0) continue
                    val covered = minOf(need.neededBase, onHand)
                    if (covered <= EPSILON) continue
                    stockBase[item.id] = onHand - covered
                    if (item.freezeNote != null) frozenDrawn += item
                }
                if (frozenDrawn.isEmpty()) continue
                val trigger = date.minusDays(1).atTime(time)
                if (trigger.isAfter(now)) {
                    val names = joinNames(frozenDrawn.map { it.name })
                    reminders += PlannedReminder(
                        type = ReminderType.DEFROST,
                        triggerAt = trigger,
                        title = "Defrost for tomorrow",
                        text = "Move the $names to the fridge tonight.",
                    )
                }
            }
        }

        if (settings.shoppingEnabled) {
            val time = ReminderTimes.parseOrDefault(settings.shoppingTime, LocalTime.of(9, 0))
            val day = dayOfWeek(settings.shoppingDay)
            if (day != null) {
                var date = now.toLocalDate().with(java.time.temporal.TemporalAdjusters.nextOrSame(day))
                if (!date.atTime(time).isAfter(now)) date = date.plusWeeks(1)
                repeat(2) { index ->
                    reminders += PlannedReminder(
                        type = ReminderType.SHOPPING,
                        triggerAt = date.plusWeeks(index.toLong()).atTime(time),
                        title = "Shopping reminder",
                        text = "Today is shopping day. Your Dinner Bot list is ready.",
                    )
                }
            }
        }

        return reminders.sortedBy { it.triggerAt }
    }

    /** The date of [dayCode] ("MON".."SUN") inside the week starting [weekStart]. */
    private fun dateOf(weekStart: LocalDate, dayCode: String): LocalDate? {
        val day = dayOfWeek(dayCode) ?: return null
        val shift = (day.value - weekStart.dayOfWeek.value + 7) % 7
        return weekStart.plusDays(shift.toLong())
    }

    private fun dayOfWeek(code: String): DayOfWeek? = when (code.trim().uppercase()) {
        "MON" -> DayOfWeek.MONDAY
        "TUE" -> DayOfWeek.TUESDAY
        "WED" -> DayOfWeek.WEDNESDAY
        "THU" -> DayOfWeek.THURSDAY
        "FRI" -> DayOfWeek.FRIDAY
        "SAT" -> DayOfWeek.SATURDAY
        "SUN" -> DayOfWeek.SUNDAY
        else -> null
    }

    /** "Pork tenderloin", "Pork tenderloin and Chicken thighs", "A, B and C". */
    private fun joinNames(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        2 -> "${names[0]} and ${names[1]}"
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
}
