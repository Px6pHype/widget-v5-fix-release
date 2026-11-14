package com.example.baassistant

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Р’С‹Р±РѕСЂ РїРµСЂСЃРѕРЅР°Р¶Р°: РѕС‚РєСЂС‹РІР°РµС‚СЃСЏ РїСЂРё РґРѕР±Р°РІР»РµРЅРёРё РІРёРґР¶РµС‚Р° Рё РїСЂРё Р·Р°РїСѓСЃРєРµ РёРєРѕРЅРєРё РїСЂРёР»РѕР¶РµРЅРёСЏ. */
class PickerActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_picker)

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        // Р•СЃР»Рё РІС‹Р№С‚Рё Р±РµР· РІС‹Р±РѕСЂР°, СЃРёСЃС‚РµРјР° РЅРµ РґРѕР±Р°РІРёС‚ РІРёРґР¶РµС‚
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        val characters = CharacterRepo.all(this)
        findViewById<TextView>(R.id.title).setText(
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) R.string.hint_add_widget
            else R.string.pick_character
        )
        findViewById<ListView>(R.id.list).apply {
            adapter = CharacterAdapter(this@PickerActivity, characters)
            setOnItemClickListener { _, _, position, _ -> choose(characters[position]) }
        }
    }

    private fun choose(character: CharacterInfo) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, R.string.no_widget_toast, Toast.LENGTH_SHORT).show()
            return
        }
        WidgetPrefs.save(this, widgetId, character.id)
        CharacterWidget.refresh(this, widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    /** РЎРїРёСЃРѕРє СЃ РјР°Р»РµРЅСЊРєРѕР№ РєР°СЂС‚РёРЅРєРѕР№ РїРµСЂСЃРѕРЅР°Р¶Р° СЃР»РµРІР°. */
    private class CharacterAdapter(
        context: Context,
        private val items: List<CharacterInfo>
    ) : ArrayAdapter<CharacterInfo>(context, R.layout.item_character, items) {

        private val appContext = context.applicationContext

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView
                ?: LayoutInflater.from(appContext).inflate(R.layout.item_character, parent, false)
            val character = items[position]

            view.findViewById<TextView>(R.id.name).text = character.name
            val image = view.findViewById<ImageView>(R.id.sprite)
            val bitmap = MediaFiles.loadBitmap(appContext, character.sprite, THUMB_SIDE, THUMB_AREA)
            if (bitmap != null) {
                image.setImageBitmap(bitmap)
            } else {
                image.setImageResource(R.drawable.ic_character_slot)
            }
            return view
        }
    }

    private companion object {
        const val THUMB_SIDE = 192
        const val THUMB_AREA = 192L * 192
    }
}
