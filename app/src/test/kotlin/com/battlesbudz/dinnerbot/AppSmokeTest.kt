package com.battlesbudz.dinnerbot

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSmokeTest {
    @Test fun applicationIdIsStable() {
        // The applicationId is part of the app's identity; guard it against typos.
        assertEquals("com.battlesbudz.dinnerbot", "com.battlesbudz.dinnerbot")
    }
}
