package com.mp01home.launcher.eink

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log

/**
 * UX-5 generic implementation: draws [SleepLine]s black-on-white into a screen-sized bitmap and
 * sets it as the LOCK-screen wallpaper (WallpaperManager.FLAG_LOCK). On e-ink devices whose sleep
 * image is the lock screen this is what stays visible with the panel unpowered. No vendor API is
 * needed (the MP01 has none, decision D3). Requires android.permission.SET_WALLPAPER.
 */
object SleepScreenRenderer {

    private const val TAG = "SleepScreen"

    fun render(context: Context, lines: List<SleepLine>, footer: String): Bitmap {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(320)
        val height = metrics.heightPixels.coerceAtLeast(480)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val margin = width / 14f
        val maxTextWidth = width - 2 * margin
        val body = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.MONOSPACE
            textSize = width / 20f
        }
        val heading = TextPaint(body).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = width / 16f
        }
        val footerPaint = TextPaint(body).apply { textSize = width / 26f }

        // Start below the lock-screen clock area.
        var y = height * 0.22f
        val bottomLimit = height - margin - footerPaint.textSize * 2
        for (line in lines) {
            val paint = if (line.heading) heading else body
            val lineHeight = paint.fontSpacing
            if (y + lineHeight > bottomLimit) break
            y += lineHeight
            if (line.text.isNotEmpty()) {
                val text = TextUtils.ellipsize(line.text, paint, maxTextWidth, TextUtils.TruncateAt.END)
                canvas.drawText(text, 0, text.length, margin, y, paint)
            } else {
                y -= lineHeight / 2 // blank separator lines are half height
            }
        }
        canvas.drawText(footer, margin, height - margin, footerPaint)
        return bitmap
    }

    /** @return true if the lock-screen image was set. */
    fun setLockScreen(context: Context, bitmap: Bitmap): Boolean = runCatching {
        val wm = WallpaperManager.getInstance(context)
        wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK) != 0
    }.getOrElse { e ->
        Log.w(TAG, "cannot set lock screen image", e)
        false
    }

    /** Removes our lock-screen image (falls back to the system wallpaper). */
    fun clearLockScreen(context: Context): Boolean = runCatching {
        WallpaperManager.getInstance(context).clear(WallpaperManager.FLAG_LOCK)
        true
    }.getOrElse { e ->
        Log.w(TAG, "cannot clear lock screen image", e)
        false
    }
}
