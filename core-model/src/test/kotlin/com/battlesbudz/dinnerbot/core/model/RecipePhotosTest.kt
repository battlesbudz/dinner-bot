package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Epic issue #10: photo reference rules — one deterministic file per recipe, household credit by default. */
class RecipePhotosTest {

    @Test fun fileNameIsDeterministicPerRecipe() {
        assertEquals("turkey-spaghetti.jpg", RecipePhotos.fileNameFor("turkey-spaghetti"))
        assertEquals(
            RecipePhotos.fileNameFor("turkey-spaghetti"),
            RecipePhotos.fileNameFor("turkey-spaghetti"),
        )
    }

    @Test fun blankRecipeIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { RecipePhotos.fileNameFor("  ") }
    }

    @Test fun creditFallsBackToHouseholdPhoto() {
        assertEquals("Household photo", RecipePhotos.DEFAULT_CREDIT)
        assertEquals("Household photo", RecipePhotos.creditOrDefault(null))
        assertEquals("Household photo", RecipePhotos.creditOrDefault("   "))
        assertEquals("Andrea's photo", RecipePhotos.creditOrDefault("  Andrea's photo  "))
    }
}
