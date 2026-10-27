package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase C1: stores are user data. The preset catalogue offers the chains
 * Battles named; custom stores are created from a name; the default store
 * is always the user's first chosen store, never a code-level chain.
 */
class StoresTest {

    @Test fun presetsOfferEveryNamedChain() {
        val names = PresetStores.ALL.map { it.name }
        for (expected in listOf(
            "Aldi", "Walmart", "Target", "Kroger", "Costco", "Sam's Club",
            "BJ's Wholesale", "Trader Joe's", "Whole Foods", "Albertsons/Safeway",
            "H-E-B", "Publix", "Meijer", "Wegmans", "Hy-Vee", "Food Lion",
            "WinCo", "Stop & Shop",
        )) {
            assertTrue("$expected missing from presets", expected in names)
        }
        // Ids are unique slugs.
        assertEquals(PresetStores.ALL.size, PresetStores.ALL.map { it.id }.toSet().size)
    }

    @Test fun customStoreIsCreatedFromAName() {
        val store = StoreIds.customStore("  Corner Market ", emptyList())
        assertEquals("corner-market", store.id)
        assertEquals("Corner Market", store.name)
    }

    @Test fun customStoreNeverCollidesWithExistingIds() {
        val existing = PresetStores.ALL.map { it.id } + "corner-market"
        val store = StoreIds.customStore("Corner Market", existing)
        assertEquals("corner-market-2", store.id)
        val third = StoreIds.customStore("Corner Market", existing + store.id)
        assertEquals("corner-market-3", third.id)
        // A custom store named like a preset gets its own id too.
        val aldiLike = StoreIds.customStore("Aldi", PresetStores.ALL.map { it.id })
        assertEquals("aldi-2", aldiLike.id)
    }

    @Test fun defaultStoreIsTheUsersFirstChosenStore() {
        val settings = HouseholdSettings(
            householdSize = 4,
            stores = listOf(Store("my-market", "My Market"), PresetStores.WEGMANS),
        )
        assertEquals("my-market", settings.firstStore?.id)
        // No stores chosen: there is no code-level default chain.
        assertEquals(null, HouseholdSettings(householdSize = 4).firstStore)
    }

    @Test fun legacyEnumNamesNormalizeToPresetIds() {
        assertEquals("aldi", StoreIds.normalize("ALDI"))
        assertEquals("walmart", StoreIds.normalize("WALMART"))
        assertEquals("sams-club", StoreIds.normalize("SAMS_CLUB"))
        assertEquals("aldi", StoreIds.normalize("aldi"))
        assertEquals("my-market", StoreIds.normalize("my-market"))
    }

    @Test fun displayNamePrefersTheUsersOwnStores() {
        val mine = listOf(Store("aldi", "My Aldi"))
        assertEquals("My Aldi", StoreIds.displayName("aldi", mine))
        assertEquals("Wegmans", StoreIds.displayName("wegmans", mine))
        assertEquals("mystery-store", StoreIds.displayName("mystery-store", mine))
    }
}
