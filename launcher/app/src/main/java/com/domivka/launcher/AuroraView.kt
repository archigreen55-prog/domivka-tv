package com.domivka.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Window background: sky gradient + stars. Opaque, drawn once per frame as a single gradient fill. */
class SkyDrawable : Drawable() {
    private val sky = Paint()
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun onBoundsChange(b: android.graphics.Rect) {
        sky.shader = LinearGradient(
            0f, 0f, 0f, b.height().toFloat(),
            intArrayOf(0xFF040815.toInt(), 0xFF0B1F3A.toInt(), 0xFF10324A.toInt()), floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(c: Canvas) {
        c.drawRect(bounds, sky)
        val kx = bounds.width() / 1920f
        val ky = bounds.height() / 1080f
        val r = min(kx, ky)
        for (s in STARS) {
            star.alpha = (s[3] * 255).toInt()
            c.drawCircle(s[0] * kx, s[1] * ky, s[2] * r, star)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE

    private companion object {
        val STARS = arrayOf(
            floatArrayOf(140f, 80f, 1.6f, .8f), floatArrayOf(420f, 160f, 1.2f, .6f),
            floatArrayOf(760f, 60f, 1.8f, .7f), floatArrayOf(1100f, 130f, 1.3f, .6f),
            floatArrayOf(1500f, 70f, 1.7f, .8f), floatArrayOf(1780f, 150f, 1.4f, .6f),
            floatArrayOf(1650f, 40f, 1.2f, .5f), floatArrayOf(300f, 260f, 1.3f, .5f),
        )
    }
}

/**
 * The aurora ribbons, pre-rendered ONCE (software canvas + BlurMaskFilter, at 1/4 resolution so they are already soft)
 * and shared by all AuroraViews. Coordinates below are in mockup space (1920x1080).
 */
object AuroraAssets {
    const val S = 4f          // mockup px per bitmap px
    const val X0 = -500f      // every bitmap covers mockup x in [-500, 2420]; its y range is cropped to the ribbon's band
    private const val BW = 730
    private const val MINT = 0xFF2EF2B6.toInt()
    private const val CYAN = 0xFF2EC4E6.toInt()
    private const val VIOLET = 0xFF9B6BFF.toInt()

    /** y(x) = cy + a1*sin(2*pi*x/l1 + p1) + a2*sin(2*pi*x/l2 + p2) */
    class Spec(
        val cy: Float, val a1: Float, val l1: Float, val p1: Float, val a2: Float, val l2: Float, val p2: Float,
        val width: Float, val alpha: Float, val c0: Int, val c1: Int, val c2: Int,
    )

    val specs = arrayOf(
        Spec(420f, 110f, 1500f, 0.6f, 40f, 620f, 1.9f, 90f, 0.55f, MINT, CYAN, VIOLET),
        Spec(560f, 90f, 1300f, 2.4f, 35f, 540f, 0.4f, 60f, 0.42f, VIOLET, CYAN, MINT),
        // 3 ribbons (was 4): ~25 % less GPU fill per frame on the Mali-450; the 3rd one now also carries violet.
        Spec(270f, 70f, 1100f, 4.0f, 30f, 480f, 2.2f, 40f, 0.40f, MINT, VIOLET, CYAN),
    )

    /** [top] = mockup y of the bitmap's first row. */
    class Ribbon(val bmp: Bitmap, val top: Float)

    @Volatile
    var bitmaps: Array<Ribbon>? = null
        private set
    private val waiting = ArrayList<() -> Unit>()
    private var building = false

    /** Main thread only. Builds the bitmaps on a background thread the first time. */
    fun whenReady(cb: () -> Unit) {
        if (bitmaps != null) return cb()
        waiting.add(cb)
        if (building) return
        building = true
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            val built = Array(specs.size) { render(specs[it]) }
            Handler(Looper.getMainLooper()).post {
                bitmaps = built
                waiting.forEach { it() }
                waiting.clear()
            }
        }.start()
    }

    private fun render(sp: Spec): Ribbon {
        val reach = sp.a1 + sp.a2 + sp.width / 2f + 100f         // wave amplitude + stroke + blur tail
        val top = maxOf(0f, ((sp.cy - reach) / S).toInt() * S)
        val bmp = Bitmap.createBitmap(BW, Math.ceil(((sp.cy + reach - top) / S).toDouble()).toInt(), Bitmap.Config.ARGB_8888)
        val xs = -380f
        val xe = 2300f
        val path = Path()
        var x = xs
        while (x <= xe) {
            val y = sp.cy + sp.a1 * sin(2 * PI * x / sp.l1 + sp.p1).toFloat() + sp.a2 * sin(2 * PI * x / sp.l2 + sp.p2).toFloat()
            val bx = (x - X0) / S
            val by = (y - top) / S
            if (x == xs) path.moveTo(bx, by) else path.lineTo(bx, by)
            x += 50f
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = sp.width / S
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            shader = LinearGradient((xs - X0) / S, 0f, (xe - X0) / S, 0f, intArrayOf(sp.c0, sp.c1, sp.c2), null, Shader.TileMode.CLAMP)
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL) // sigma ~ 7 bitmap px = ~28 mockup px
        }
        Canvas(bmp).drawPath(path, p)
        return Ribbon(bmp, top)
    }
}

/**
 * Animated aurora: each frame draws 4 small pre-blurred bitmaps scaled up with bilinear filtering, each under a slowly
 * varying affine transform (translate / vertical scale / skew / rotate) and alpha driven by sine waves with different
 * periods (8..25 s), then the static mountains. No allocation, no blur per frame. Capped at ~30 fps.
 *
 * The sky + stars come from the window background ([SkyDrawable]).
 */
class AuroraView(ctx: Context, private val animatable: Boolean = true) : View(ctx), Choreographer.FrameCallback {

    var animationEnabled = true
        set(v) { field = v; refresh() }
    var resumed = false
        set(v) { field = v; refresh() }

    private var windowFocused = false
    private var shown = false
    private var attached = false
    private var running = false
    private var lastNs = 0L
    private var t = STILL_T
    private var step = 2              // redraw every Nth vsync; see doFrame
    private var vsyncCount = 0
    private var winFrames = 0
    private var winMs = 0f

    private var ribbons: Array<AuroraAssets.Ribbon>? = AuroraAssets.bitmaps
    private var readyAt = 0L          // when the ribbons arrived late: fade them in over FADE_MS
    private var mountains: Bitmap? = null
    private var mountainsTop = 0f

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val mountainPaint = Paint()
    private val m = Matrix()

    init {
        setWillNotDraw(false)
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------

    private fun refresh() {
        val run = animatable && animationEnabled && resumed && windowFocused && shown && attached
        if (run && !running) {
            running = true
            lastNs = 0L
            Choreographer.getInstance().postFrameCallback(this)
        } else if (!run && running) {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
        }
        if (!animationEnabled && t != STILL_T) {
            t = STILL_T
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        if (ribbons == null) AuroraAssets.whenReady {
            ribbons = AuroraAssets.bitmaps
            readyAt = SystemClock.uptimeMillis()
            invalidate()
        }
        refresh()
    }

    override fun onDetachedFromWindow() {
        attached = false
        refresh()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        windowFocused = hasWindowFocus
        refresh()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        shown = visibility == VISIBLE
        refresh()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        // Even pacing: we are called on EVERY vsync and redraw on every [step]-th one (2 → steady 30 fps on 60 Hz).
        // postFrameCallbackDelayed() gave uneven 33/50 ms gaps = visible stutter.
        Choreographer.getInstance().postFrameCallback(this)
        if (vsyncCount++ % step != 0) return
        if (lastNs != 0L) {
            val dt = frameTimeNanos - lastNs
            // Clock follows real time (dropped frames don't slow the motion down), capped after long pauses.
            t += min(dt, 100_000_000L) / 1e9f
            // Safety net for a weak GPU: if ~3 s of drawn frames average well below 30 fps, settle on a steady 20 fps.
            winMs += dt / 1e6f
            if (++winFrames == 90) {
                if (step == 2 && winMs / winFrames > 45f) step = 3
                winFrames = 0
                winMs = 0f
            }
        }
        lastNs = frameTimeNanos
        invalidate()
    }

    // ---- drawing ---------------------------------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        mountains = null
        if (w <= 0 || h <= 0) return
        val kx = w / 1920f
        val ky = h / 1080f
        val top = (730 * ky).toInt()
        val bmp = Bitmap.createBitmap(w, h - top, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(0f, -top.toFloat())
        c.scale(kx, ky)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = 0xFF0A1A2C.toInt()
        c.drawPath(poly(0, 880, 160, 800, 300, 850, 470, 740, 640, 830, 820, 760, 1000, 850, 1180, 770, 1360, 840, 1560, 750, 1740, 830, 1920, 790), fill)
        fill.color = 0xFF06111F.toInt()
        c.drawPath(poly(0, 960, 240, 900, 480, 950, 740, 890, 1000, 960, 1260, 900, 1520, 960, 1760, 910, 1920, 940), fill)
        mountains = bmp
        mountainsTop = top.toFloat()
    }

    private fun poly(vararg xy: Int): Path {
        val p = Path()
        p.moveTo(xy[0].toFloat(), xy[1].toFloat())
        for (i in 2 until xy.size step 2) p.lineTo(xy[i].toFloat(), xy[i + 1].toFloat())
        p.lineTo(1920f, 1080f)
        p.lineTo(0f, 1080f)
        p.close()
        return p
    }

    override fun onDraw(c: Canvas) {
        val rb = ribbons
        if (rb != null) {
            val kx = width / 1920f
            val ky = height / 1080f
            val px = width / 2f
            val py = height * 0.42f
            val fade = if (readyAt == 0L) 1f else ((SystemClock.uptimeMillis() - readyAt) / FADE_MS).coerceIn(0f, 1f)
            if (fade < 1f) postInvalidateOnAnimation()
            for (i in rb.indices) {
                val mo = MOTION[i]
                m.setScale(AuroraAssets.S * kx, AuroraAssets.S * ky)
                m.postTranslate(AuroraAssets.X0 * kx - px, rb[i].top * ky - py)
                m.postScale(1f + mo[6] * wave(mo[7], mo[8]), 1f + mo[9] * wave(mo[10], mo[11]))
                m.postSkew(mo[12] * wave(mo[13], mo[14]), 0f)
                m.postRotate(mo[15] * wave(mo[16], mo[17]))
                m.postTranslate(px + mo[0] * kx * wave(mo[1], mo[2]), py + mo[3] * ky * wave(mo[4], mo[5]))
                val a = AuroraAssets.specs[i].alpha * (1.0f + 0.3f * wave(mo[18], mo[19])) * fade
                paint.alpha = (a * 255f).toInt().coerceIn(0, 255)
                c.drawBitmap(rb[i].bmp, m, paint)
            }
        }
        mountains?.let { c.drawBitmap(it, 0f, mountainsTop, mountainPaint) }
    }

    /** Jump the animation clock (used by the screenshot tests). */
    fun seek(seconds: Float) {
        t = seconds
        invalidate()
    }

    // SPEED < 1 slows every motion down: smaller change per frame = smoother look at 30 fps.
    private fun wave(period: Float, phase: Float) = sin(2.0 * PI * (t * SPEED / period + phase)).toFloat()

    private companion object {
        const val STILL_T = 6f
        const val SPEED = 0.7f
        const val FADE_MS = 900f

        // tx amp(px) T phase | ty amp T phase | xscale amp T phase | yscale amp T phase | skew amp T phase | rot(deg) amp T phase | alpha T phase
        // layout: [0..2] tx, [3..5] ty, [6..8] x-scale, [9..11] y-scale, [12..14] skew, [15..17] rotation, [18..19] alpha
        val MOTION = arrayOf(
            floatArrayOf(150f, 23f, 0.00f, 36f, 17f, 0.20f, 0.03f, 21f, 0.40f, 0.16f, 13f, 0.50f, 0.05f, 11f, 0.70f, 2.0f, 19f, 0.10f, 9f, 0.00f),
            floatArrayOf(-180f, 19f, 0.30f, 44f, 25f, 0.60f, 0.04f, 17f, 0.90f, 0.20f, 15f, 0.10f, -0.06f, 14f, 0.20f, -2.5f, 21f, 0.50f, 12f, 0.40f),
            floatArrayOf(130f, 16f, 0.70f, 50f, 11f, 0.10f, 0.03f, 23f, 0.20f, 0.22f, 8f, 0.80f, 0.07f, 17f, 0.90f, 1.6f, 24f, 0.30f, 8f, 0.20f),
            floatArrayOf(-110f, 25f, 0.50f, 40f, 20f, 0.90f, 0.05f, 14f, 0.60f, 0.15f, 22f, 0.30f, -0.05f, 9f, 0.50f, -1.8f, 13f, 0.60f, 15f, 0.70f),
        )
    }
}
