package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test fun plusAndMinus() {
        assertEquals(Money(450), Money(200) + Money(250))
        assertEquals(Money(-50), Money(200) - Money(250))
    }

    @Test fun timesIntAndDouble() {
        assertEquals(Money(594), Money(198) * 3)
        assertEquals(Money(99), Money(198) * 0.5)
    }

    @Test fun portionCostRoundsToNearestCent() {
        // $21.68 pack, use 1.25 of 9 lb -> $3.01
        assertEquals(Money(301), Money(2168).portion(1.25, 9.0))
    }

    @Test fun formatsDollars() {
        assertEquals("$3.96", Money(396).format())
        assertEquals("$0.98", Money(98).format())
        assertEquals("$21.68", Money(2168).format())
        assertEquals("-$1.50", Money(-150).format())
    }

    @Test fun comparisonWorks() {
        assertTrue(Money(100) < Money(200))
        assertEquals(0, Money(50).compareTo(Money(50)))
    }
}
