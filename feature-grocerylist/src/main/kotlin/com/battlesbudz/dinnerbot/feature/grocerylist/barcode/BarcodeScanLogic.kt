package com.battlesbudz.dinnerbot.feature.grocerylist.barcode

import com.battlesbudz.dinnerbot.core.model.BarcodeLink
import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.ScanSubstitution
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.feature.pricebook.PriceCorrection
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

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
 *
 * The v0.4.4 rework (Battles's field reports on the v0.4.2 build) adds:
 *
 * - A scanned product that is not in the book can be ADDED from the
 *   link picker: [buildUserItem] makes the price book item from the
 *   form she fills in (name prefilled from the Open Food Facts hint,
 *   the scan's store as home store, the shelf price she types). The id
 *   carries the "user-" prefix no seed id uses, so seed refreshes,
 *   which only ever touch seed ids, can never overwrite or drop it.
 * - A scan from a shopping line that names a DIFFERENT item can
 *   substitute: [substituteLine] builds the line that goes on THIS
 *   week's list at the typed price, marked with the planned item it
 *   replaced, and [foldSubstitutedLines] keeps the swap standing when
 *   the week is repriced, with the cash and food deltas computed
 *   honestly. The original item's book price is never touched by a
 *   substitution; the recipe and future weeks are never touched at all.
 * - Substitutions are remembered ([rememberedSubstitution]): the next
 *   time the same original item is scanned from a line, the most recent
 *   swap for it (the one made for the same dinner when the context
 *   matches) is surfaced as a suggestion. Nothing applies itself.
 */
object BarcodeScanLogic {

    /** The price book source note recorded on corrections that came from a scan. */
    const val SCAN_SOURCE: String = "Corrected by barcode scan"

    /** The price book source note on items created from a scan (PR v0.4.4). */
    const val NEW_ITEM_SOURCE: String = "Added by barcode scan"

    /** Prefix of every user-added item id; no seed id uses it. */
    const val USER_ITEM_ID_PREFIX: String = "user-"

    /** The pack-rounding epsilon, Costing's own value, for pack counts. */
    private const val PACK_EPSILON: Double = 1e-9

    /**
     * The planned items a swap marker names, in order. The marker is a
     * comma-joined list because one row can displace more than one
     * planned item: a line born from a write-time merge also stood for
     * the substitute's own planned use, and re-swapping that row must
     * keep the fresh costing from resurrecting either origin (Codex,
     * PR #24). Item ids are slugs and never contain a comma.
     */
    fun displacedOriginsOf(marker: String?): List<String> =
        marker?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /** The first displaced origin of a line, its primary pairing. */
    fun primaryOriginOf(line: GroceryLine): String? =
        displacedOriginsOf(line.substitutedForItemId).firstOrNull()

    /** The marker a swap of [original] carries: its origins, then itself. */
    private fun markerFor(original: GroceryLine): String =
        (displacedOriginsOf(original.substitutedForItemId) + original.itemId)
            .distinct().joinToString(",")

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

    /** A display name reduced to the slug half of a user-added item id. */
    fun slugFor(name: String): String {
        val slug = name.lowercase()
            .map { ch -> if (ch.isLetterOrDigit()) ch else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
        return slug.ifEmpty { "item" }
    }

    /**
     * The id for a user-added item: "user-" plus a slug of its name,
     * numbered (-2, -3, ...) when the plain id is already taken. Seed
     * ids never carry the prefix, so a user id can never collide with a
     * seed row the refresh owns.
     */
    fun newItemId(name: String, existingIds: Set<String>): String {
        val base = USER_ITEM_ID_PREFIX + slugFor(name)
        if (base !in existingIds) return base
        var n = 2
        while ("$base-$n" in existingIds) n++
        return "$base-$n"
    }

    /**
     * The price book item a scan creates for a product the book did not
     * have (Battles's sugar case: the lookup named it, the book did not
     * hold it, and the old flow dead-ended). Every field comes from the
     * form Andrea filled in; the typed price is the pack price, flagged
     * user-corrected like any price she enters, with [NEW_ITEM_SOURCE]
     * noting the door it came through. From here the item is an
     * ordinary book item: the planner costs it, backups carry it, and
     * the seed refresh leaves it alone.
     */
    fun buildUserItem(
        name: String,
        category: String,
        homeStore: Store,
        packDescription: String,
        packQuantity: Quantity,
        shelfPriceCents: Int,
        checkedDate: String,
        existingIds: Set<String>,
    ): PriceItem {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "a new item needs a name" }
        require(shelfPriceCents > 0) { "price must be positive" }
        require(packQuantity.amount > 0.0) { "pack quantity must be positive" }
        return PriceItem(
            id = newItemId(trimmedName, existingIds),
            name = trimmedName,
            category = category.trim().ifEmpty { "Other" },
            homeStore = homeStore,
            packDescription = packDescription.trim(),
            packQuantity = packQuantity,
            packPriceCents = shelfPriceCents,
            pricePerUnitCents = shelfPriceCents.toDouble() / packQuantity.amount,
            checkedDate = checkedDate,
            source = NEW_ITEM_SOURCE,
            userCorrected = true,
        )
    }

    /**
     * The line a substitution puts on THIS week's list: one pack of the
     * substitute at the typed shelf price, at the store the planned line
     * was shopping, marked with the planned item it replaced so
     * repricing keeps the swap (see [foldSubstitutedLines]) and the
     * record can be written. The planned recipe is not consulted and
     * not changed; the original item keeps its own book price.
     *
     * [shelfPriceCents] is the price of ONE pack, the pack in hand; it
     * is the line's per-pack cash figure. The line buys as many packs
     * as the dinner quantity takes, by the planner's own pack math
     * (Costing): a 1 lb pack swapped in for a 2 lb need buys two
     * packs, never one pack and a short recipe. The line's
     * [GroceryLine.needed] is the DINNER quantity, exactly as on a
     * planner line: the planned line's need expressed in the
     * substitute's pack unit. That is what makes the list read "Need
     * 2 lb" for a 2 lb need met by a 4 lb pack, and what lets
     * Costing.buyLeftover see the 2 lb that will remain. The
     * attributed food cost follows the planner's own definition (see
     * Costing): that same need at the typed price's per-unit rate,
     * never the whole pack when the pack is bigger.
     *
     * The planned line's check-off state carries over: a line Andrea
     * already checked off was already dealt with, and its replacement
     * must not reappear as still to buy. The marker names every origin
     * the fold must suppress: the replaced line's own origins plus the
     * line itself. A swap made from an already-swapped line therefore
     * keeps the original displaced item (never only the intermediate
     * substitute, which the planner would not regenerate on its own),
     * and a swap made from a write-time MERGED line keeps both the
     * first displaced item and the substitute's own planned use.
     */
    fun substituteLine(original: GroceryLine, substitute: PriceItem, shelfPriceCents: Int): GroceryLine {
        require(shelfPriceCents > 0) { "price must be positive" }
        require(substitute.id != original.itemId) { "a substitution names a different item" }
        if (original.needed.unit.dimension != substitute.packQuantity.unit.dimension) {
            // Cross-dimension swap (Codex, PR #24): the recipe measures
            // in cups, the product sells by weight. There is no honest
            // conversion without a product-specific equivalence, and
            // Costing.amountInPackUnit's cross-dimension fallback only
            // compares base scalars, so nothing is derived from it.
            // The swap is defined the way a shopper treats it: ONE
            // pack, at the typed price, the whole pack the dinner's
            // cost this week.
            return GroceryLine(
                itemId = substitute.id,
                displayName = substitute.name,
                store = original.store,
                needed = substitute.packQuantity,
                packsToBuy = 1,
                estimatedPackPriceCents = shelfPriceCents,
                portionCostCents = shelfPriceCents,
                checkedOff = original.checkedOff,
                reconciliationAdded = original.reconciliationAdded,
                substitutedForItemId = markerFor(original),
            )
        }
        val perUnitCents = shelfPriceCents.toDouble() / substitute.packQuantity.amount
        val needInPackUnit = Costing.amountInPackUnit(original.needed, substitute)
        val packs = ceil(needInPackUnit / substitute.packQuantity.amount - PACK_EPSILON)
            .toInt().coerceAtLeast(1)
        return GroceryLine(
            itemId = substitute.id,
            displayName = substitute.name,
            store = original.store,
            needed = Quantity(needInPackUnit, substitute.packQuantity.unit),
            packsToBuy = packs,
            estimatedPackPriceCents = shelfPriceCents,
            portionCostCents = (needInPackUnit * perUnitCents).roundToInt(),
            checkedOff = original.checkedOff,
            // A Week 2 top-up line stays a top-up line: dropping the
            // flag would move the purchase into the big trip's
            // section and change when it is scheduled (Codex, PR #24).
            reconciliationAdded = original.reconciliationAdded,
            substitutedForItemId = markerFor(original),
        )
    }

    /**
     * Merge a swap into a line that already buys the substitute for
     * another dinner of the same week (the write-time twin of the
     * carry [foldSubstitutedLines] performs at reprice): inserting a
     * second line would budget a second purchase the week may not
     * need. The combined line carries both dinners' need, both
     * dinners' food portions, and the swap's marker. Its price follows
     * the line's own representation: at the item's home store, packs
     * recomputed by the planner's pack math (never fewer than the line
     * already bought) at the typed per-pack price; at an alternate
     * store, the combined need priced at the scanned rate, in
     * Costing's one-line need-cost form. It reads as checked off only
     * when both source lines were: any unchecked part is still to buy.
     */
    fun mergeSwapIntoLine(existing: GroceryLine, swap: GroceryLine, item: PriceItem): GroceryLine {
        require(existing.itemId == item.id && swap.itemId == item.id) {
            "a swap merges only into a line for the same item"
        }
        require(existing.substitutedForItemId == null) { "only a plain line absorbs a swap" }
        require(swap.substitutedForItemId != null) { "the absorbed line must be a swap" }
        require(existing.store == swap.store) {
            "a swap merges only into a line shopping at the same store; " +
                "a different store keeps the purchases separate"
        }
        require(existing.reconciliationAdded == swap.reconciliationAdded) {
            "a swap merges only into a line on the same trip; a big-trip " +
                "line and a Week 2 top-up line stay separate purchases"
        }
        val combinedNeedInPackUnit = Costing.amountInPackUnit(existing.needed, item) +
            Costing.amountInPackUnit(swap.needed, item)
        val packsForCombined = ceil(combinedNeedInPackUnit / item.packQuantity.amount - PACK_EPSILON)
            .toInt().coerceAtLeast(1)
        val combinedPortion = (existing.portionCostCents ?: 0) + (swap.portionCostCents ?: 0)
        val combinedChecked = existing.checkedOff && swap.checkedOff
        return if (existing.store == item.homeStore) {
            existing.copy(
                needed = Quantity(combinedNeedInPackUnit, item.packQuantity.unit),
                packsToBuy = max(existing.packsToBuy, packsForCombined),
                estimatedPackPriceCents = swap.estimatedPackPriceCents,
                portionCostCents = combinedPortion,
                checkedOff = combinedChecked,
                substitutedForItemId = swap.substitutedForItemId,
            )
        } else {
            // Alternate-store representation (Costing, for a Sam's item
            // the gate routed elsewhere): packsToBuy 1 and the line's
            // price IS the cost of the quantity needed, not a pack
            // price. The merged price must cover the COMBINED need at
            // the scanned rate, or the week keeps budgeting one dinner's
            // amount for two dinners' food (Codex, PR #24).
            val scannedRatePerPackUnit = swap.estimatedPackPriceCents / item.packQuantity.amount
            existing.copy(
                needed = Quantity(combinedNeedInPackUnit, item.packQuantity.unit),
                packsToBuy = 1,
                estimatedPackPriceCents = (combinedNeedInPackUnit * scannedRatePerPackUnit)
                    .roundToInt(),
                portionCostCents = combinedPortion,
                checkedOff = combinedChecked,
                substitutedForItemId = swap.substitutedForItemId,
            )
        }
    }

    /**
     * The food portion of a swap line after its price is corrected
     * (a tap on its price, or a rescan) to [newPackPriceCents]
     * (Codex, PR #24). A correction updates the row's pack price but
     * a swap line is carried whole at reprice, so without this its
     * portion would keep the pre-correction rate forever. The portion
     * follows the line's own representation: at the item's home store,
     * the line's need at the corrected per-unit rate (a merged line's
     * combined need included); at an alternate store the line's price
     * IS the cost of its quantity, so the portion is that price, the
     * typed total for the whole line.
     */
    fun correctedSwapPortion(line: GroceryLine, item: PriceItem, newPackPriceCents: Int): Int {
        require(line.itemId == item.id) { "the portion recompute is for the line's own item" }
        require(line.substitutedForItemId != null) { "only swap lines carry a frozen portion" }
        return if (line.store == item.homeStore) {
            val needInPackUnit = Costing.amountInPackUnit(line.needed, item)
            (needInPackUnit * newPackPriceCents / item.packQuantity.amount).roundToInt()
        } else {
            newPackPriceCents
        }
    }

    /** A repriced week's lines with its scan substitutions folded back in, and the money deltas. */
    data class SubstitutedFold(
        val lines: List<GroceryLine>,
        /** Add to the repriced cash total: carried swaps in, displaced planned lines out. */
        val cashDeltaCents: Int,
        /** Add to the repriced food cost on the same basis. */
        val foodDeltaCents: Int,
    )

    /**
     * Fold a stored week's scan substitutions into a fresh repricing,
     * the same contract the Sunday reconciliation merge established for
     * its lines: each planned line a swap displaced stays off the fresh
     * list, and each swap line is carried over at its scanned price
     * with its check-off state intact.
     *
     * When the fresh costing buys the substitute on its own (another
     * dinner in the week needs it too), the stored swap line CARRIES
     * AS A WHOLE and the fresh line drops — when they shop at the
     * same store, so they are the same purchase. That is the only
     * honest read of the stored state: the write path already merged
     * the swap into the week's purchase for this item (see
     * [mergeSwapIntoLine]), so the stored line's need, pack count and
     * portion already cover both dinners. Rebuilding it from the
     * fresh line would shrink the purchase back to one dinner's and
     * double-count the other's portion. The stored price stands too:
     * it was typed at scan time, and a merged alternate-store line's
     * price is its combined need cost, which no fresh one-dinner line
     * can price. A fresh line for the item at a DIFFERENT store
     * is a separate purchase and stands; the swap carries alongside
     * it. A second swap for the same substitute has no fresh line
     * left to displace and carries as its own line: two swaps were
     * two packs in the cart.
     *
     * The deltas are computed from the line sums themselves (final
     * lines minus fresh lines), so they price exactly the movements:
     * carried swap lines in, displaced planned lines out, displaced
     * fresh lines for a carried swap's item out.
     */
    fun foldSubstitutedLines(current: List<GroceryLine>, fresh: List<GroceryLine>): SubstitutedFold {
        val swaps = current.filter { it.substitutedForItemId != null }
        if (swaps.isEmpty()) return SubstitutedFold(fresh, 0, 0)
        val displacedIds = swaps.flatMap { displacedOriginsOf(it.substitutedForItemId) }.toSet()
        val kept = fresh.filter { it.itemId !in displacedIds }.toMutableList()
        val carried = mutableListOf<GroceryLine>()
        for (swap in swaps) {
            val freshIndex = kept.indexOfFirst {
                it.itemId == swap.itemId && it.store == swap.store
            }
            if (freshIndex >= 0) {
                // The stored line carries whole, at its stored price:
                // refreshing the price from the one-dinner fresh line
                // would reset a merged line's combined cost (an
                // alternate-store line's price IS its need cost).
                kept.removeAt(freshIndex)
                carried += swap
            } else {
                carried += swap
            }
        }
        val folded = kept + carried
        fun cash(lines: List<GroceryLine>): Int = lines.sumOf { it.packsToBuy * it.estimatedPackPriceCents }
        fun food(lines: List<GroceryLine>): Int = lines.sumOf { it.portionCostCents ?: 0 }
        return SubstitutedFold(
            lines = folded,
            cashDeltaCents = cash(folded) - cash(fresh),
            foodDeltaCents = food(folded) - food(fresh),
        )
    }

    /**
     * The remembered substitution to suggest for [originalItemId]: the
     * most recent swap recorded for it, preferring the one made for the
     * same dinner when [recipeId] names the current context. Null when
     * the pairing was never swapped, so a different item never inherits
     * another item's memory. A suggestion only; nothing applies itself.
     */
    fun rememberedSubstitution(
        records: List<ScanSubstitution>,
        originalItemId: String,
        recipeId: String? = null,
    ): ScanSubstitution? {
        val matches = records.filter { it.originalItemId == originalItemId }
        if (matches.isEmpty()) return null
        if (recipeId != null) {
            matches.filter { it.recipeId == recipeId }
                .maxByOrNull { it.createdAtMillis }
                ?.let { return it }
        }
        return matches.maxByOrNull { it.createdAtMillis }
    }

    /**
     * The recipe context of a swap from a line for [originalItemId]:
     * the one dinner on the week that uses the item, when there is
     * exactly one. Two dinners using it (or none) means the context is
     * not knowable, and the record is written without one.
     */
    fun recipeContextFor(
        originalItemId: String,
        nights: List<PlannedNight>,
        recipesById: Map<String, Recipe>,
    ): String? {
        val recipeIds = nights.asSequence()
            .filterNot { it.notCooking }
            .mapNotNull { recipesById[it.recipeId] }
            .filter { recipe -> recipe.ingredients.any { it.priceItemId == originalItemId } }
            .map { it.id }
            .distinct()
            .toList()
        return recipeIds.singleOrNull()
    }

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
