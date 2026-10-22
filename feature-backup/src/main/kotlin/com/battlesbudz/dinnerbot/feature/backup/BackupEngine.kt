package com.battlesbudz.dinnerbot.feature.backup

import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.RecipeRating
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One recipe's thumbs rating inside a backup file (Phase V2-1). */
@Serializable
data class BackupRecipeRating(
    val recipeId: String,
    val rating: RecipeRating,
)

/**
 * One JSON file carrying the user's own data: settings, the full price book
 * (including Andrea's corrections), the pantry checklist, Sam's bulk stock,
 * and recipe thumbs ratings. Moving it goes through the Android system file
 * picker only; Dinner Bot has no network permission, so a backup can never
 * leave the phone unless the user picks a destination herself.
 *
 * Format version 2 (v0.2.0) adds recipeRatings. Version 1 files carry no
 * ratings field and import with ratings empty.
 */
@Serializable
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val settings: HouseholdSettings,
    val priceItems: List<PriceItem>,
    val pantryItems: List<PantryItem>,
    val bulkStock: List<BulkStock>,
    val recipeRatings: List<BackupRecipeRating> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION: Int = 2
    }
}

object BackupEngine {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun export(data: BackupData): String = json.encodeToString(BackupData.serializer(), data)

    /**
     * Parse and validate a backup file. Throws IllegalArgumentException with a
     * plain message when the file is not a usable Dinner Bot backup.
     */
    fun import(text: String): BackupData {
        val data = try {
            json.decodeFromString(BackupData.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("That file is not a Dinner Bot backup.", e)
        }
        require(data.version in 1..BackupData.CURRENT_VERSION) {
            "That backup is from a newer version of Dinner Bot."
        }
        require(data.priceItems.isNotEmpty()) { "That backup has no price book in it." }
        val ids = data.priceItems.map { it.id }
        require(ids.size == ids.toSet().size) { "That backup has duplicate price items." }
        for (item in data.priceItems) {
            require(item.packPriceCents > 0) { "That backup has a broken price for ${item.name}." }
            require(item.packQuantity.amount > 0.0) { "That backup has a broken pack size for ${item.name}." }
        }
        val ratingIds = data.recipeRatings.map { it.recipeId }
        require(ratingIds.size == ratingIds.toSet().size) { "That backup has duplicate recipe ratings." }
        return data
    }
}
