package com.dockeep.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dockeep.app.utils.AppLock
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.dockeep.app.ui.RedactionOverlay
import com.dockeep.app.utils.FileUtils
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Editing tools for a scan already stored in a document.
 *
 * Scanning applies its own correction on capture, but anything already in the
 * vault — imported from the gallery, captured before this existed, or simply
 * shot badly — needs the same treatment afterwards. This screen provides it:
 * rotate, crop, a document filter, and brightness / contrast.
 *
 * The preview is a downscaled bitmap with a [ColorMatrixColorFilter] applied
 * live, which costs nothing to drag a slider against. The same matrix is baked
 * into the full-resolution image only on Save, and written back over the
 * original path so the block keeps its place in the document.
 */
class ScanEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "image_path"

        /** Long edge of the preview bitmap. Enough to judge a filter by. */
        private const val PREVIEW_MAX_PX = 1400

        private const val PREFS_NAME = "app_prefs"
        private const val THEME_PREF = "is_dark_theme"

        fun intent(context: Context, imagePath: String): Intent =
            Intent(context, ScanEditorActivity::class.java)
                .putExtra(EXTRA_IMAGE_PATH, imagePath)
    }

    /** The document filters, in the order the chips present them. */
    private enum class Filter { ORIGINAL, GREY, MONO, ENHANCE }

    private lateinit var preview: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var filterChips: ChipGroup
    private lateinit var brightnessSeek: SeekBar
    private lateinit var contrastSeek: SeekBar
    private lateinit var redaction: RedactionOverlay
    private lateinit var redactIcon: ImageView
    private lateinit var redactLabel: TextView

    private var imagePath: String = ""

    /** Downscaled source for the preview, before rotation. */
    private var previewSource: Bitmap? = null

    /** The rotated copy currently on screen, if rotation is not zero. */
    private var rotatedPreview: Bitmap? = null

    /** Rotation in degrees, always a multiple of 90. */
    private var rotation = 0

    private var filter = Filter.ORIGINAL
    private var brightness = 0f   // -1..1
    private var contrast = 1f     // 0.5..2

    /** Set when Crop replaces the pixels, so Save knows to use the new file. */
    private var croppedSource: File? = null

    private val cropLauncher = registerForActivityResult(CropImageContract()) { result ->
        if (!result.isSuccessful) return@registerForActivityResult
        val uri = result.uriContent ?: return@registerForActivityResult

        // The crop is already applied to the pixels, so the rotation the user
        // had dialled in is now baked in too and must be reset.
        lifecycleScope.launch {
            val staged = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(cacheDir, "scan_edit_crop.jpg")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                    target.takeIf { it.exists() && it.length() > 0 }
                }.getOrNull()
            }
            if (staged == null) {
                toast(R.string.ledger_scan_edit_failed)
                return@launch
            }
            croppedSource = staged
            rotation = 0
            redaction.clear()
            previewSource?.recycle()
            previewSource = withContext(Dispatchers.IO) { decodePreview(staged.absolutePath) }
            applyPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyStoredTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_editor)

        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH).orEmpty()
        if (imagePath.isEmpty() || !File(imagePath).exists()) {
            toast(R.string.ledger_scan_missing)
            finish()
            return
        }

        bindViews()
        loadPreview()
    }

    override fun onDestroy() {
        // The ImageView may still hold the rotated copy, so drop it first.
        preview.setImageDrawable(null)
        rotatedPreview?.recycle()
        rotatedPreview = null
        previewSource?.recycle()
        previewSource = null
        super.onDestroy()
    }

    private fun applyStoredTheme() {
        val dark = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(THEME_PREF, false)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun bindViews() {
        preview = findViewById(R.id.editorPreview)
        progress = findViewById(R.id.editorProgress)
        filterChips = findViewById(R.id.filterChips)
        brightnessSeek = findViewById(R.id.brightnessSeek)
        contrastSeek = findViewById(R.id.contrastSeek)
        redaction = findViewById(R.id.redactionOverlay)
        redactIcon = findViewById(R.id.toolRedactIcon)
        redactLabel = findViewById(R.id.toolRedactLabel)

        findViewById<View>(R.id.toolRedact).setOnClickListener { toggleRedaction() }
        redaction.onChanged = { count ->
            // While the tool is on, the label doubles as the count.
            if (redaction.isActive) {
                redactLabel.text = if (count == 0) {
                    getString(R.string.ledger_redact_on)
                } else {
                    getString(R.string.ledger_redact_count, count)
                }
            }
        }

        findViewById<View>(R.id.editorClose).setOnClickListener { finish() }
        findViewById<View>(R.id.editorCancel).setOnClickListener { finish() }
        findViewById<View>(R.id.editorSave).setOnClickListener { save() }

        findViewById<View>(R.id.toolRotateLeft).setOnClickListener { rotateBy(-90) }
        findViewById<View>(R.id.toolRotateRight).setOnClickListener { rotateBy(90) }
        findViewById<View>(R.id.toolCrop).setOnClickListener { startCrop() }
        findViewById<View>(R.id.toolReset).setOnClickListener { resetAdjustments() }

        filterChips.setOnCheckedStateChangeListener { _, checked ->
            filter = when (checked.firstOrNull()) {
                R.id.filterGrey -> Filter.GREY
                R.id.filterMono -> Filter.MONO
                R.id.filterEnhance -> Filter.ENHANCE
                else -> Filter.ORIGINAL
            }
            applyPreview()
        }

        val slider = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                when (bar.id) {
                    // 0..100 maps to -1..1 around a neutral centre.
                    R.id.brightnessSeek -> brightness = (value - 50) / 50f
                    // 0..100 maps to 0.5..2, again neutral at the centre.
                    R.id.contrastSeek ->
                        contrast = if (value <= 50) {
                            0.5f + (value / 50f) * 0.5f
                        } else {
                            1f + ((value - 50) / 50f)
                        }
                }
                applyPreview()
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        }
        brightnessSeek.setOnSeekBarChangeListener(slider)
        contrastSeek.setOnSeekBarChangeListener(slider)
    }

    private fun resetAdjustments() {
        rotation = 0
        filter = Filter.ORIGINAL
        brightness = 0f
        contrast = 1f
        croppedSource = null
        redaction.clear()
        brightnessSeek.progress = 50
        contrastSeek.progress = 50
        filterChips.check(R.id.filterOriginal)
        loadPreview()
    }

    /**
     * Turns the redaction tool on or off.
     *
     * While it is on the overlay swallows touches so a drag draws a box; the
     * marks stay on screen when it is off, and are only burned in on Save.
     */
    private fun toggleRedaction() {
        redaction.isActive = !redaction.isActive
        val on = redaction.isActive
        redactIcon.setColorFilter(
            ContextCompat.getColor(this, if (on) R.color.ledger_accent else R.color.ledger_text)
        )
        redactLabel.setTextColor(
            ContextCompat.getColor(this, if (on) R.color.ledger_accent else R.color.ledger_text)
        )
        redactLabel.text = if (on) {
            val n = redaction.count()
            if (n == 0) getString(R.string.ledger_redact_on)
            else getString(R.string.ledger_redact_count, n)
        } else {
            getString(R.string.ledger_redact)
        }
        if (on) {
            Toast.makeText(this, R.string.ledger_redact_hint, Toast.LENGTH_SHORT).show()
        }
        syncRedactionBounds()
    }

    /**
     * Tells the overlay where the scan is actually drawn.
     *
     * fitCenter letterboxes, so the image rarely fills the view; without this
     * the boxes would map onto the wrong part of the file.
     */
    private fun syncRedactionBounds() {
        val drawable = preview.drawable ?: return
        val values = FloatArray(9)
        preview.imageMatrix.getValues(values)
        val scaleX = values[android.graphics.Matrix.MSCALE_X]
        val scaleY = values[android.graphics.Matrix.MSCALE_Y]
        val transX = values[android.graphics.Matrix.MTRANS_X]
        val transY = values[android.graphics.Matrix.MTRANS_Y]

        val w = drawable.intrinsicWidth * scaleX
        val h = drawable.intrinsicHeight * scaleY
        redaction.setImageBounds(
            android.graphics.RectF(
                transX + preview.paddingLeft,
                transY + preview.paddingTop,
                transX + preview.paddingLeft + w,
                transY + preview.paddingTop + h
            )
        )
    }

    private fun rotateBy(degrees: Int) {
        // The marks are positioned against the image as drawn; turning it
        // would leave them over the wrong content.
        if (redaction.count() > 0) {
            redaction.clear()
            Toast.makeText(this, R.string.ledger_redact_cleared, Toast.LENGTH_SHORT).show()
        }
        rotation = ((rotation + degrees) % 360 + 360) % 360
        applyPreview()
    }

    private fun startCrop() {
        val source = croppedSource ?: File(imagePath)
        cropLauncher.launch(
            CropImageContractOptions(
                uri = FileUtils.getUriForFile(this, source),
                cropImageOptions = CropImageOptions(
                    guidelines = CropImageView.Guidelines.ON,
                    outputCompressFormat = Bitmap.CompressFormat.JPEG,
                    outputCompressQuality = 92,
                    allowRotation = true,
                    allowFlipping = true,
                    autoZoomEnabled = true,
                    activityTitle = getString(R.string.ledger_crop)
                )
            )
        )
    }

    // ══ Preview ═════════════════════════════════════════════════════════

    private fun loadPreview() {
        val path = (croppedSource ?: File(imagePath)).absolutePath
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodePreview(path) }
            progress.visibility = View.GONE
            if (bitmap == null) {
                toast(R.string.ledger_scan_missing)
                finish()
                return@launch
            }
            previewSource?.recycle()
            previewSource = bitmap
            applyPreview()
        }
    }

    /** Decodes at roughly [PREVIEW_MAX_PX] so sliders stay responsive. */
    private fun decodePreview(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null

        var sample = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / (sample * 2) >= PREVIEW_MAX_PX) sample *= 2

        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private fun applyPreview() {
        val source = previewSource ?: return

        // Rotate the bitmap rather than the view: View.rotation spins the
        // drawing but leaves the bounds alone, so a quarter turn on a portrait
        // scan gets clipped by the frame.
        val shown = if (rotation == 0) {
            source
        } else {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
        }

        preview.colorFilter = ColorMatrixColorFilter(buildMatrix())
        preview.setImageBitmap(shown)
        preview.post { syncRedactionBounds() }

        // Release the previous rotated copy; the unrotated source is kept.
        rotatedPreview?.takeIf { it != shown && it != source }?.recycle()
        rotatedPreview = shown.takeIf { it != source }
    }

    /**
     * The colour matrix for the current filter and adjustments.
     *
     * Filters are expressed as saturation plus a contrast/brightness pair
     * rather than as a per-pixel pass, so the preview costs one matrix and the
     * bake costs a single [Canvas.drawBitmap].
     */
    private fun buildMatrix(): ColorMatrix {
        val matrix = ColorMatrix()

        // Filter first: it sets the baseline the sliders then work against.
        val (filterSat, filterContrast, filterBright) = when (filter) {
            Filter.ORIGINAL -> Triple(1f, 1f, 0f)
            Filter.GREY -> Triple(0f, 1f, 0f)
            // Hard document look: no colour, and a steep curve so paper goes
            // white and ink goes black.
            Filter.MONO -> Triple(0f, 3.2f, 0.06f)
            // Gentle clean-up that keeps colour, for stamps and signatures.
            Filter.ENHANCE -> Triple(1.05f, 1.45f, 0.06f)
        }
        matrix.setSaturation(filterSat)

        val scale = filterContrast * contrast
        // Pivot contrast around mid-grey so the image does not slide dark.
        val shift = (-0.5f * scale + 0.5f + filterBright + brightness) * 255f

        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, shift,
                    0f, scale, 0f, 0f, shift,
                    0f, 0f, scale, 0f, shift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return matrix
    }

    // ══ Save ════════════════════════════════════════════════════════════

    /** Snapshot taken on the main thread; bakeAndWrite runs off it. */
    private var redactionBoxes: List<android.graphics.RectF> = emptyList()

    private fun save() {
        redactionBoxes = redaction.boxesAsFractions()
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { bakeAndWrite() }
            progress.visibility = View.GONE
            if (ok) {
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                toast(R.string.ledger_scan_edit_failed)
            }
        }
    }

    /**
     * Applies rotation and the colour matrix to the full-resolution image and
     * writes it back over the original path.
     *
     * Writes to a temp file first and only then replaces the original, so a
     * failure part-way through cannot leave a truncated scan behind.
     */
    private fun bakeAndWrite(): Boolean {
        val sourcePath = (croppedSource ?: File(imagePath)).absolutePath
        var source: Bitmap? = null
        var output: Bitmap? = null
        return try {
            source = BitmapFactory.decodeFile(sourcePath) ?: return false

            val swap = rotation == 90 || rotation == 270
            val outW = if (swap) source.height else source.width
            val outH = if (swap) source.width else source.height

            output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawColor(android.graphics.Color.WHITE)

            val matrix = Matrix().apply {
                postRotate(rotation.toFloat(), source.width / 2f, source.height / 2f)
                // Re-centre after a quarter turn changes the bounding box.
                if (swap) {
                    postTranslate((outW - source.width) / 2f, (outH - source.height) / 2f)
                }
            }

            canvas.drawBitmap(
                source,
                matrix,
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(buildMatrix())
                }
            )

            // Burn the redactions in. Done on the output bitmap so the black
            // is part of the pixels, not an overlay a viewer could strip.
            val marks = redactionBoxes
            if (marks.isNotEmpty()) {
                val black = Paint().apply { color = android.graphics.Color.BLACK }
                for (box in marks) {
                    canvas.drawRect(
                        box.left * outW, box.top * outH,
                        box.right * outW, box.bottom * outH,
                        black
                    )
                }
            }

            val temp = File(cacheDir, "scan_edit_out.jpg")
            FileOutputStream(temp).use { out ->
                output.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (!temp.exists() || temp.length() == 0L) return false

            temp.copyTo(File(imagePath), overwrite = true)
            temp.delete()
            croppedSource?.delete()
            true
        } catch (e: Exception) {
            Log.e("ScanEditor", "Could not write the edited scan", e)
            false
        } finally {
            output?.recycle()
            source?.recycle()
        }
    }

    private fun toast(resId: Int) =
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        // Coming back from recents can land directly on this screen, which
        // would show its contents without the vault ever being unlocked.
        // Finishing returns to the gated home screen, which does the asking.
        if (AppLock.shouldChallenge(this)) finish()
    }

}
