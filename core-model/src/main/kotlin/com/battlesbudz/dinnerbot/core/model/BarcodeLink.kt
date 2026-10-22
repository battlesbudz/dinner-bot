package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

/**
 * A learned link between a product barcode and one price book item
 * (Phase V2-5). Barcodes mean nothing on their own: the first time a
 * code is scanned, Andrea says which price book item it is, and the
 * link is remembered on the phone from then on. Links are her data:
 * they live in the local database and ride along in backup
 * export / import, so a new phone keeps them.
 */
@Serializable
data class BarcodeLink(
    val barcode: String,
    val itemId: String,
    val createdAtMillis: Long,
)
