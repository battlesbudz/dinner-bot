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
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.BarcodeScanLogic
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.BarcodeScannerDialog
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.LinkBarcodeDialog
import com.battlesbudz.dinnerbot.feature.grocerylist.barcode.ScanPriceDialog
import com.battlesbudz.dinnerbot.feature.pricebook.PriceCorrection

/** One persisted shopping row, as displayed. [rowId] is the Room row id. */
data class GroceryRow(
    val rowId: Long,
    val line: GroceryLine,
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
    var scanPreferredItemId by remember { mutableStateOf<String?>(null) }
    var pickedItem by remember { mutableStateOf<PriceItem?>(null) }
    var relinking by remember { mutableStateOf(false) }
    var cameraDeniedNote by remember { mutableStateOf(false) }
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

    fun startScan(preferredItemId: String?) {
        scanPreferredItemId = preferredItemId
        pickedItem = null
        relinking = false
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
        val picked = pickedItem
        val resolved = scanResolution
        when {
            picked != null -> ScanPriceDialog(
                item = picked,
                onConfirm = { cents ->
                    onScanPriceCorrected(BarcodeScanLogic.scanCorrection(picked, cents))
                    clearScan()
                },
                onDismiss = { clearScan() },
            )
            relinking || resolved is BarcodeScanLogic.Resolution.Unlinked -> LinkBarcodeDialog(
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
                onSkip = { clearScan() },
            )
            resolved is BarcodeScanLogic.Resolution.Linked -> ScanPriceDialog(
                item = resolved.item,
                onConfirm = { cents ->
                    onScanPriceCorrected(BarcodeScanLogic.scanCorrection(resolved.item, cents))
                    clearScan()
                },
                onDismiss = { clearScan() },
                onRelink = { relinking = true },
            )
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
            startScan(row.line.itemId)
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
                startScan(row.line.itemId)
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
