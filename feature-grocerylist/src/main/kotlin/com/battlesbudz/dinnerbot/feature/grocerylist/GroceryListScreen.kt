package com.battlesbudz.dinnerbot.feature.grocerylist

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.battlesbudz.dinnerbot.core.model.BarcodeLink
import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.ScanSubstitution
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.BarcodeScanLogic
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.BarcodeScannerDialog
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.LinkBarcodeDialog
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.NewItemFormDialog
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.ScanPriceDialog
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.SubstituteChoiceDialog
import com.battlesbudz.dinnerbot.feature.pricebook.PriceCorrection
import java.time.LocalDate

/** One persisted shopping row, as displayed. [rowId] is the Room row id. */
data class GroceryRow(
    val rowId: Long,
    val line: GroceryLine,
    /** The plan this row belongs to; empty when the caller does not track it. */
    val planId: String = "",
)

private fun storeLabel(store: Store): String = when (store) {
    Store.ALDI -> "Aldi"
    Store.WALMART -> "Walmart"
    Store.SAMS_CLUB -> "Sam's Club"
}

/**
 * Shopping mode: the list split into per-store sections with an estimated
 * cash subtotal per store, check-off boxes, and tap-a-price correction.
 * Correcting a price writes back to the price book (handled by the caller).
 *
 * For a two-week plan (Phase V2-3) the caller passes the big-trip rows in
 * [rows] and week 2's fresh rows in [topUpRows]: the screen renders the
 * big trip first, then the top-up as its own section, each with per-store
 * subtotals. With no top-up rows the rendering is the original one-week
 * list, unchanged.
 */
@Composable
fun GroceryListScreen(
    rows: List<GroceryRow>,
    onToggleChecked: (GroceryRow, Boolean) -> Unit,
    onPriceCorrected: (GroceryRow, Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Price book, for the Sam's bulk lines' leftover + freeze note (Phase V2-2). */
    priceItems: List<PriceItem> = emptyList(),
    /** Week 2's fresh top-up rows of a two-week plan (Phase V2-3); empty for a one-week plan. */
    topUpRows: List<GroceryRow> = emptyList(),
    /** True when the plan covers two weeks, even if nothing landed on the top-up. */
    twoWeekTrip: Boolean = false,
    /** Learned barcode links (Phase V2-5); a scanned code resolves through these. */
    barcodeLinks: List<BarcodeLink> = emptyList(),
    /** A link Andrea just taught the app; the caller persists it as her data. */
    onBarcodeLinkLearned: (BarcodeLink) -> Unit = {},
    /**
     * A price correction that arrived by scanning (Phase V2-5): the item
     * already carries her shelf price through the same correction write as
     * a typed fix; the caller saves it to the price book.
     */
    onScanPriceCorrected: (PriceItem) -> Unit = { _ -> },
    /**
     * A scan launched from a line, where the scanned code names that
     * line's own item (Phase V2-5): the caller runs the line's own
     * correction with the entered total, so a line bought at a non-home
     * store corrects that store's price, never the home store's.
     */
    onScanLineCorrected: (GroceryRow, Int) -> Unit = { _, _ -> },
    /** Remembered scan substitutions (PR v0.4.4); the substitute flow surfaces the matching one. */
    scanSubstitutions: List<ScanSubstitution> = emptyList(),
    /**
     * Recipe context per listed item (PR v0.4.4): a (plan id, item id)
     * pair mapped to the one planned dinner of THAT plan that uses the
     * item, when there is exactly one, so the remembered-swap
     * suggestion prefers the swap made for this dinner. Keyed per plan
     * because a fortnight can use the same item in different dinners
     * in each week.
     */
    substitutionRecipeContext: Map<Pair<String, String>, String?> = emptyMap(),
    /**
     * A price book item created from a scan (PR v0.4.4): the caller
     * saves it to the book and links the scanned barcode to it.
     */
    onScanNewItem: (PriceItem, String) -> Unit = { _, _ -> },
    /**
     * A this-week-only substitution (PR v0.4.4): the row's planned line
     * comes off this week's list, the scanned item goes on at the typed
     * price, the item joins the book first when it is new, and the swap
     * is remembered. The caller writes all of it.
     */
    onSubstitute: (GroceryRow, PriceItem, String?, Int) -> Unit = { _, _, _, _ -> },
) {
    var editing by remember { mutableStateOf<GroceryRow?>(null) }

    // ---- Barcode scanning (Phase V2-5) ----
    // The CAMERA permission is asked for only at the moment a scan is
    // tapped, never before; if it is declined, scanning quietly steps
    // aside and tap-to-correct remains, exactly as before this phase.
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var scanning by remember { mutableStateOf(false) }
    var scannedCode by remember { mutableStateOf<String?>(null) }
    var scanRow by remember { mutableStateOf<GroceryRow?>(null) }
    var scanPreferredItemId by remember { mutableStateOf<String?>(null) }
    var pickedItem by remember { mutableStateOf<PriceItem?>(null) }
    var relinking by remember { mutableStateOf(false) }
    var cameraDeniedNote by remember { mutableStateOf(false) }
    // PR v0.4.4: the add-a-new-item path. [showNewItemForm] opens the
    // form from the link picker; [formPrefillName] carries the lookup's
    // hint into it; [pendingNewItem] holds an item the form just built
    // during a line scan until the substitute choice is made (a general
    // scan saves it immediately instead).
    var showNewItemForm by remember { mutableStateOf(false) }
    var formPrefillName by remember { mutableStateOf<String?>(null) }
    var pendingNewItem by remember { mutableStateOf<PriceItem?>(null) }
    val todayIso = remember { LocalDate.now().toString() }
    val itemCategories = remember(priceItems) {
        (priceItems.map { it.category }.distinct().sorted() + "Other").distinct()
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        if (granted) {
            cameraDeniedNote = false
            scanning = true
        } else {
            cameraDeniedNote = true
        }
    }

    fun startScan(preferredItemId: String?, row: GroceryRow? = null) {
        scanPreferredItemId = preferredItemId
        scanRow = row
        pickedItem = null
        relinking = false
        showNewItemForm = false
        formPrefillName = null
        pendingNewItem = null
        if (cameraGranted) {
            scanning = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun clearScan() {
        scannedCode = null
        pickedItem = null
        relinking = false
        scanPreferredItemId = null
        scanRow = null
        showNewItemForm = false
        formPrefillName = null
        pendingNewItem = null
    }

    val priceItemsById = remember(priceItems) { priceItems.associateBy { it.id } }
    val scanResolution = remember(scannedCode, barcodeLinks, priceItemsById) {
        scannedCode?.let { BarcodeScanLogic.resolve(it, barcodeLinks, priceItemsById) }
    }

    if (scanning) {
        BarcodeScannerDialog(
            onBarcode = { code ->
                scanning = false
                scannedCode = code
            },
            onDismiss = { scanning = false },
        )
    }
    val activeCode = scannedCode
    if (activeCode != null) {
        val resolved = scanResolution
        val displayItem = pendingNewItem ?: pickedItem
            ?: (resolved as? BarcodeScanLogic.Resolution.Linked)?.item
        when {
            showNewItemForm -> NewItemFormDialog(
                barcode = activeCode,
                suggestedName = formPrefillName,
                initialStore = scanRow?.line?.store ?: Store.ALDI,
                categories = itemCategories,
                today = todayIso,
                existingIds = priceItemsById.keys,
                onConfirm = { item ->
                    showNewItemForm = false
                    if (scanRow != null) {
                        // A line scan still owes the substitute choice;
                        // the item is saved by whichever choice she makes.
                        pendingNewItem = item
                    } else {
                        onScanNewItem(item, activeCode)
                        clearScan()
                    }
                },
                onDismiss = { showNewItemForm = false },
            )
            relinking || displayItem == null -> LinkBarcodeDialog(
                barcode = activeCode,
                candidates = BarcodeScanLogic.linkCandidates(
                    allItems = priceItems,
                    listItemIds = (rows + topUpRows).map { it.line.itemId },
                    preferredItemId = scanPreferredItemId,
                ),
                onPick = { item ->
                    onBarcodeLinkLearned(
                        BarcodeLink(activeCode, item.id, System.currentTimeMillis()),
                    )
                    relinking = false
                    pickedItem = item
                },
                onAddNew = { hint ->
                    formPrefillName = hint
                    relinking = false
                    showNewItemForm = true
                },
                onSkip = { clearScan() },
            )
            else -> {
                // A scan launched from a line, naming that line's own
                // item, corrects the line itself: the row's store
                // governs the write, like a tap on the line's price. A
                // line scan naming a DIFFERENT item offers the
                // this-week-only substitution (PR v0.4.4). Any other
                // scan corrects the item's book pack price.
                val lineRow = scanRow?.takeIf {
                    BarcodeScanLogic.scanTargetsRow(displayItem.id, it.line.itemId)
                }
                val fromRow = scanRow
                if (lineRow != null) {
                    val lineCash = GroceryListBuilder.lineCashCents(lineRow.line)
                    ScanPriceDialog(
                        item = displayItem,
                        headerText = "Scanned from your ${storeLabel(lineRow.line.store)} line, " +
                            "priced at ${Money(lineCash).format()} right now. Type what this " +
                            "line actually cost; your number is saved to the price book, the " +
                            "same as tapping the price.",
                        initialCents = lineCash,
                        onConfirm = { cents ->
                            onScanLineCorrected(lineRow, cents)
                            clearScan()
                        },
                        onDismiss = { clearScan() },
                        onRelink = if (pickedItem == null) ({ relinking = true }) else null,
                    )
                } else if (fromRow != null) {
                    // A scan from an already-swapped line looks its
                    // memory up by the ROOT planned item: that is the
                    // pairing the records are written against.
                    val rootItemId = BarcodeScanLogic.primaryOriginOf(fromRow.line)
                        ?: fromRow.line.itemId
                    val remembered = BarcodeScanLogic.rememberedSubstitution(
                        scanSubstitutions,
                        rootItemId,
                        substitutionRecipeContext[fromRow.planId to rootItemId],
                    )
                    SubstituteChoiceDialog(
                        scannedItem = displayItem,
                        lineDisplayName = fromRow.line.displayName,
                        initialCents = displayItem.packPriceCents,
                        remembered = remembered,
                        rememberedSubstituteName = remembered?.let {
                            priceItemsById[it.substituteItemId]?.name
                        },
                        onSubstitute = { cents ->
                            onSubstitute(fromRow, displayItem, activeCode, cents)
                            clearScan()
                        },
                        onSavePriceOnly = { cents ->
                            if (pendingNewItem != null) {
                                onScanNewItem(
                                    BarcodeScanLogic.scanCorrection(displayItem, cents),
                                    activeCode,
                                )
                            } else {
                                onScanPriceCorrected(
                                    BarcodeScanLogic.scanCorrection(displayItem, cents),
                                )
                            }
                            clearScan()
                        },
                        onDismiss = { clearScan() },
                    )
                } else {
                    ScanPriceDialog(
                        item = displayItem,
                        headerText = "The price book's last saved price is " +
                            "${Money(displayItem.packPriceCents).format()} " +
                            "for ${displayItem.packDescription}. Type the shelf price for the " +
                            "pack; your number is saved to the price book.",
                        initialCents = displayItem.packPriceCents,
                        onConfirm = { cents ->
                            onScanPriceCorrected(BarcodeScanLogic.scanCorrection(displayItem, cents))
                            clearScan()
                        },
                        onDismiss = { clearScan() },
                        onRelink = if (pickedItem == null) ({ relinking = true }) else null,
                    )
                }
            }
        }
    }

    if (rows.isEmpty() && topUpRows.isEmpty()) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text("Shopping list", style = MaterialTheme.typography.headlineMedium)
            Text(
                "No list yet. Plan a week first and the list builds itself from the dinners.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ScanBarcodeButton(modifier = Modifier.padding(top = 16.dp)) { startScan(null) }
            if (cameraDeniedNote) {
                CameraDeniedNote(modifier = Modifier.padding(top = 8.dp))
            }
        }
        return
    }
    val total = (rows + topUpRows).sumOf { GroceryListBuilder.lineCashCents(it.line) }
    val bigTripTotal = rows.sumOf { GroceryListBuilder.lineCashCents(it.line) }
    val topUpTotal = topUpRows.sumOf { GroceryListBuilder.lineCashCents(it.line) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Shopping list", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (twoWeekTrip) {
                    "Two weeks, one big trip of about ${Money(bigTripTotal).format()} plus a fresh " +
                        "top-up of about ${Money(topUpTotal).format()} for week 2. Estimated total: " +
                        "${Money(total).format()}. Tap a price to fix it; your number is saved to " +
                        "the price book."
                } else {
                    "Estimated cash total: ${Money(total).format()}. Tap a price to fix it; " +
                        "your number is saved to the price book."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            ScanBarcodeButton(modifier = Modifier.padding(top = 12.dp)) { startScan(null) }
            if (cameraDeniedNote) {
                CameraDeniedNote(modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (twoWeekTrip) {
            item {
                Text(
                    "Big trip — buy first",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "Everything for week 1, plus week 2's shelf-stable, frozen, and bulk food.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        storeSections(rows, priceItemsById, onToggleChecked, onScanRow = { row ->
            startScan(row.line.itemId, row)
        }) { editing = it }
        if (topUpRows.isNotEmpty()) {
            item {
                Text(
                    "Fresh top-up — buy at the start of week 2",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "Week 2's fresh food, bought later so nothing spoils.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            storeSections(topUpRows, priceItemsById, onToggleChecked, onScanRow = { row ->
                startScan(row.line.itemId, row)
            }) { editing = it }
        }
    }

    editing?.let { row ->
        PriceEditDialog(
            title = row.line.displayName,
            currentTotalCents = GroceryListBuilder.lineCashCents(row.line),
            onDismiss = { editing = null },
            onSave = { cents ->
                onPriceCorrected(row, cents)
                editing = null
            },
        )
    }
}

/** One section's rows, grouped by store with a cash subtotal per store. */
private fun LazyListScope.storeSections(
    rows: List<GroceryRow>,
    priceItemsById: Map<String, PriceItem>,
    onToggleChecked: (GroceryRow, Boolean) -> Unit,
    onScanRow: (GroceryRow) -> Unit,
    onEditPrice: (GroceryRow) -> Unit,
) {
    val byStore = rows.groupBy { it.line.store }
    Store.entries.forEach { store ->
        val storeRows = byStore[store] ?: return@forEach
        item {
            val subtotal = storeRows.sumOf { GroceryListBuilder.lineCashCents(it.line) }
            Text(
                "${storeLabel(store)} — about ${Money(subtotal).format()}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        items(storeRows, key = { it.rowId }) { row ->
            GroceryRowView(row, priceItemsById, onToggleChecked, onScanRow, onEditPrice)
        }
    }
}

@Composable
private fun ScanBarcodeButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.testTag("scan-barcode-button"),
    ) { Text("Scan a barcode") }
}

@Composable
private fun CameraDeniedNote(modifier: Modifier = Modifier) {
    Text(
        "The camera is off, so scanning is unavailable. You can still tap any " +
            "price to fix it, exactly as before.",
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.testTag("camera-denied-note"),
    )
}

@Composable
private fun GroceryRowView(
    row: GroceryRow,
    priceItemsById: Map<String, PriceItem>,
    onToggleChecked: (GroceryRow, Boolean) -> Unit,
    onScanRow: (GroceryRow) -> Unit,
    onEditPrice: (GroceryRow) -> Unit,
) {
    val line = row.line
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = line.checkedOff,
            onCheckedChange = { checked -> onToggleChecked(row, checked) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                line.displayName,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (line.checkedOff) TextDecoration.LineThrough else null,
            )
            Text(
                "Need ${trimNumber(line.needed.amount)} ${line.needed.unit.label}" +
                    if (line.packsToBuy > 1) " · buy ${line.packsToBuy} packs" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            // A scan swap (PR v0.4.4) says what it replaced, so the
            // list never quietly differs from the plan.
            val swappedFor = BarcodeScanLogic.displacedOriginsOf(line.substitutedForItemId)
            if (swappedFor.isNotEmpty()) {
                Text(
                    "Swapped in for " +
                        swappedFor.joinToString(" and ") {
                            priceItemsById[it]?.name ?: "a planned item"
                        } +
                        " — this week only",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Freeze guidance (Phase V2-2): when a Sam's bulk pack
            // is being bought, say how much will be left over and
            // how to freeze it. Guidance only, no tracking.
            val pricedItem = priceItemsById[line.itemId]
            val leftover = pricedItem?.let { Costing.buyLeftover(line, it) }
            val freezeNote = pricedItem?.freezeNote
            if (leftover != null && !freezeNote.isNullOrBlank()) {
                Text(
                    "You will have about ${trimNumber(leftover.amount)} " +
                        "${leftover.unit.label} left over. $freezeNote",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money(GroceryListBuilder.lineCashCents(line)).format(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable { onEditPrice(row) }.padding(8.dp),
            )
            TextButton(
                onClick = { onScanRow(row) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) { Text("Scan", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
internal fun PriceEditDialog(
    title: String,
    currentTotalCents: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var text by remember { mutableStateOf((currentTotalCents / 100.0).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What did this line actually cost? Your price is saved to the price book.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Price") },
                    prefix = { Text("$") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cents = PriceCorrection.parseToCents(text)
                    if (cents != null && cents > 0) onSave(cents)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.2f", value).trimEnd('0').trimEnd('.')
