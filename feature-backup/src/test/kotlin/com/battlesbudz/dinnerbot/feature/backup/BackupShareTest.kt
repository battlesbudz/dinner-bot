package com.battlesbudz.dinnerbot.feature.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Epic issue #9: the share payload is the backup file itself, and the
 * import wording says plainly that importing replaces current data.
 */
class BackupShareTest {

    @Test fun shareUsesTheBackupFileNameAndJsonType() {
        // Share must produce the same file export produces (issue #9
        // acceptance), so the name matches the export name in the app.
        assertEquals("dinner-bot-backup.json", BackupShare.FILE_NAME)
        assertEquals("application/json", BackupShare.MIME_TYPE)
    }

    @Test fun shareFileLivesInTheShareCacheDirectory() {
        val cache = File("/tmp/cache")
        val file = BackupShare.shareFileIn(cache)
        assertEquals(
            File(File(cache, "shared_backups"), "dinner-bot-backup.json").path,
            file.path,
        )
    }

    @Test fun shareTextTellsTheReceiverWhatImportingDoes() {
        assertTrue(BackupShare.SHARE_TEXT.contains("Import backup"))
        assertTrue(BackupShare.SHARE_TEXT.contains("replaces"))
        assertTrue(BackupShare.SHARE_TEXT.contains("plans are not in the file"))
    }

    @Test fun importWordingSaysReplaceAndNotLiveSharing() {
        val message = BackupShare.IMPORT_REPLACES_MESSAGE
        assertTrue(message.contains("replaces everything"))
        assertTrue(message.contains("not a live"))
        assertTrue(message.contains("last import wins"))
    }

    @Test fun importWordingAdmitsPlansAreClearedNotCarried() {
        // A backup holds no plans: applyBackup clears the phone's plan
        // and grocery rows (they were priced from the old book), so the
        // warning must say that instead of implying the sender's plan
        // arrives with the file.
        val message = BackupShare.IMPORT_REPLACES_MESSAGE
        assertTrue(message.contains("Plans and shopping lists are not in a backup"))
        assertTrue(message.contains("clears"))
    }
}
