package com.dockeep.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.dockeep.app.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The onboarding vault.
 *
 * A real six-face box drawn in perspective, not a flat stack: the faces are
 * defined as 3D quads, rotated by the idle animation, projected through the
 * same 900px focal length the design uses, then painted back-to-front. The
 * door is hinged on its left edge and swings open on a loop to show the red
 * interior and the three documents inside, while the padlock shackle lifts.
 *
 * Everything on the door and on each sheet is drawn in that surface's own
 * plane via [Matrix.setPolyToPoly], so the padlock, the letter tiles and the
 * ruled lines all take the same perspective as the face they sit on.
 */
class VaultView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** A point in the box's own space, before rotation and projection. */
    private data class P3(val x: Float, val y: Float, val z: Float)

    /** A quad plus how to paint it, ready to be depth-sorted. */
    private class Face(
        val pts: Array<P3>,
        val fill: Int,
        val depth: Float,
        /** Size of this face in its own units, so surfaces keep their aspect. */
        val localW: Float = 1f,
        val localH: Float = 1f,
        val surface: ((Canvas) -> Unit)? = null
    )

    // ── Box dimensions, matching the design's 150 x 110 x 100 vault ──────
    private val halfW = 75f
    private val halfH = 55f
    private val halfD = 50f
    private val focal = 900f

    // ── Animation state ─────────────────────────────────────────────────
    private var yaw = -26f
    private var pitch = 13f
    private var doorAngle = 0f
    private var docLiftA = 0f
    private var docLiftB = 0f
    private var docLiftC = 0f
    private var shackleLift = 0f

    private var animator: ValueAnimator? = null

    // ── Paints ──────────────────────────────────────────────────────────
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.MITER
    }
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val density = resources.displayMetrics.density

    private val colShellSide = color(R.color.ledger_neutral_300)
    private val colShellTop = color(R.color.ledger_neutral_200)
    private val colShellBottom = color(R.color.ledger_neutral_500)
    private val colInterior = color(R.color.ledger_accent)
    private val colDoor = color(R.color.ledger_neutral_200)
    private val colSheet = color(R.color.ledger_neutral_100)
    private val colRule = color(R.color.ledger_rule_strong)
    private val colFaint = color(R.color.ledger_neutral_400)
    private val colDoorText = color(R.color.ledger_neutral_700)

    private val archivoBlack: Typeface? =
        ResourcesCompat.getFont(context, R.font.archivo_black)
    private val archivoBold: Typeface? =
        ResourcesCompat.getFont(context, R.font.archivo_bold)

    /** The three sheets inside: tile colour, glyph colour, letter, rule width. */
    private val sheets = listOf(
        Triple(color(R.color.doc_tile_yellow), Color.BLACK, "A"),
        Triple(color(R.color.doc_tile_red), Color.WHITE, "P"),
        Triple(color(R.color.doc_tile_purple), Color.WHITE, "D")
    )

    private fun color(id: Int) = ContextCompat.getColor(context, id)

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startIdleLoop()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    /**
     * The 12-second idle loop from the design: the box turns slowly while the
     * door opens, holds, and shuts again.
     */
    private fun startIdleLoop() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 12_000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float

                // Box turn: a slow sway rather than a full spin.
                val sway = (sin(t * 2 * Math.PI) * 0.5 + 0.5).toFloat()
                yaw = lerp(-26f, -4f, sway)
                pitch = lerp(13f, 10f, sway)

                // Door: shut, open, hold, shut.
                doorAngle = keyframes(
                    t,
                    floatArrayOf(0f, 0.15f, 0.38f, 0.64f, 0.87f, 1f),
                    floatArrayOf(0f, 0f, 74f, 74f, 0f, 0f)
                )

                // The sheets rise a little while the door stands open.
                docLiftA = keyframes(
                    t,
                    floatArrayOf(0f, 0.22f, 0.44f, 0.62f, 0.84f, 1f),
                    floatArrayOf(0f, 0f, 3f, 3f, 0f, 0f)
                )
                docLiftB = keyframes(
                    t,
                    floatArrayOf(0f, 0.24f, 0.46f, 0.62f, 0.82f, 1f),
                    floatArrayOf(0f, 0f, 9f, 9f, 0f, 0f)
                )
                docLiftC = keyframes(
                    t,
                    floatArrayOf(0f, 0.26f, 0.48f, 0.62f, 0.80f, 1f),
                    floatArrayOf(0f, 0f, 15f, 15f, 0f, 0f)
                )

                shackleLift = keyframes(
                    t,
                    floatArrayOf(0f, 0.16f, 0.40f, 0.64f, 0.86f, 1f),
                    floatArrayOf(0f, 0f, 7f, 7f, 0f, 0f)
                )

                invalidate()
            }
            start()
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Piecewise-linear keyframe lookup, mirroring the design's CSS stops. */
    private fun keyframes(t: Float, stops: FloatArray, values: FloatArray): Float {
        for (i in 0 until stops.size - 1) {
            if (t >= stops[i] && t <= stops[i + 1]) {
                val span = stops[i + 1] - stops[i]
                val local = if (span <= 0f) 0f else (t - stops[i]) / span
                // Ease in-out so the door does not start and stop abruptly.
                val eased = local * local * (3f - 2f * local)
                return lerp(values[i], values[i + 1], eased)
            }
        }
        return values.last()
    }

    // ── Projection ──────────────────────────────────────────────────────

    private var scale = 1f
    private var cx = 0f
    private var cy = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        // Fit the box, allowing headroom for the door when it swings open.
        scale = minOf(w / 260f, h / 170f)
    }

    /** Rotates a point by the current pitch and yaw. */
    private fun rotate(p: P3): P3 {
        val ry = Math.toRadians(yaw.toDouble())
        val rx = Math.toRadians(pitch.toDouble())

        // Yaw about the vertical axis.
        val x1 = (p.x * cos(ry) + p.z * sin(ry)).toFloat()
        val z1 = (-p.x * sin(ry) + p.z * cos(ry)).toFloat()

        // Pitch about the horizontal axis.
        val y2 = (p.y * cos(rx) - z1 * sin(rx)).toFloat()
        val z2 = (p.y * sin(rx) + z1 * cos(rx)).toFloat()

        return P3(x1, y2, z2)
    }

    /** Perspective divide, using the same focal length as the design. */
    private fun project(p: P3): FloatArray {
        val r = rotate(p)
        val k = focal / (focal - r.z)
        return floatArrayOf(cx + r.x * k * scale, cy + r.y * k * scale)
    }

    private fun depthOf(pts: Array<P3>): Float = pts.map { rotate(it).z }.average().toFloat()

    // ── Geometry ────────────────────────────────────────────────────────

    /**
     * A point on the door, given [u] along its width from the hinge and [v]
     * down its height. The door is hinged on the box's left front edge and
     * swings out toward the viewer.
     */
    private fun doorPoint(u: Float, v: Float): P3 {
        val a = Math.toRadians(doorAngle.toDouble())
        val x = -halfW + u * cos(a).toFloat()
        val z = halfD + u * sin(a).toFloat()
        return P3(x, v, z)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        strokePaint.strokeWidth = 2f * density

        val faces = mutableListOf<Face>()

        // Interior back wall — the red the design reveals when the door opens.
        val back = arrayOf(
            P3(-halfW, -halfH, -halfD), P3(halfW, -halfH, -halfD),
            P3(halfW, halfH, -halfD), P3(-halfW, halfH, -halfD)
        )
        faces += Face(back, colInterior, depthOf(back))

        // Left and right walls.
        val left = arrayOf(
            P3(-halfW, -halfH, -halfD), P3(-halfW, -halfH, halfD),
            P3(-halfW, halfH, halfD), P3(-halfW, halfH, -halfD)
        )
        faces += Face(left, colShellSide, depthOf(left))

        val right = arrayOf(
            P3(halfW, -halfH, halfD), P3(halfW, -halfH, -halfD),
            P3(halfW, halfH, -halfD), P3(halfW, halfH, halfD)
        )
        faces += Face(right, colShellSide, depthOf(right))

        // Top and bottom.
        val top = arrayOf(
            P3(-halfW, -halfH, -halfD), P3(halfW, -halfH, -halfD),
            P3(halfW, -halfH, halfD), P3(-halfW, -halfH, halfD)
        )
        faces += Face(top, colShellTop, depthOf(top))

        val bottom = arrayOf(
            P3(-halfW, halfH, halfD), P3(halfW, halfH, halfD),
            P3(halfW, halfH, -halfD), P3(-halfW, halfH, -halfD)
        )
        faces += Face(bottom, colShellBottom, depthOf(bottom))

        // The three sheets, each lifting a little while the door is open.
        val lifts = floatArrayOf(docLiftA, docLiftB, docLiftC)
        val zs = floatArrayOf(-24f, -10f, 4f)
        for (i in 0..2) {
            val z = zs[i]
            val lift = lifts[i]
            val sw = 42f
            val sh = 40f
            val pts = arrayOf(
                P3(-sw, -sh - lift, z), P3(sw, -sh - lift, z),
                P3(sw, sh - lift, z), P3(-sw, sh - lift, z)
            )
            val (tile, glyph, letter) = sheets[i]
            faces += Face(pts, colSheet, depthOf(pts), 84f, 80f) { c ->
                drawSheetSurface(c, tile, glyph, letter)
            }
        }

        // The door. Skipped once it is almost flat to the eye, where a quad
        // would collapse into a line and the stroke would flicker.
        val door = arrayOf(
            doorPoint(0f, -halfH), doorPoint(150f, -halfH),
            doorPoint(150f, halfH), doorPoint(0f, halfH)
        )
        faces += Face(door, colDoor, depthOf(door), 150f, 110f) { c -> drawDoorSurface(c) }

        // Painter's algorithm: farthest first.
        faces.sortBy { it.depth }
        for (face in faces) drawFace(canvas, face)
    }

    private fun drawFace(canvas: Canvas, face: Face) {
        val p = face.pts.map { project(it) }

        val path = Path()
        path.moveTo(p[0][0], p[0][1])
        for (i in 1 until p.size) path.lineTo(p[i][0], p[i][1])
        path.close()

        fillPaint.color = face.fill
        canvas.drawPath(path, fillPaint)

        strokePaint.color = colRule
        canvas.drawPath(path, strokePaint)

        // Draw whatever sits on this surface, in the surface's own plane.
        val surface = face.surface ?: return
        val area = quadArea(p)
        if (area < 8f) return

        val dst = floatArrayOf(
            p[0][0], p[0][1], p[1][0], p[1][1],
            p[2][0], p[2][1], p[3][0], p[3][1]
        )
        val src = floatArrayOf(
            0f, 0f,
            face.localW, 0f,
            face.localW, face.localH,
            0f, face.localH
        )
        val m = Matrix()
        if (!m.setPolyToPoly(src, 0, dst, 0, 4)) return

        canvas.save()
        canvas.concat(m)
        surface(canvas)
        canvas.restore()
    }

    private fun quadArea(p: List<FloatArray>): Float {
        var a = 0f
        for (i in p.indices) {
            val j = (i + 1) % p.size
            a += p[i][0] * p[j][1] - p[j][0] * p[i][1]
        }
        return abs(a) / 2f
    }

    /**
     * Painted in the door's own plane, in 0..1 coordinates: the accent padlock
     * with its shackle and keyhole, and the ON DEVICE line beneath.
     */
    private fun drawDoorSurface(canvas: Canvas) {
        // Door units: 150 wide, 110 tall.
        val cxD = 75f
        val cyD = 50f

        // Padlock body.
        surfacePaint.style = Paint.Style.FILL
        surfacePaint.color = colInterior
        canvas.drawRect(cxD - 22f, cyD - 17f, cxD + 22f, cyD + 17f, surfacePaint)

        surfacePaint.style = Paint.Style.STROKE
        surfacePaint.strokeWidth = 2f
        surfacePaint.color = colRule
        canvas.drawRect(cxD - 22f, cyD - 17f, cxD + 22f, cyD + 17f, surfacePaint)

        // Shackle, lifting out of the body on the loop.
        val top = cyD - 17f - shackleLift
        val arc = Path()
        arc.moveTo(cxD - 11f, top)
        arc.lineTo(cxD - 11f, top - 6f)
        arc.quadTo(cxD, top - 24f, cxD + 11f, top - 6f)
        arc.lineTo(cxD + 11f, top)
        canvas.drawPath(arc, surfacePaint)

        // Keyhole.
        surfacePaint.style = Paint.Style.FILL
        surfacePaint.color = colRule
        canvas.drawRect(cxD - 2.5f, cyD - 5.5f, cxD + 2.5f, cyD + 5.5f, surfacePaint)

        // ON DEVICE, tracked, under the lock.
        textPaint.typeface = archivoBold
        textPaint.color = colDoorText
        textPaint.textSize = 8.5f
        textPaint.letterSpacing = 0.2f
        canvas.drawText("ON DEVICE", cxD, cyD + 34f, textPaint)
    }

    /** A sheet's own face: the letter tile and two ruled lines. */
    private fun drawSheetSurface(canvas: Canvas, tile: Int, glyph: Int, letter: String) {
        // Sheet units: 84 wide, 80 tall, 7 of padding as in the design.
        surfacePaint.style = Paint.Style.FILL
        surfacePaint.color = tile
        canvas.drawRect(7f, 7f, 27f, 27f, surfacePaint)

        textPaint.typeface = archivoBlack
        textPaint.color = glyph
        textPaint.textSize = 13f
        textPaint.letterSpacing = 0f
        canvas.drawText(letter, 17f, 22f, textPaint)

        surfacePaint.color = colFaint
        canvas.drawRect(7f, 35f, 77f, 37f, surfacePaint)
        canvas.drawRect(7f, 42f, 57f, 44f, surfacePaint)
    }
}
