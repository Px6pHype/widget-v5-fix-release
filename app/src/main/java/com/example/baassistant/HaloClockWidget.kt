package com.example.baassistant

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Часы с нимбом — как в BlueOS: мягкий нимб над цифрами, дата и приветствие
 * «Добрый вечер, Сэнсэй». Время и дата обновляются сами (TextClock),
 * приветствие пересчитывается раз в полчаса. Тап по часам — настройки ассистента.
 */
class HaloClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, manager, it) }
    }

    companion object {
        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setTextViewText(R.id.greeting, greeting(context))
            views.setImageViewBitmap(R.id.halo, halo(context))
            views.setOnClickPendingIntent(
                R.id.root,
                PendingIntent.getActivity(
                    context, widgetId,
                    Intent(context, WallpaperSettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            manager.updateAppWidget(widgetId, views)
        }

        /** Приветствие по времени суток — как экран «Добрый вечер, Сэнсэй» в игре. */
        private fun greeting(context: Context): String {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val res = when (hour) {
                in 5..11 -> R.string.greeting_morning
                in 12..17 -> R.string.greeting_day
                in 18..22 -> R.string.greeting_evening
                else -> R.string.greeting_night
            }
            return context.getString(res)
        }

        /** Нимб: мягкое лавандовое кольцо. Рисуется один раз и кэшируется. */
        private var haloCache: Bitmap? = null

        private fun halo(context: Context): Bitmap {
            haloCache?.let { return it }
            val d = context.resources.displayMetrics.density
            val w = (76 * d).toInt().coerceAtLeast(40)
            val h = (22 * d).toInt().coerceAtLeast(14)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = 0xD9C7C0E8.toInt()
                strokeWidth = 3.2f * d
                maskFilter = BlurMaskFilter(2.2f * d, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawOval(4f * d, 4f * d, w - 4f * d, h - 4f * d, p)
            haloCache = bmp
            return bmp
        }
    }
}
