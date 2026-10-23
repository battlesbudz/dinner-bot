package com.battlesbudz.dinnerbot.feature.backup

import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.CookCheckoff
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.RanOutEvent
import com.battlesbudz.dinnerbot.core.model.RecipeRating
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One recipe's thumbs rating inside a backup file (Phase V2-1). */
@Serializable
data class BackupRecipeRating(
    val recipeId: String,
    val rating: RecipeRating,
)

/** One learned barcode link inside a backup file (Phase V2-5). */
@Serializable
data class BackupBarcodeLink(
    val barcode: String,
    val itemId: String,
)

/**
 * One JSON file carrying the user's own data: settings, the full price book
 * (including Andrea's corrections), the pantry checklist, Sam's bulk stock,
 * recipe thumbs ratings, learned barcode links, and the Sunday
 * reconciliation state (ran-out events and cook check-offs, Phase V2-7, so
 * a phone swap mid-week does not lose the ran-out list). Moving it goes
 * through the Android system file picker only; a backup can never leave
 * the phone unless the user picks a destination herself.
 *
 * Format version 2 (v0.2.0) adds recipeRatings. Version 1 files carry no
 * ratings field and import with ratings empty.
 *
 * Format version 3 (v0.3.0) adds barcodeLinks. Older files carry no links
 * field and import with links empty.
 *
 * Format version 4 (v0.4.0) adds ranOutEvents and cookCheckoffs. Older
 * files import with both empty.
 */
@Serializable
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val settings: HouseholdSettings,
    val priceItems: List<PriceItem>,
    val pantryItems: List<PantryItem>,
    val bulkStock: List<BulkStock>,
    val recipeRatings: List<BackupRecipeRating> = emptyList(),
    val barcodeLinks: List<BackupBarcodeLink> = emptyList(),
    val ranOutEvents: List<RanOutEvent> = emptyList(),
    val cookCheckoffs: List<CookCheckoff> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION: Int = 4
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
        val linkCodes = data.barcodeLinks.map { it.barcode }
        require(linkCodes.size == linkCodes.toSet().size) { "That backup has duplicate barcode links." }
        val itemIds = ids.toSet()
        for (link in data.barcodeLinks) {
            require(link.barcode.isNotBlank()) { "That backup has a barcode link with no barcode." }
            require(link.itemId in itemIds) {
                "That backup links a barcode to an item that is not in its price book."
            }
        }
        val eventIds = data.ranOutEvents.map { it.id }
        require(eventIds.size == eventIds.toSet().size) { "That backup has duplicate ran-out records." }
        for (event in data.ranOutEvents) {
            require(event.itemKey.isNotBlank()) { "That backup has a ran-out record with no item." }
            require(event.dateMarked.isNotBlank()) { "That backup has a ran-out record with no date." }
        }
        val checkoffKeys = data.cookCheckoffs.map { Triple(it.planId, it.day, it.itemKey) }
        require(checkoffKeys.size == checkoffKeys.toSet().size) {
            "That backup has duplicate cook check-offs."
        }
        return data
    }
}
