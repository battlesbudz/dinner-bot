package com.battlesbudz.dinnerbot.feature.barcode

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one optional network assist in Dinner Bot (Phase V2-5, Battles's
 * D-V2-3 decision): when the phone has a connection, a scanned barcode
 * may be looked up in Open Food Facts, the public product database.
 * What comes back only ever PREFILLS and SUGGESTS: the product name,
 * the pack size for the add-ingredient form (v0.6.3), and the label's
 * nutrition facts, which land on the price book item when Andrea
 * confirms it (also v0.6.3, Battles's call to prefer the label over
 * the USDA generic). The lookup never links anything by itself and
 * never supplies a price.
 *
 * There is no Dinner Bot server: the phone talks to the public
 * database directly. Local link learning is the base behavior and
 * needs no connection at all; with no connection, or on any error at
 * all, this returns null and every flow proceeds exactly as it does
 * offline. Nothing about the household is sent: the request carries
 * only the barcode that was just scanned.
 */
object OpenFoodFactsLookup {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    private const val FIELDS =
        "product_name,product_name_en,generic_name,generic_name_en," +
            "quantity,product_quantity,product_quantity_unit," +
            "serving_size,serving_quantity,serving_quantity_unit,nutriments"

    /** The full product for [barcode] (name, pack quantity, label facts), or null when none is available. */
    suspend fun lookupProduct(barcode: String): OffProduct? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://world.openfoodfacts.org/api/v2/product/" +
                    "${barcode.trim()}.json?fields=$FIELDS",
            )
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                // Open Food Facts asks API users to identify the app.
                connection.setRequestProperty(
                    "User-Agent",
                    "DinnerBot/0.6.3 (local-first meal planner)",
                )
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                OffResponseParser.parseProduct(body)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /** A displayable product name for [barcode], or null when none is available. */
    suspend fun suggestProductName(barcode: String): String? =
        lookupProduct(barcode)?.name
}
