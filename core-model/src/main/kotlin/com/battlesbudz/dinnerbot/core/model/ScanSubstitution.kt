package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

/**
 * One remembered barcode-scan substitution (PR v0.4.4, the user's field
 * report from the v0.4.2 barcode build).
 *
 * the user's rule, in substance: a substitution made from a shopping line
 * is THIS WEEK ONLY — the planned line comes off this week's list, the
 * scanned product goes on at the typed price, and the recipe and future
 * weeks are never changed. But the app should remember the swap in a
 * simple way, so the next time the same situation arises (a scan from a
 * line for the same original item, in the same recipe context when one
 * is known) the remembered substitute is surfaced as the first
 * suggestion: "Last time you swapped in chicken drumsticks for this."
 * A suggestion only; nothing ever applies itself.
 *
 * The record is deliberately small: what was planned, what was bought
 * instead, the dinner it was for when that is unambiguous, what was paid,
 * and when. [planId] ties the record to the week it happened in; backup
 * files do not carry it (a restored record keeps its memory value without
 * pointing at a plan that may not exist in the restore).
 */
@Serializable
data class ScanSubstitution(
    /** Room row id; 0 until persisted. */
    val id: Long = 0,
    /** The week the swap happened in, when known. Not carried in backups. */
    val planId: String? = null,
    /** Price book id of the planned item that came off the list. */
    val originalItemId: String,
    /** Price book id of the item bought instead. */
    val substituteItemId: String,
    /**
     * The recipe the swap was made for, when exactly one planned dinner
     * using the original item was on the week; null when the context was
     * ambiguous or there was none. Used only to prefer the most relevant
     * memory when several exist for the same original item.
     */
    val recipeId: String? = null,
    /** The shelf price typed at the scan, cents, for the substitute's pack. */
    val substitutePriceCents: Int,
    /** When the swap happened, epoch millis. */
    val createdAtMillis: Long,
)
