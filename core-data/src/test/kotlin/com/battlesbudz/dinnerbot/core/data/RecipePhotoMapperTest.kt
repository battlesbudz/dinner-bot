package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.RecipePhoto
import org.junit.Assert.assertEquals
import org.junit.Test

/** Epic issue #10: photo references convert between model and entity losslessly. */
class RecipePhotoMapperTest {

    @Test fun photoRoundTripsThroughEntity() {
        val photo = RecipePhoto(
            recipeId = "turkey-spaghetti",
            fileName = "turkey-spaghetti.jpg",
            credit = "Household photo",
            updatedAtMillis = 99L,
        )
        val entity = with(SeedMapper) { photo.toEntity() }
        assertEquals("turkey-spaghetti", entity.recipeId)
        assertEquals("turkey-spaghetti.jpg", entity.fileName)
        assertEquals("Household photo", entity.credit)
        assertEquals(99L, entity.updatedAtMillis)
        assertEquals(photo, with(SeedMapper) { entity.toModel() })
    }
}
