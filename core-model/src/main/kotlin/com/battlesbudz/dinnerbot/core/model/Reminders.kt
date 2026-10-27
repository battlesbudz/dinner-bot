package com.battlesbudz.dinnerbot.core.model

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

/**
 * The household's local reminders (Phase V2-4). Three independent
 * reminders, each with its own on/off switch and time, all scheduled on
 * the phone itself: there is no server and nothing leaves the device.
 *
 * Stored with the household settings as one JSON blob, so every reminder
 * starts switched off and an older settings row (or backup) without the
 * blob simply decodes to these defaults.
 *
 * Times are "HH:mm" 24-hour strings, the same shape the settings screen
 * writes; day codes are the household's usual "MON".."SUN" codes.
 */
@Serializable
data class ReminderSettings(
    /** Dinner reminder on cook nights, naming tonight's dinner from the current plan. */
    val dinnerEnabled: Boolean = false,
    val dinnerTime: String = "16:30",
    /** Shopping reminder on the household's shopping day. */
    val shoppingEnabled: Boolean = false,
    /** Day code ("MON".."SUN") the household shops on. */
    val shoppingDay: String = "SUN",
    val shoppingTime: String = "09:00",
    /**
     * Defrost reminder on the evening before a cook night whose dinner
     * draws on frozen bulk stock, naming the item to move.
     */
    val defrostEnabled: Boolean = false,
    val defrostTime: String = "20:00",
)

/** Parsing and display for the "HH:mm" time strings [ReminderSettings] stores. */
object ReminderTimes {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseOrNull(value: String): LocalTime? =
        runCatching { LocalTime.parse(value.trim(), formatter) }.getOrNull()

    /** A stored string that does not parse reads as [fallback] rather than crashing a schedule. */
    fun parseOrDefault(value: String, fallback: LocalTime): LocalTime =
        parseOrNull(value) ?: fallback

    /** The stored "HH:mm" shape for [time]. */
    fun format(time: LocalTime): String = time.format(formatter)

    /** How a time reads on screen, e.g. "4:30 PM". */
    fun display(time: LocalTime): String {
        val hour12 = when (val h = time.hour % 12) {
            0 -> 12
            else -> h
        }
        val amPm = if (time.hour < 12) "AM" else "PM"
        return "%d:%02d %s".format(hour12, time.minute, amPm)
    }

    fun displayOrDefault(value: String, fallback: LocalTime): String =
        display(parseOrDefault(value, fallback))
}

/** Pure state questions the settings screen and the scheduler both ask. */
object ReminderLogic {
    fun anyEnabled(settings: ReminderSettings): Boolean =
        settings.dinnerEnabled || settings.shoppingEnabled || settings.defrostEnabled

    /**
     * True when a reminder is switched on but notifications cannot appear.
     * [notificationsGranted] is the EFFECTIVE state the caller computed:
     * true on Android versions with no notification runtime permission, or
     * when the permission has been granted. The settings screen shows a
     * plain explanation whenever this is true.
     */
    fun warningNeeded(settings: ReminderSettings, notificationsGranted: Boolean): Boolean =
        anyEnabled(settings) && !notificationsGranted
}
