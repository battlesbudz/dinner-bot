package com.battlesbudz.dinnerbot.feature.barcode

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The barcode surfaces' door to the premium scan allowance (Phase C3).
 *
 * Scanning lives in two places — the shopping list and the shared
 * add-ingredient form — and the free scan allowance (15 starter scans,
 * then 10 a month; see PremiumRules in core-model) is one budget
 * across both, so both consult the same gate object that the app
 * provides through [LocalScanGate]:
 *
 * - Before the camera opens, the surface asks [canStartScan]. When it
 *   answers false, the surface simply does not open the camera; the
 *   gate's owner (the app) is responsible for showing the contextual
 *   Premium prompt. Manual entry is never blocked by the gate: only
 *   the camera waits on it.
 * - When a barcode actually decodes, the surface calls
 *   [onScanCompleted] once, so an opened-and-abandoned camera costs
 *   nothing.
 *
 * A null gate (tests, previews, anywhere the app did not provide one)
 * means scanning is ungated, exactly as before this phase.
 */
class ScanGate(
    val canStartScan: () -> Boolean,
    val onScanCompleted: () -> Unit,
)

/** The app's scan gate, provided once above every scanning surface. */
val LocalScanGate = staticCompositionLocalOf<ScanGate?> { null }
