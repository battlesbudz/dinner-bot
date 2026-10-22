package com.battlesbudz.dinnerbot.feature.grocerylist.barcode

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one optional network assist in Dinner Bot (Phase V2-5, Battles's
 * D-V2-3 decision): when the phone has a connection, a scanned barcode
 * may be looked up in Open Food Facts, the public product database, to
 * SUGGEST the product's name while Andrea links the code to a price
 * book item. The suggestion is shown for her to confirm or ignore; it
 * never links anything by itself and never supplies a price.
 *
 * There is no Dinner Bot server: the phone talks to the public database
 * directly. Local link learning is the base behavior and needs no
 * connection at all; with no connection, or on any error at all, this
 * returns null and the link-now flow proceeds exactly as it does
 * offline. Nothing about the household is sent: the request carries
 * only the barcode that was just scanned.
 */
object OpenFoodFactsLookup {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    /** A displayable product name for [barcode], or null when none is available. */
    suspend fun suggestProductName(barcode: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://world.openfoodfacts.org/api/v2/product/" +
                    "${barcode.trim()}.json?fields=product_name,product_name_en,generic_name,generic_name_en",
            )
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                // Open Food Facts asks API users to identify the app.
                connection.setRequestProperty("User-Agent", "DinnerBot/0.3.0 (local-first meal planner)")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                BarcodeScanLogic.parseOffProductName(body)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
