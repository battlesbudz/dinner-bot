package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

/**
 * One store the user shops at (Phase C1, de-personalization).
 *
 * A store is data, not code: an [id] other records reference (price items
 * carry it as their home store, grocery lines group by it) and the [name]
 * the user sees. Nothing in the app special-cases any store by brand; a
 * warehouse or bulk store is just a store the user shops at.
 */
@Serializable
data class Store(
    val id: String,
    val name: String,
)

/**
 * The preset chains onboarding offers (Phase C1): national chains plus the
 * major regional ones. These are choices presented to the user, never a
 * default: a store only becomes one of the user's stores when they pick
 * it, and anywhere a default store is needed the user's first chosen
 * store is used (see [HouseholdSettings.firstStore]).
 */
object PresetStores {
    val ALDI: Store = Store("aldi", "Aldi")
    val WALMART: Store = Store("walmart", "Walmart")
    val TARGET: Store = Store("target", "Target")
    val KROGER: Store = Store("kroger", "Kroger")
    val COSTCO: Store = Store("costco", "Costco")
    val SAMS_CLUB: Store = Store("sams-club", "Sam's Club")
    val BJS: Store = Store("bjs-wholesale", "BJ's Wholesale")
    val TRADER_JOES: Store = Store("trader-joes", "Trader Joe's")
    val WHOLE_FOODS: Store = Store("whole-foods", "Whole Foods")
    val ALBERTSONS_SAFEWAY: Store = Store("albertsons-safeway", "Albertsons/Safeway")
    val HEB: Store = Store("heb", "H-E-B")
    val PUBLIX: Store = Store("publix", "Publix")
    val MEIJER: Store = Store("meijer", "Meijer")
    val WEGMANS: Store = Store("wegmans", "Wegmans")
    val HY_VEE: Store = Store("hy-vee", "Hy-Vee")
    val FOOD_LION: Store = Store("food-lion", "Food Lion")
    val WINCO: Store = Store("winco", "WinCo")
    val STOP_AND_SHOP: Store = Store("stop-and-shop", "Stop & Shop")

    /** Every preset chain, national first, then regional, in display order. */
    val ALL: List<Store> = listOf(
        ALDI, WALMART, TARGET, KROGER, COSTCO, SAMS_CLUB, BJS, TRADER_JOES,
        WHOLE_FOODS, ALBERTSONS_SAFEWAY,
        HEB, PUBLIX, MEIJER, WEGMANS, HY_VEE, FOOD_LION, WINCO, STOP_AND_SHOP,
    )

    fun byId(id: String): Store? = ALL.firstOrNull { it.id == id }
}

/** Store id helpers: legacy values, slugs, display names, custom stores. */
object StoreIds {

    /**
     * Store ids written before Phase C1 were enum names ("ALDI",
     * "SAMS_CLUB"). They normalize to the preset slug ids so stored data,
     * old backups, and old seed files keep resolving to the same store.
     * Anything else passes through trimmed, exactly as written.
     */
    fun normalize(id: String): String = when (id.trim()) {
        "ALDI" -> PresetStores.ALDI.id
        "WALMART" -> PresetStores.WALMART.id
        "SAMS_CLUB" -> PresetStores.SAMS_CLUB.id
        else -> id.trim()
    }

    /** Slug for a store name: lowercase, words joined with hyphens. */
    fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "store" }

    /**
     * Build the custom store for a user-typed [name]. The id is the name's
     * slug, suffixed until it is free among [existingIds], so a custom
     * store can never shadow a preset or another of the user's stores.
     */
    fun customStore(name: String, existingIds: Collection<String>): Store {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "a store needs a name" }
        val base = slug(trimmed)
        var candidate = base
        var suffix = 2
        while (candidate in existingIds) {
            candidate = "$base-$suffix"
            suffix++
        }
        return Store(id = candidate, name = trimmed)
    }

    /**
     * The display name for a store [id]: the user's own store of that id
     * first, then a preset chain, then the id itself (a store whose record
     * is gone still groups under a readable label, never a blank).
     */
    fun displayName(id: String, userStores: List<Store> = emptyList()): String =
        userStores.firstOrNull { it.id == id }?.name
            ?: PresetStores.byId(id)?.name
            ?: id
}

/**
 * Copy for the seed price book's starting prices (Phase C1): wherever the
 * app presents seed prices as not yet corrected by the user, this is the
 * disclosure. Seed prices start at national averages; the user sets the
 * correct local price the first time they scan an item, and the app
 * remembers that price on later scans.
 */
object SeedPrices {
    const val NATIONAL_AVERAGE_SOURCE: String = "National average"
    const val DISCLOSURE: String =
        "Prices start at national averages. Set the correct local price the " +
            "first time you scan an item, and the app will remember it."
}
