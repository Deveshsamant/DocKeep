package com.dockeep.app.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Copies files out of the vault and into the user's own storage.
 *
 * Scans go to the gallery, PDFs to Documents. Shared by every screen that
 * offers to save something, so the naming — which is what the user sees
 * afterwards — and the MediaStore handling are decided in one place.
 */
object MediaExport {

    private const val TAG = "MediaExport"

    /** Where saved scans land, under the device's Pictures directory. */
    private const val ALBUM = "DocKeep"

    /**
     * Saves [source] to the gallery as [displayName].
     *
     * Runs on the IO dispatcher: the caller reaches this from the main thread
     * and a scan can be several megabytes.
     *
     * @return the new item's Uri, or null if it could not be written.
     */
    suspend fun saveImage(context: Context, source: File, displayName: String): Uri? =
        withContext(Dispatchers.IO) {
            if (!source.exists()) {
                Log.w(TAG, "Nothing to save: ${source.path}")
                return@withContext null
            }

            val fileName = sanitize(displayName) + ".jpg"

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveViaMediaStore(context, source, fileName)
                } else {
                    saveViaFile(context, source, fileName)
                }
            } catch (e: Exception) {
                // The previous version swallowed this, which made a failed
                // save impossible to diagnose from a bug report.
                Log.e(TAG, "Could not save $fileName to the gallery", e)
                null
            }
        }

    private fun saveViaMediaStore(context: Context, source: File, fileName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + ALBUM
            )
            // Hide the row until the bytes are actually there, so nothing
            // else picks up a half-written image.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        try {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output) }
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
        } catch (e: Exception) {
            // Leave no pending row behind on a failed write.
            resolver.delete(uri, null, null)
            throw e
        }

        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null
        )
        return uri
    }

    private fun saveViaFile(context: Context, source: File, fileName: String): Uri? {
        val album = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM
        )
        if (!album.exists() && !album.mkdirs()) return null

        // Saving the same scan twice used to overwrite the first copy without
        // saying so; MediaStore numbers duplicates, and so does this.
        val target = uniqueFile(album, fileName)
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }

        // Without this the gallery does not show it until the next media scan.
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        return Uri.fromFile(target)
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "jpg")
        var candidate = File(dir, fileName)
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n).$ext")
            n++
        }
        return candidate
    }

    /**
     * A gallery-safe file name.
     *
     * Scans are stored as "documentId-timestamp-random.jpg", which tells the
     * user nothing once it is sitting in their gallery, so callers pass the
     * document's name and page instead.
     */
    private fun sanitize(name: String): String =
        name.trim()
            .replace(Regex("[^a-zA-Z0-9\\-_ ]"), "_")
            .trim()
            .take(60)
            .ifBlank { "DocKeep" }

    /**
     * Saves [source] into Documents/DocKeep as [displayName].pdf.
     *
     * Same shape as [saveImage]: off the main thread, hidden until written,
     * and it reports a null insert rather than doing nothing. The previous
     * version copied on the UI thread and, when MediaStore refused the insert,
     * silently did nothing at all — the user tapped Save and got no file and
     * no message.
     */
    suspend fun savePdf(context: Context, source: File, displayName: String): Uri? =
        withContext(Dispatchers.IO) {
            if (!source.exists()) {
                Log.w(TAG, "Nothing to save: ${source.path}")
                return@withContext null
            }

            val fileName = sanitize(displayName) + ".pdf"

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                        put(
                            MediaStore.Files.FileColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOCUMENTS + "/" + ALBUM
                        )
                        put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                    }

                    val uri = resolver.insert(
                        MediaStore.Files.getContentUri("external"), values
                    ) ?: return@withContext null

                    try {
                        resolver.openOutputStream(uri)?.use { output ->
                            FileInputStream(source).use { input -> input.copyTo(output) }
                        } ?: run {
                            resolver.delete(uri, null, null)
                            return@withContext null
                        }
                    } catch (e: Exception) {
                        resolver.delete(uri, null, null)
                        throw e
                    }

                    resolver.update(
                        uri,
                        ContentValues().apply {
                            put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                        },
                        null,
                        null
                    )
                    uri
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOCUMENTS
                        ),
                        ALBUM
                    )
                    if (!dir.exists() && !dir.mkdirs()) return@withContext null

                    val target = uniqueFile(dir, fileName)
                    FileInputStream(source).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                    MediaScannerConnection.scanFile(
                        context, arrayOf(target.absolutePath), null, null
                    )
                    Uri.fromFile(target)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not save $fileName to Documents", e)
                null
            }
        }

}
