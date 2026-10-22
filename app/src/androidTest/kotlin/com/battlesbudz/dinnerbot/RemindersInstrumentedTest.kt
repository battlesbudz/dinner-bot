package com.battlesbudz.dinnerbot

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.feature.onboarding.SettingsScreen
import com.battlesbudz.dinnerbot.feature.reminders.PlannedReminder
import com.battlesbudz.dinnerbot.feature.reminders.ReminderScheduler
import com.battlesbudz.dinnerbot.feature.reminders.ReminderType
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase V2-4 flow proof on the emulator: the planner's output is really
 * scheduled as WorkManager work (and a replan replaces it wholesale),
 * the worker runs to success when its time comes, and the settings
 * reminders block asks for the notification permission at the moment a
 * reminder is switched on, saying plainly when notifications are off.
 * Whether a notification visually arrives is a real-device check on
 * Andrea's phone, named as such in the release notes.
 */
@RunWith(AndroidJUnit4::class)
class RemindersInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private var testDriverAvailable = false

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDriverAvailable = try {
            val config = Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build()
            WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
            WorkManagerTestInitHelper.getTestDriver(context) != null
        } catch (e: IllegalStateException) {
            // The app's own WorkManager instance was already initialized
            // by the platform; scheduling assertions below run against it
            // and only the test-driver fast-forward is unavailable.
            false
        }
    }

    private fun workInfos(name: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get(15, TimeUnit.SECONDS)

    @Test fun plannedRemindersAreScheduledAndAReplanReplacesThem() {
        val now = LocalDateTime.now()
        val dinner = PlannedReminder(
            type = ReminderType.DINNER,
            triggerAt = now.plusHours(2),
            title = "Tonight's dinner",
            text = "Tonight: Turkey spaghetti",
        )
        val shopping = PlannedReminder(
            type = ReminderType.SHOPPING,
            triggerAt = now.plusHours(3),
            title = "Shopping reminder",
            text = "Today is shopping day. Your Dinner Bot list is ready.",
        )
        ReminderScheduler.reschedule(context, listOf(dinner, shopping), now)

        val dinnerInfos = workInfos(dinner.uniqueName)
        assertEquals(1, dinnerInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, dinnerInfos.single().state)
        val delayMillis = dinnerInfos.single().initialDelayMillis
        // About two hours out, within WorkManager's scheduling tolerance.
        assertTrue("delay was $delayMillis", delayMillis in 1000L * 60 * 100..1000L * 60 * 140)
        assertEquals(1, workInfos(shopping.uniqueName).size)

        // A wholesale replan with nothing in it cancels the whole set.
        ReminderScheduler.reschedule(context, emptyList(), now)
        assertTrue(
            workInfos(dinner.uniqueName).none { it.state == WorkInfo.State.ENQUEUED },
        )
        assertTrue(
            workInfos(shopping.uniqueName).none { it.state == WorkInfo.State.ENQUEUED },
        )
    }

    @Test fun workerRunsToSuccessWhenItsTimeComes() {
        assumeTrue("needs the WorkManager test driver", testDriverAvailable)
        val now = LocalDateTime.now()
        val reminder = PlannedReminder(
            type = ReminderType.DEFROST,
            triggerAt = now.plusHours(5),
            title = "Defrost for tomorrow",
            text = "Move the Pork tenderloin to the fridge tonight.",
        )
        ReminderScheduler.reschedule(context, listOf(reminder), now)
        val info = workInfos(reminder.uniqueName).single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)

        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(info.id)

        // On the API 34 emulator the test app holds no POST_NOTIFICATIONS
        // grant, so the worker takes its quiet no-op path; either way it
        // must finish SUCCEEDED, never crash or retry.
        val deadline = System.currentTimeMillis() + 15_000
        var state = workInfos(reminder.uniqueName).singleOrNull()?.state
        while (state != WorkInfo.State.SUCCEEDED && System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            state = WorkManager.getInstance(context).getWorkInfoById(info.id)
                .get(15, TimeUnit.SECONDS)?.state
        }
        assertEquals(WorkInfo.State.SUCCEEDED, state)
    }

    @Test fun switchingAReminderOnAsksForPermissionAndWarnsWhileItIsMissing() {
        val requests = AtomicInteger(0)
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    initial = HouseholdSettings(onboardingComplete = true),
                    notificationsGranted = false,
                    onRequestNotificationPermission = { requests.incrementAndGet() },
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithTag("reminder-dinner-switch")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        // The ask happens at the switch-on moment, and the plain warning
        // is on screen while a reminder is on without the permission.
        assertEquals(1, requests.get())
        composeRule.onNodeWithTag("reminders-permission-warning")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test fun noWarningAndNoAskWhenNotificationsAreGranted() {
        val requests = AtomicInteger(0)
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    initial = HouseholdSettings(onboardingComplete = true),
                    notificationsGranted = true,
                    onRequestNotificationPermission = { requests.incrementAndGet() },
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithTag("reminder-dinner-switch")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(0, requests.get())
        composeRule.onNodeWithTag("reminders-permission-warning").assertDoesNotExist()
    }
}
