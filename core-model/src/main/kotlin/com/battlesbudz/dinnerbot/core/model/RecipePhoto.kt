package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

/**
 * One household photo attached to a recipe (Epic issue #10, decision
 * D-V2-2). Photos are the household's own, taken as dinners are cooked
 * and attached from the phone's photos; no photo is bundled with the
 * app, and a recipe with no photo renders exactly as it always has.
 *
 * The image file itself lives in app-local storage (see [RecipePhotos]);
 * this record is the reference to it plus its credit, kept in the local
 * database next to the recipe's rating. Photos are not part of the
 * backup file: the JSON backup carries text data only, so importing a
 * backup neither creates nor removes photo records, and a photo taken
 * on this phone stays on this phone.
 */
@Serializable
data class RecipePhoto(
    val recipeId: String,
    /** File name inside the app's recipe-photos folder; deterministic per recipe. */
    val fileName: String,
    /** Credit stored with the photo, per the project's licensing rule. */
    val credit: String,
    val updatedAtMillis: Long,
)

/** Pure rules for recipe photo references (Epic issue #10). */
object RecipePhotos {

    /** App-local folder (under the app's files directory) holding photo files. */
    const val DIRECTORY_NAME: String = "recipe_photos"

    /**
     * The credit household photos carry: the photos are the household's
     * own, taken of the actual dish from the actual recipe (SCOPE_PLAN
     * section 8's rule: photos are ours or openly licensed with credit
     * stored in the data).
     */
    const val DEFAULT_CREDIT: String = "Household photo"

    /**
     * The one file name a recipe's photo is stored under. Deterministic
     * so attaching a new photo replaces the old file instead of piling
     * up orphans, and removing a photo is one delete.
     */
    fun fileNameFor(recipeId: String): String {
        require(recipeId.isNotBlank()) { "recipeId must not be blank" }
        return "$recipeId.jpg"
    }

    /** The credit to store: the household's words when given, the default otherwise. */
    fun creditOrDefault(credit: String?): String =
        credit?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_CREDIT
}
