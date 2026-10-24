package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.ScanSubstitution
import org.junit.Assert.assertEquals
import org.junit.Test

/** PR v0.4.4: remembered scan substitutions convert between model and entity losslessly. */
class ScanSubstitutionMapperTest {

    @Test fun substitutionRoundTripsThroughEntity() {
        val record = ScanSubstitution(
            id = 7L,
            planId = "plan-1",
            originalItemId = "chicken-breast",
            substituteItemId = "user-chicken-drumsticks",
            recipeId = "sheet-pan-chicken",
            substitutePriceCents = 499,
            createdAtMillis = 123456789L,
        )
        val entity = with(SeedMapper) { record.toEntity() }
        assertEquals(7L, entity.id)
        assertEquals("plan-1", entity.planId)
        assertEquals("chicken-breast", entity.originalItemId)
        assertEquals("user-chicken-drumsticks", entity.substituteItemId)
        assertEquals("sheet-pan-chicken", entity.recipeId)
        assertEquals(499, entity.substitutePriceCents)
        assertEquals(123456789L, entity.createdAtMillis)
        assertEquals(record, with(SeedMapper) { entity.toModel() })
    }

    @Test fun restoredSubstitutionHasNoPlanOrRecipe() {
        // A record restored from a backup carries no plan id; the
        // nullable context fields must round-trip as null, not crash.
        val record = ScanSubstitution(
            originalItemId = "chicken-breast",
            substituteItemId = "user-chicken-drumsticks",
            recipeId = null,
            substitutePriceCents = 450,
            createdAtMillis = 5L,
        )
        assertEquals(record, with(SeedMapper) { record.toEntity().toModel() })
    }
}
