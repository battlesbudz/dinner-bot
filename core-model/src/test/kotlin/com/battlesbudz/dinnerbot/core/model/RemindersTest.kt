package com.battlesbudz.dinnerbot.core.model

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {

    @Test fun defaultsHaveEveryReminderOff() {
        val settings = ReminderSettings()
        assertFalse(ReminderLogic.anyEnabled(settings))
        assertEquals("SUN", settings.shoppingDay)
    }

    @Test fun anyEnabledSeesEachSwitch() {
        assertTrue(ReminderLogic.anyEnabled(ReminderSettings(dinnerEnabled = true)))
        assertTrue(ReminderLogic.anyEnabled(ReminderSettings(shoppingEnabled = true)))
        assertTrue(ReminderLogic.anyEnabled(ReminderSettings(defrostEnabled = true)))
        assertFalse(ReminderLogic.anyEnabled(ReminderSettings(dinnerTime = "07:00")))
    }

    @Test fun warningOnlyWhenAReminderIsOnWithoutPermission() {
        val on = ReminderSettings(dinnerEnabled = true)
        assertTrue(ReminderLogic.warningNeeded(on, notificationsGranted = false))
        assertFalse(ReminderLogic.warningNeeded(on, notificationsGranted = true))
        // Nothing on means nothing to warn about, permission or not.
        assertFalse(ReminderLogic.warningNeeded(ReminderSettings(), notificationsGranted = false))
    }

    @Test fun timesParseFormatAndDisplay() {
        assertEquals(LocalTime.of(16, 30), ReminderTimes.parseOrNull("16:30"))
        assertEquals(LocalTime.of(9, 0), ReminderTimes.parseOrNull("09:00"))
        assertNull(ReminderTimes.parseOrNull("tea time"))
        assertNull(ReminderTimes.parseOrNull(""))
        assertEquals(
            LocalTime.of(18, 0),
            ReminderTimes.parseOrDefault("junk", LocalTime.of(18, 0)),
        )
        assertEquals("16:30", ReminderTimes.format(LocalTime.of(16, 30)))
        assertEquals("09:05", ReminderTimes.format(LocalTime.of(9, 5)))
        assertEquals("4:30 PM", ReminderTimes.display(LocalTime.of(16, 30)))
        assertEquals("9:00 AM", ReminderTimes.display(LocalTime.of(9, 0)))
        assertEquals("12:00 PM", ReminderTimes.display(LocalTime.of(12, 0)))
        assertEquals("12:00 AM", ReminderTimes.display(LocalTime.of(0, 0)))
    }
}
