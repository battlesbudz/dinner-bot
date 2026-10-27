package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

/**
 * One photo attached to a recipe (Epic issue #10, decision
 * D-V2-2). Household photos are the household's own, taken as dinners
 * are cooked and attached from the phone's photos. On the commercial
 * branch the starter library also ships with a bundled photo per
 * recipe (Phase C2, see [SeedPhotoCredit]): at first install the
 * bundled image is copied into the same local storage and recorded
 * with its credit, after which it behaves exactly like a household
 * photo — the user can replace it or remove it, and a removal sticks.
 * A recipe with no photo renders exactly as it always has.
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

/**
 * One bundled starter-library photo (Phase C2): the asset file that
 * ships with the app for a seed recipe, and the credit it carries.
 * The canonical list is seed-data/photo-credits.json; the images live
 * in seed-data/photos/. Stock photos are openly licensed and credited
 * to their author; the rest are AI generated, and the credit says so.
 */
@Serializable
data class SeedPhotoCredit(
    val recipeId: String,
    /** Asset path of the bundled image, relative to the assets root. */
    val file: String,
    /** Credit stored on the photo record and shown with the photo. */
    val credit: String,
    /** "stock" or "ai". */
    val source: String,
    /** Photo page URL for stock photos; null for generated images. */
    val sourceUrl: String? = null,
    /** License name for stock photos (e.g. "CC BY 2.0"); null for generated images. */
    val license: String? = null,
)

/** The bundled starter-photo manifest (seed-data/photo-credits.json). */
@Serializable
data class SeedPhotosFile(
    val version: Int,
    val photos: List<SeedPhotoCredit>,
)

/** Pure rules for recipe photo references (Epic issue #10). */
object RecipePhotos {

    /**
     * App-local folder (under the app's no-backup files directory, so
     * Android Auto Backup never uploads a photo off the phone) holding
     * photo files.
     */
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
