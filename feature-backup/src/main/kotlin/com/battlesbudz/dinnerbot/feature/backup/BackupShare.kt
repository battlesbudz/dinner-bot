package com.battlesbudz.dinnerbot.feature.backup

import java.io.File

/**
 * Sharing polish (Epic issue #9, decision D-V2-1): household sharing IS
 * the backup file. Export saves it; share hands the very same file to
 * whatever the household already uses (a message, a drive folder, a
 * cable) through the Android share sheet; the other phone imports it.
 * There is no Dinner Bot server, account, or live shared plan: a backup
 * is a copy from the moment it was made, and the wording here says so
 * where the household will read it.
 *
 * This object holds the share payload's moving parts as plain data so
 * the construction is unit-testable without Android UI: the app module
 * turns these values into the actual share intent.
 */
object BackupShare {

    /** The backup file's name, identical for export and share. */
    const val FILE_NAME: String = "dinner-bot-backup.json"

    /** MIME type the file is shared as. */
    const val MIME_TYPE: String = "application/json"

    /** Title on the system share sheet's chooser. */
    const val CHOOSER_TITLE: String = "Share Dinner Bot backup"

    /** Subject line for share targets that take one (email, drive). */
    const val SHARE_SUBJECT: String = "Dinner Bot backup"

    /** Message that travels with the shared file, for the person receiving it. */
    const val SHARE_TEXT: String =
        "This is a Dinner Bot backup file. Open Dinner Bot on your phone, tap " +
            "Import backup in Settings, and pick this file. Importing replaces " +
            "that phone's settings, prices, pantry, stock, ratings, and barcode " +
            "links with this backup's contents, and clears its current plan " +
            "and shopping list — plans are not in the file."

    /**
     * The plain wording shown before an import runs (Epic issue #9's
     * acceptance: import copy is plain and accurate). Importing replaces
     * the current data on this phone wholesale; plans and shopping lists
     * are not part of a backup and are cleared by an import, not carried
     * over; and a backup is a copy at a moment in time, not a live
     * shared plan.
     */
    const val IMPORT_REPLACES_MESSAGE: String =
        "Importing replaces everything Dinner Bot holds on this phone — " +
            "settings, prices (including corrections made here), pantry, bulk " +
            "stock, recipe ratings, barcode links, and Sunday ran-out " +
            "records — with the backup's contents.\n\n" +
            "Plans and shopping lists are not in a backup. Importing clears " +
            "this phone's current plan and list; you plan a fresh week from " +
            "the imported prices.\n\n" +
            "A backup is a copy from the moment it was made, not a live " +
            "shared plan. If both phones keep planning afterwards, the two " +
            "copies drift apart, and the last import wins."

    /** Sub-directory of the app cache the share copy is written to. */
    const val SHARE_DIRECTORY: String = "shared_backups"

    /** The file a share writes into [cacheDir] before the share sheet opens. */
    fun shareFileIn(cacheDir: File): File = File(File(cacheDir, SHARE_DIRECTORY), FILE_NAME)
}
