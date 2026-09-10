package com.dockeep.app.utils

import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Reads the text off a scan.
 *
 * Runs entirely on device. The result is cached on the block so a scan is only
 * ever read once, which is what lets search look inside documents without a
 * per-query cost.
 *
 * Recognition is a native call with its own lifetime, so the recognizer is
 * held once for the process rather than created per page.
 */
object OcrReader {

    private const val TAG = "OcrReader"

    /**
     * Long edge the page is decoded at before recognition.
     *
     * ML Kit wants at least ~1000px for body text to resolve; beyond about
     * this the extra pixels buy nothing and cost memory.
     */
    private const val MAX_EDGE_PX = 1600

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Reads [file] and returns the text found, or an empty string when the
     * page holds none. Returns null only if the page could not be read at
     * all, so the caller can tell "nothing on it" from "try again later".
     *
     * Decoding happens on the IO dispatcher regardless of what the caller is
     * on. Both call sites reach this from viewModelScope or lifecycleScope,
     * which are main-thread, and a full-page decode there janks the UI — a
     * dozen of them in a row on launch is an ANR waiting to happen.
     */
    suspend fun read(file: File): String? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null

        val bitmap = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) return@withContext null

            var sample = 1
            val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
            while (longEdge / (sample * 2) >= MAX_EDGE_PX) sample *= 2

            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not decode ${file.name}", e)
            null
        } ?: return@withContext null

        try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { result ->
                        // Recycle only once recognition has actually finished.
                        // Doing it in a finally would also fire on
                        // cancellation, freeing a bitmap ML Kit is still
                        // reading from — that is a native crash, not an
                        // exception.
                        bitmap.recycle()
                        if (cont.isActive) cont.resume(result.text)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Recognition failed for ${file.name}", e)
                        bitmap.recycle()
                        if (cont.isActive) cont.resume(null)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recognition threw for ${file.name}", e)
            null
        }
    }

    /**
     * Picks a document name out of recognised text.
     *
     * Real documents put their title at the top in the largest type, so the
     * first few lines are the useful ones. Lines that are mostly digits,
     * punctuation, or a single character are skipped — those are reference
     * numbers and form furniture, not names.
     */
    fun suggestName(ocrText: String?): String? {
        if (ocrText.isNullOrBlank()) return null

        val candidate = ocrText.lineSequence()
            .map { it.trim() }
            .filter { it.length in 4..40 }
            .filter { line ->
                val letters = line.count { it.isLetter() }
                // At least half letters, and more than one word's worth.
                letters >= line.length / 2 && letters >= 4
            }
            .filterNot { line ->
                // Skip the boilerplate that tops most official forms.
                val lower = line.lowercase()
                lower.startsWith("government of") ||
                    lower.startsWith("republic of") ||
                    lower.startsWith("http") ||
                    lower.all { !it.isLetter() }
            }
            .take(6)
            .maxByOrNull { line ->
                // Prefer a line that reads like a title: several words, and
                // set in capitals the way headings usually are.
                val words = line.split(Regex("\\s+")).size
                val caps = line.count { it.isUpperCase() }
                words * 2 + caps
            }
            ?: return null

        return candidate
            .split(Regex("\\s+"))
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
            .take(40)
    }
}
