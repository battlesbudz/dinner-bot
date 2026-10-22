package com.battlesbudz.dinnerbot.feature.grocerylist.barcode

import com.battlesbudz.dinnerbot.core.model.BarcodeLink
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.feature.pricebook.PriceCorrection

/**
 * The pure decisions of the barcode scan flow (Phase V2-5), kept free of
 * Android and ML Kit so every rule is unit-testable on the JVM:
 *
 * - A scanned code resolves through the links learned on this phone:
 *   a known code names its price book item; an unknown code starts the
 *   link-now flow.
 * - Link-now candidates are offered with the current list's items first
 *   (the item being scanned is almost certainly on the list), then the
 *   rest of the price book by name.
 * - A scan correction is the price book's existing correction write with
 *   a scan source note. Scanning never invents a price: the cents always
 *   come from the shelf in front of Andrea, typed by her, exactly like a
 *   tap-to-correct.
 */
object BarcodeScanLogic {

    /** The price book source note recorded on corrections that came from a scan. */
    const val SCAN_SOURCE: String = "Corrected by barcode scan"

    sealed interface Resolution {
        /** The code is linked; [item] is the price book item it names. */
        data class Linked(val item: PriceItem) : Resolution

        /** The code has no link yet (or its item is gone); offer link-now or skip. */
        data class Unlinked(val barcode: String) : Resolution
    }

    fun resolve(
        barcode: String,
        links: List<BarcodeLink>,
        itemsById: Map<String, PriceItem>,
    ): Resolution {
        val link = links.firstOrNull { it.barcode == barcode } ?: return Resolution.Unlinked(barcode)
        val item = itemsById[link.itemId] ?: return Resolution.Unlinked(barcode)
        return Resolution.Linked(item)
    }

    /**
     * Items offered in the link-now picker: [preferredItemId] first when
     * given (a per-line scan starts from that line's item), then the rest
     * of the shopping list's items in list order, then the whole price
     * book by name. Each item appears once.
     */
    fun linkCandidates(
        allItems: List<PriceItem>,
        listItemIds: List<String>,
        preferredItemId: String? = null,
    ): List<PriceItem> {
        val byId = allItems.associateBy { it.id }
        val orderedIds = LinkedHashSet<String>()
        if (preferredItemId != null && byId.containsKey(preferredItemId)) {
            orderedIds.add(preferredItemId)
        }
        listItemIds.forEach { if (byId.containsKey(it)) orderedIds.add(it) }
        allItems.sortedBy { it.name.lowercase() }.forEach { orderedIds.add(it.id) }
        return orderedIds.mapNotNull { byId[it] }
    }

    /**
     * The item as it lands in the price book after Andrea confirms the
     * shelf price she is looking at: the exact record a typed correction
     * writes, with the source noting it came from a scan.
     */
    fun scanCorrection(item: PriceItem, shelfPriceCents: Int): PriceItem =
        PriceCorrection.applyPackPrice(item, shelfPriceCents, source = SCAN_SOURCE)

    /**
     * Whether a scan launched from a shopping line corrects that line
     * (rather than the item's book pack price directly): only when the
     * scanned code names the very item the line buys. Then the row's
     * store governs the write, exactly as a tap on that line's price
     * does — a rerouted line (bought at a non-home store) corrects that
     * store's alternate price; a code naming any other item corrects
     * that item's own book price and leaves the line alone.
     */
    fun scanTargetsRow(scannedItemId: String, rowItemId: String?): Boolean =
        rowItemId != null && rowItemId == scannedItemId

    /**
     * Pull a product name out of an Open Food Facts product response, or
     * null when the response holds none. The lookup is only ever a naming
     * hint for the link-now picker; a name found here is shown to Andrea
     * as a suggestion and never links or prices anything by itself.
     *
     * OFF answers {"status":1,"product":{"product_name":"..."}} for a
     * known code and {"status":0,...} for an unknown one. This reads the
     * document as text (no JSON library on this module's classpath) and
     * extracts the first usable name field, product_name preferred, then
     * the English and generic variants.
     */
    fun parseOffProductName(responseBody: String): String? {
        if (extractJsonLong(responseBody, "status") == 0L) return null
        for (key in listOf("product_name", "product_name_en", "generic_name", "generic_name_en")) {
            val value = extractJsonString(responseBody, key)
            if (!value.isNullOrBlank()) return value.trim()
        }
        return null
    }

    /** Reads the string value of the first "key" occurrence, unescaping JSON string escapes. */
    internal fun extractJsonString(text: String, key: String): String? {
        val needle = "\"$key\""
        var from = 0
        while (true) {
            val keyAt = text.indexOf(needle, from)
            if (keyAt < 0) return null
            var i = keyAt + needle.length
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length || text[i] != ':') {
                from = keyAt + needle.length
                continue
            }
            i++
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length || text[i] != '"') return null
            i++
            val out = StringBuilder()
            while (i < text.length) {
                val ch = text[i]
                when {
                    ch == '\\' && i + 1 < text.length -> {
                        when (val esc = text[i + 1]) {
                            'n' -> out.append('\n')
                            't' -> out.append('\t')
                            'r' -> out.append('\r')
                            'u' -> {
                                val hex = text.substring(i + 2, minOf(i + 6, text.length))
                                hex.toIntOrNull(16)?.let { out.append(it.toChar()) }
                                i += 4
                            }
                            else -> out.append(esc)
                        }
                        i += 2
                    }
                    ch == '"' -> return out.toString()
                    else -> {
                        out.append(ch)
                        i++
                    }
                }
            }
            return null
        }
    }

    /** Reads the numeric value of the first "key" occurrence, or null. */
    internal fun extractJsonLong(text: String, key: String): Long? {
        val needle = "\"$key\""
        val keyAt = text.indexOf(needle)
        if (keyAt < 0) return null
        var i = keyAt + needle.length
        while (i < text.length && (text[i].isWhitespace() || text[i] == ':')) i++
        val start = i
        while (i < text.length && (text[i].isDigit() || text[i] == '-')) i++
        if (i == start) return null
        return text.substring(start, i).toLongOrNull()
    }
}
