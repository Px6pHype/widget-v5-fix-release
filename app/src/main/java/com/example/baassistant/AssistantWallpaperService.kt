package com.example.baassistant

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.text.TextPaint
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Живые обои «Ассистент SCHALE».
 *
 * Фон: настоящие обои пользователя, уменьшенные, размытые и затонированные в синий —
 * как интерфейс SHITTIM/OS из игры. Поверх плавают частицы и «нимбы».
 * Персонаж: Арона или Плана (выбор в WallpaperSettingsActivity), просто дышит
 * и наклоняется за свайпом страниц — на тапы не реагирует, как обычные живые обои.
 */
class AssistantWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = AssistantEngine()

    private inner class AssistantEngine : Engine() {

        private val main = Handler(Looper.getMainLooper())
        private val io = Executors.newSingleThreadExecutor()
        private val density = resources.displayMetrics.density

        private var active = false
        private var w = 0
        private var h = 0

        // Персонаж
        private var characterId: String? = null
        private var sprite: Bitmap? = null
        private var spriteFile: String? = null

        // Размытый фон
        private var backdrop: Bitmap? = null
        private var loadingBackdrop = false

        // Анимация
        private var t = 0L                    // время анимации, мс
        private var xOffset = 0f              // позиция страницы 0..1
        private var pages = 1f

        private val matrix = Matrix()
        private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val placeholderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13f * density
        }
        private var fallback: Bitmap? = null

        // x, y (0..1), скорость (0..1 в секунду), радиус (dp), тип (0 точка/1 нимб), альфа (0..1)
        private var particles = FloatArray(0)

        private val wallpaperReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                loadBackdropAsync() // пользователь сменил обои — пере-размываем
            }
        }

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!active) return
                drawFrame()
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(false) // тапы не нужны: персонаж просто дышит
        }

        override fun onVisibilityChanged(visible: Boolean) {
            active = visible
            if (visible) {
                reloadCharacterIfNeeded()
                loadBackdropAsync()
                registerReceiverSafe()
                Choreographer.getInstance().removeFrameCallback(frameCallback)
                Choreographer.getInstance().postFrameCallback(frameCallback)
            } else {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
                unregisterReceiverSafe()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width
            h = height
            loadBackdropAsync()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            active = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float,
            yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            this.xOffset = xOffset
            pages = if (xOffsetStep > 0.001f) (1f / xOffsetStep).toInt().coerceAtLeast(1).toFloat() else 1f
        }

        // ---------------- рисование ----------------

        private fun drawFrame() {
            val now = SystemClock.uptimeMillis()
            val sinceLast = now - t
            // Рисуем не чаще ~35 к/с: канвас софтверный, так плавнее и бережнее батарею
            if (t != 0L && sinceLast < FRAME_MIN_MS) return
            val dt = if (t == 0L) 16L else sinceLast.coerceIn(1L, 100L)
            t = now

            val canvas = surfaceHolder.lockCanvas() ?: return
            try {
                drawBackdrop(canvas, now)
                drawParticles(canvas, dt)
                drawCharacter(canvas, now)
            } finally {
                try {
                    surfaceHolder.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    Log.w(TAG, "unlock: $e")
                }
            }
        }

        private fun drawBackdrop(canvas: Canvas, now: Long) {
            val b = backdrop
            if (b != null) {
                val s = max(w.toFloat() / b.width, h.toFloat() / b.height)
                val dx = sin(t / 11000.0).toFloat() * 9f * density
                val dy = sin(t / 15000.0 + 1.7).toFloat() * 5f * density
                matrix.reset()
                matrix.setScale(s, s)
                val offX = ((w - b.width * s) / 2f + dx).coerceIn(w - b.width * s, 0f)
                val offY = ((h - b.height * s) / 2f + dy).coerceIn(h - b.height * s, 0f)
                matrix.postTranslate(offX, offY)
                canvas.drawBitmap(b, matrix, bgPaint)
                canvas.drawColor(0x1410182C) // очень лёгкое затемнение — блюр остаётся виден
            } else {
                drawFallback(canvas, now)
            }
        }

        private fun drawFallback(canvas: Canvas, now: Long) {
            val fb = fallbackBackdrop() ?: return
            val s = max(w.toFloat() / fb.width, h.toFloat() / fb.height) * 1.08f
            val dx = sin(t / 9000.0).toFloat() * 5f * density
            val dy = sin(t / 12000.0 + 1.3).toFloat() * 4f * density
            matrix.reset()
            matrix.setScale(s, s)
            val offX = ((w - fb.width * s) / 2f + dx).coerceIn(w - fb.width * s, 0f)
            val offY = ((h - fb.height * s) / 2f + dy).coerceIn(h - fb.height * s, 0f)
            matrix.postTranslate(offX, offY)
            canvas.drawBitmap(fb, matrix, bgPaint)
        }

        /** Красивый «размытый» запасной фон: тёмная база со световыми пятнами,
         *  как расфокусированный интерфейс SCHALE. Рисуется один раз и кэшируется. */
        private fun fallbackBackdrop(): Bitmap? {
            if (w == 0 || h == 0) return null
            fallback?.let { return it }
            val bw = (w / 4).coerceAtLeast(96)
            val bh = (h / 4).coerceAtLeast(96)
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(0xFF0B1220.toInt())
            fun blob(cx: Float, cy: Float, r: Float, color: Int) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(cx, cy, r, color, 0x00000000, Shader.TileMode.CLAMP)
                }
                c.drawCircle(cx, cy, r, p)
            }
            blob(bw * 0.75f, bh * 0.16f, bw * 0.95f, 0x4466CCFF.toInt())  // голубое свечение сверху
            blob(bw * 0.12f, bh * 0.42f, bw * 0.75f, 0x332E5FA8.toInt())  // синее пятно слева
            blob(bw * 0.55f, bh * 0.88f, bw * 0.85f, 0x2E3AA6FF.toInt())  // подсветка за персонажем
            blob(bw * 0.30f, bh * 0.08f, bw * 0.45f, 0x2EFFB86C.toInt())  // тёплый блик (SHITTIM)
            // плавное затемнение к низу
            c.drawRect(
                0f, 0f, bw.toFloat(), bh.toFloat(),
                Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, bh.toFloat(),
                        0x000B1220, 0x8C0B1220.toInt(), Shader.TileMode.CLAMP
                    )
                }
            )
            fallback = bmp
            return bmp
        }

        private fun drawParticles(canvas: Canvas, dt: Long) {
            if (particles.isEmpty()) initParticles()
            val seconds = dt / 1000f
            var i = 0
            while (i < particles.size) {
                particles[i + 1] -= particles[i + 2] * seconds
                if (particles[i + 1] < -0.06f) {
                    particles[i + 1] = 1.06f
                    particles[i] = Math.random().toFloat()
                }
                val px = particles[i] * w
                val py = particles[i + 1] * h
                val radiusDp = particles[i + 3]
                val alpha = particles[i + 5]
                if (particles[i + 4] < 0.5f) {
                    dotPaint.color = Color.WHITE
                    dotPaint.alpha = (alpha * 255).toInt()
                    canvas.drawCircle(px, py, radiusDp * density, dotPaint)
                } else {
                    haloPaint.color = 0xFF66CCFF.toInt()
                    haloPaint.alpha = (alpha * 255).toInt()
                    haloPaint.strokeWidth = 1.6f * density
                    val rx = radiusDp * 2.4f * density
                    val ry = radiusDp * 0.8f * density
                    canvas.drawOval(px - rx, py - ry, px + rx, py + ry, haloPaint)
                }
                i += 6
            }
        }

        private fun initParticles() {
            val count = 40
            particles = FloatArray(count * 6)
            var i = 0
            while (i < particles.size) {
                particles[i] = Math.random().toFloat()
                particles[i + 1] = Math.random().toFloat()
                particles[i + 2] = (0.015f + Math.random() * 0.05f).toFloat()
                particles[i + 3] = (1.2f + Math.random() * 2.6f).toFloat()
                particles[i + 4] = if (i % 13 == 0) 1f else 0f
                particles[i + 5] = (0.06f + Math.random() * 0.14f).toFloat()
                i += 6
            }
        }

        private fun drawCharacter(canvas: Canvas, now: Long) {
            val sp = sprite
            if (sp == null) {
                placeholderPaint.alpha = 150
                val msg = spriteFile?.let { "Положи $it в assets/sprites" } ?: "..."
                canvas.drawText(msg, charCenterX(), h * 0.5f, placeholderPaint)
                placeholderPaint.alpha = 255
                return
            }

            val scale = h * 0.78f / sp.height
            val bob = sin(t / 1400.0).toFloat() * h * 0.009f
            val breathe = (1f + 0.006f * sin(t / 2600.0)).toFloat()

            // Псевдо-3D: начало координат в нижней точке персонажа
            matrix.reset()
            matrix.postTranslate(-sp.width / 2f, -sp.height.toFloat())
            matrix.postScale(scale, scale * breathe)
            matrix.postTranslate(charCenterX(), h + bob)
            canvas.drawBitmap(sp, matrix, spritePaint)
        }

        // ---------------- данные ----------------

        private fun reloadCharacterIfNeeded() {
            val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val id = prefs.getString(KEY_CHARACTER, "arona") ?: "arona"
            if (id == characterId && sprite != null) return
            characterId = id
            val character = CharacterRepo.find(applicationContext, id)
                ?: CharacterRepo.all(applicationContext).firstOrNull()
            spriteFile = character?.sprite
            sprite = character?.let {
                MediaFiles.loadBitmap(applicationContext, it.sprite, 1080, 1_600_000L)
            }
        }

        private fun loadBackdropAsync() {
            if (loadingBackdrop || w == 0 || h == 0) return
            loadingBackdrop = true
            val targetW = (w / 3).coerceAtLeast(64)
            val targetH = (h / 3).coerceAtLeast(64)
            io.execute {
                val src: Bitmap? = readWallpaperBitmap()
                val result: Bitmap? = src?.let {
                    try {
                        ImageFx.blurredCover(it, targetW, targetH, radius = 16)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Блюр не удался: $t")
                        null
                    }
                }
                main.post { backdrop = result; loadingBackdrop = false }
            }
        }

        /** Текущие обои: сначала пробуем файл напрямую, потом обёртку Drawable.
         *  До Android 12 Drawable-путь может требовать разрешения — поэтому три попытки. */
        private fun readWallpaperBitmap(): Bitmap? {
            val manager = WallpaperManager.getInstance(applicationContext)
            // 1) Прямой файл обоев — самый надёжный путь
            try {
                manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
                    val bmp = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                    if (bmp != null) return bmp
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Файл обоев недоступен: $t")
            }
            // 2) Drawable текущих обоев
            for (d in listOfNotNull(
                runCatching { manager.drawable }.getOrNull(),
                runCatching { manager.peekDrawable() }.getOrNull()
            )) {
                val bmp = (d as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bmp != null && !bmp.isRecycled) return bmp
            }
            Log.w(TAG, "Обои прочитать не удалось — показываю запасной фон")
            return null
        }

        private fun registerReceiverSafe() {
            try {
                val filter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
                if (Build.VERSION.SDK_INT >= 33) {
                    applicationContext.registerReceiver(wallpaperReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    applicationContext.registerReceiver(wallpaperReceiver, filter)
                }
            } catch (e: Exception) {
                Log.w(TAG, "receiver: $e")
            }
        }

        private fun unregisterReceiverSafe() {
            try {
                applicationContext.unregisterReceiver(wallpaperReceiver)
            } catch (_: Exception) {
            }
        }

        private fun screenW(): Float = if (pages >= 2f) w / pages else w.toFloat()
        private fun visibleLeft(): Float = xOffset * (w - screenW())
        private fun charCenterX(): Float = visibleLeft() + screenW() * 0.5f
    }

    companion object {
        private const val TAG = "AssistantWallpaper"
        private const val PREFS = "assistant"
        private const val KEY_CHARACTER = "character_id"
        private const val FRAME_MIN_MS = 28L
    }
}

/** Размытие и тонировка фона. */
object ImageFx {

    /** Cover-масштаб + размытие + тёмно-синий тон: фон в стиле интерфейса SCHALE. */
    fun blurredCover(src: Bitmap, targetW: Int, targetH: Int, radius: Int): Bitmap {
        val s = max(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val scaled = Bitmap.createScaledBitmap(
            src,
            max(1, (src.width * s).toInt()),
            max(1, (src.height * s).toInt()),
            true
        )
        var out = boxBlur(scaled, radius, passes = 3)
        val canvas = Canvas(out)
        canvas.drawColor(0x4D101C2E.toInt()) // лёгкий тёмно-синий тон — размытие остаётся видно
        // плавное затемнение к низу
        canvas.drawRect(
            0f, 0f, out.width.toFloat(), out.height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, out.height.toFloat(),
                    0x000B1220, 0x8C0B1220.toInt(), Shader.TileMode.CLAMP
                )
            }
        )
        // Насыщенность применяем ОДИН раз здесь, на фоновом потоке: без фильтра
        // в каждом кадре рисование обоев становится лёгким (раньше это душило FPS)
        val saturated = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
        Canvas(saturated).drawBitmap(
            out, 0f, 0f,
            Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.45f) })
            }
        )
        return saturated
    }
    private fun boxBlur(source: Bitmap, radius: Int, passes: Int): Bitmap {
        val r = radius.coerceIn(1, 25)
        val w = source.width
        val h = source.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        source.getPixels(a, 0, w, 0, 0, w, h)
        var from = a
        var to = b
        repeat(passes) {
            boxPass(from, to, w, h, r, horizontal = true)
            boxPass(to, from, w, h, r, horizontal = false)
        }
        return Bitmap.createBitmap(from, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Размытие скользящим окном: O(n) на проход. */
    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val other = if (horizontal) h else w
        val step = if (horizontal) 1 else w
        val outer = if (horizontal) w else 1
        val div = r + r + 1
        for (o in 0 until other) {
            val base = o * outer
            var sa = 0L; var sr = 0L; var sg = 0L; var sb = 0L
            for (k in -r..r) {
                val p = src[base + k.coerceIn(0, len - 1) * step]
                sa += (p ushr 24) and 0xFF
                sr += (p ushr 16) and 0xFF
                sg += (p ushr 8) and 0xFF
                sb += p and 0xFF
            }
            for (i in 0 until len) {
                dst[base + i * step] = (((sa / div) shl 24)
                    .or(((sr / div) shl 16))
                    .or(((sg / div) shl 8))
                    .or(sb / div)).toInt()
                val addP = src[base + min(i + r + 1, len - 1) * step]
                val subP = src[base + max(i - r, 0) * step]
                sa += ((addP ushr 24) and 0xFF) - ((subP ushr 24) and 0xFF)
                sr += ((addP ushr 16) and 0xFF) - ((subP ushr 16) and 0xFF)
                sg += ((addP ushr 8) and 0xFF) - ((subP ushr 8) and 0xFF)
                sb += (addP and 0xFF) - (subP and 0xFF)
            }
        }
    }
}
