package com.example.baassistant

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/** Настройки обоев: выбираем, кто живёт на рабочем столе — Арона или Плана. */
class WallpaperSettingsActivity : Activity() {

    private var selected = "arona"
    private val density by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpaper_settings)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        selected = prefs.getString(KEY_CHARACTER, "arona") ?: "arona"

        val cards = linkedMapOf("arona" to R.id.pick_arona, "plana" to R.id.pick_plana)
        for ((id, viewId) in cards) {
            val card = findViewById<View>(viewId)
            val nameView = card.findViewById<TextView>(R.id.pick_name)
            val character = CharacterRepo.find(this, id)
            nameView.text = character?.name ?: id
            val image = card.findViewById<ImageView>(R.id.pick_image)
            val bitmap = character?.let { MediaFiles.loadBitmap(this, it.sprite, 256, 256L * 256) }
            if (bitmap != null) {
                image.setImageBitmap(bitmap)
            } else {
                image.setImageResource(R.drawable.ic_character_slot)
            }
            card.setOnClickListener {
                selected = id
                prefs.edit().putString(KEY_CHARACTER, id).apply()
                refresh(cards)
                Toast.makeText(this, getString(R.string.settings_selected, nameView.text), Toast.LENGTH_SHORT).show()
            }
        }
        refresh(cards)

        findViewById<View>(R.id.install).setOnClickListener { installWallpaper() }
    }

    private fun refresh(cards: Map<String, Int>) {
        for ((id, viewId) in cards) {
            findViewById<View>(viewId).background = cardBackground(id == selected)
        }
    }

    /** Подсветка выбранной карточки. */
    private fun cardBackground(active: Boolean) = GradientDrawable().apply {
        cornerRadius = 16f * density
        setColor(if (active) 0x1F3AA6FF else 0x1410182C)
        setStroke(
            ((if (active) 3f else 1f) * density).toInt().coerceAtLeast(1),
            if (active) 0xFF3AA6FF.toInt() else 0x338899AA
        )
    }

    private fun installWallpaper() {
        // До Android 12 (включительно) чтение обоев для блюра требует разрешения —
        // спрашиваем здесь, после ответа откроется диалог установки
        if (Build.VERSION.SDK_INT <= 32 && checkSelfPermission(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), REQ_READ_WALLPAPER)
            return
        }
        try {
            // Быстрый путь: системный диалог «Установить обои?» прямо для нашего сервиса
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this, AssistantWallpaperService::class.java)
                )
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: Exception) {
                Toast.makeText(this, R.string.settings_open_picker_fallback, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_READ_WALLPAPER &&
            grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            installWallpaper() // разрешение дали — продолжаем установку обоев
        }
    }

    private companion object {
        const val PREFS = "assistant"
        const val KEY_CHARACTER = "character_id"
        const val REQ_READ_WALLPAPER = 1
    }
}
