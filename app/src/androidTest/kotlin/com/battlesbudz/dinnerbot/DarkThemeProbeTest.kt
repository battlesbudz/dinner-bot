package com.battlesbudz.dinnerbot

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.battlesbudz.dinnerbot.ui.DinnerBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Theme-level pin for dark mode (v0.6.1): DinnerBotTheme must select
 * the dark scheme under a night configuration and the light scheme
 * under a day one, so the app follows the phone's dark setting with
 * no in-app toggle. The colors asserted here are the schemes' own
 * background pairings; a washed-out or swapped scheme fails here
 * before it can ship.
 */
@RunWith(AndroidJUnit4::class)
class DarkThemeProbeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun configurationWithNight(night: Boolean): Configuration {
        val base = ApplicationProvider.getApplicationContext<Context>().resources.configuration
        val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return Configuration(base).apply {
            uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK) or nightBits
        }
    }

    private fun schemeUnder(night: Boolean): Pair<Color, Color> {
        var background: Color? = null
        var onBackground: Color? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides configurationWithNight(night)) {
                DinnerBotTheme {
                    background = MaterialTheme.colorScheme.background
                    onBackground = MaterialTheme.colorScheme.onBackground
                }
            }
        }
        composeRule.runOnIdle {
            assertNotNull(background)
            assertNotNull(onBackground)
        }
        return background!! to onBackground!!
    }

    @Test fun nightConfigurationSelectsTheDarkScheme() {
        val (background, onBackground) = schemeUnder(night = true)
        assertEquals(Color(0xFF121211), background)
        assertEquals(Color(0xFFF2F1EC), onBackground)
    }

    @Test fun dayConfigurationKeepsTheLightScheme() {
        val (background, onBackground) = schemeUnder(night = false)
        assertEquals(Color(0xFFFFFFFF), background)
        assertEquals(Color(0xFF141414), onBackground)
    }
}
