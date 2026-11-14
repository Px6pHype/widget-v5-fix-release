package com.example.baassistant

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews

/** РџРѕРґРїРёСЃСЊ РїРѕРґ РїРµСЂСЃРѕРЅР°Р¶РµРј: РёРјСЏ Рё С‚РµРєСЃС‚ СЂРµРїР»РёРєРё. */
data class Dialog(val name: String, val text: String)

/** Виджет с одним персонажем: персонаж просто «дышит», на тапы не реагирует. */
class CharacterWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, manager, it, dialog = null) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.remove(context, it) }
    }

    companion object {
        // RemoteViews РїРµСЂРµРґР°СЋС‚СЃСЏ С‡РµСЂРµР· Binder (Р»РёРјРёС‚ РѕРєРѕР»Рѕ 1 РњР‘), РїРѕСЌС‚РѕРјСѓ РєР°СЂС‚РёРЅРєР°
        // РѕРіСЂР°РЅРёС‡РµРЅР° Рё РїРѕ СЃС‚РѕСЂРѕРЅРµ, Рё РїРѕ РїР»РѕС‰Р°РґРё: ~560 РљР‘ РІ ARGB_8888.
        private const val MAX_SIDE = 330
        private const val MAX_AREA_PX = 85_000L

        /** РџРµСЂРµСЂРёСЃРѕРІР°С‚СЊ РІРёРґР¶РµС‚. dialog = null РїСЂСЏС‡РµС‚ РїРѕРґРїРёСЃСЊ. */
        fun refresh(context: Context, widgetId: Int, dialog: Dialog? = null) {
            render(context, AppWidgetManager.getInstance(context), widgetId, dialog)
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int, dialog: Dialog?) {
            val saved = WidgetPrefs.load(context, widgetId)
            val character = CharacterRepo.find(context, saved)
                ?: CharacterRepo.all(context).firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_character)

            val sprite = character?.let { MediaFiles.loadBitmap(context, it.sprite, MAX_SIDE, MAX_AREA_PX) }
            if (sprite != null) {
                // «Дыхание»: три кадра, ViewFlipper переключает их с кроссфейдом
                views.setImageViewBitmap(R.id.character, sprite)
                views.setImageViewBitmap(R.id.character_b, breatheUpFrame(sprite))
                views.setImageViewBitmap(R.id.character_c, breatheDownFrame(sprite))
                views.setViewVisibility(R.id.flipper, View.VISIBLE)
                views.setViewVisibility(R.id.placeholder, View.GONE)
            } else {
                // РљР°СЂС‚РёРЅРєРё РЅРµС‚: РїРѕРґСЃРєР°Р·С‹РІР°РµРј, РєСѓРґР° Рё РєР°РєРѕР№ С„Р°Р№Р» РїРѕР»РѕР¶РёС‚СЊ
                views.setViewVisibility(R.id.flipper, View.GONE)
                views.setTextViewText(R.id.placeholder, placeholder(context, character?.sprite))
                views.setViewVisibility(R.id.placeholder, View.VISIBLE)
            }

            // Подпись как в BlueOS: имя мелким лавандовым, статус белым
            views.setTextViewText(R.id.name, dialog?.name ?: character?.name ?: "")
            views.setTextViewText(R.id.status, dialog?.text ?: defaultStatus(context, character))

            manager.updateAppWidget(widgetId, views)
        }

        /** Спокойная реплика по умолчанию — статус, пока диалога нет. */
        private fun defaultStatus(context: Context, character: CharacterInfo?): String {
            val lines = character?.lines ?: return ""
            return (lines.lastOrNull { it.voice == null } ?: lines.firstOrNull())?.text ?: ""
        }

        /** Кадр «вдох»: персонаж чуть выше и уже. */
        private fun breatheUpFrame(sprite: Bitmap): Bitmap = Bitmap.createScaledBitmap(
            sprite,
            (sprite.width * 0.99f).toInt().coerceAtLeast(1),
            (sprite.height * 1.035f).toInt().coerceAtLeast(1),
            true
        )

        /** Кадр «выдох»: персонаж чуть ниже и шире. */
        private fun breatheDownFrame(sprite: Bitmap): Bitmap = Bitmap.createScaledBitmap(
            sprite,
            (sprite.width * 1.005f).toInt().coerceAtLeast(1),
            (sprite.height * 0.975f).toInt().coerceAtLeast(1),
            true
        )

        private fun placeholder(context: Context, spriteName: String?): String {
            if (CharacterRepo.all(context).isEmpty()) {
                return "characters.json РїСѓСЃС‚ РёР»Рё СЃР»РѕРјР°РЅ\nРџСЂРѕРІРµСЂСЊ С„Р°Р№Р» РІ assets РёР»Рё РІ\n${MediaFiles.userDir(context, "")?.path ?: "РїР°РїРєРµ РїСЂРёР»РѕР¶РµРЅРёСЏ"}"
            }
            val dir = MediaFiles.userDir(context, "sprites")?.path
            return "РќРµС‚ РєР°СЂС‚РёРЅРєРё: ${spriteName ?: "вЂ”"}\n\nРџРѕР»РѕР¶Рё С„Р°Р№Р» СЃСЋРґР°:\n$dir\nРёР»Рё РІ assets/sprites Рё РїРµСЂРµСЃРѕР±РµСЂРё APK"
        }
    }
}
