package com.example.baassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.InputStream
import kotlin.math.min
import kotlin.math.sqrt
import org.json.JSONArray

/** РћРґРЅР° СЂРµРїР»РёРєР°: С‚РµРєСЃС‚ РїРѕРґРїРёСЃРё Рё С„Р°Р№Р» РѕР·РІСѓС‡РєРё (РјРѕР¶РµС‚ РѕС‚СЃСѓС‚СЃС‚РІРѕРІР°С‚СЊ). */
data class Line(val text: String, val voice: String?)

/** РџРµСЂСЃРѕРЅР°Р¶. */
data class CharacterInfo(
    val id: String,
    val name: String,
    val sprite: String,     // РёРјСЏ С„Р°Р№Р»Р° РєР°СЂС‚РёРЅРєРё
    val lines: List<Line>
)

object CharacterRepo {
    private const val TAG = "CharacterRepo"
    private var cache: List<CharacterInfo>? = null

    fun all(context: Context): List<CharacterInfo> =
        cache ?: load(context).also { cache = it }

    fun find(context: Context, id: String?): CharacterInfo? =
        all(context).firstOrNull { it.id == id }

    private fun load(context: Context): List<CharacterInfo> {
        val text = readJson(context) ?: return emptyList()
        return try {
            val array = JSONArray(text)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                val phrases = o.getJSONArray("lines")
                CharacterInfo(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    sprite = o.getString("sprite"),
                    lines = List(phrases.length()) { j ->
                        val p = phrases.getJSONObject(j)
                        Line(
                            text = p.getString("text"),
                            voice = if (p.has("voice") && !p.isNull("voice")) p.getString("voice") else null
                        )
                    }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "characters.json СЃР»РѕРјР°РЅ: $e")
            emptyList()
        }
    }

    /** РЎРїРµСЂРІР° characters.json СЂСЏРґРѕРј СЃ С„Р°Р№Р»Р°РјРё РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ, РїРѕС‚РѕРј assets РІРЅСѓС‚СЂРё APK. */
    private fun readJson(context: Context): String? {
        context.getExternalFilesDir(null)?.let { root ->
            val external = File(root, "characters.json")
            if (external.isFile) {
                try {
                    return external.readText()
                } catch (e: Exception) {
                    Log.w(TAG, "РќРµ СѓРґР°Р»РѕСЃСЊ РїСЂРѕС‡РёС‚Р°С‚СЊ ${external.path}: $e")
                }
            }
        }
        return try {
            context.assets.open("characters.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "РќРµС‚ characters.json РІ assets: $e")
            null
        }
    }
}

/** РљР°РєРѕР№ РїРµСЂСЃРѕРЅР°Р¶ СЃС‚РѕРёС‚ РІ РєР°РєРѕРј РІРёРґР¶РµС‚Рµ: widgetId -> characterId. */
object WidgetPrefs {
    private const val FILE = "widgets"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(context: Context, widgetId: Int, characterId: String) {
        prefs(context).edit().putString(widgetId.toString(), characterId).apply()
    }

    fun load(context: Context, widgetId: Int): String? =
        prefs(context).getString(widgetId.toString(), null)

    fun remove(context: Context, widgetId: Int) {
        prefs(context).edit().remove(widgetId.toString()).apply()
    }
}

/**
 * РџРѕРёСЃРє РєР°СЂС‚РёРЅРѕРє Рё Р·РІСѓРєР°.
 *
 * РџРѕСЂСЏРґРѕРє: СЃРїРµСЂРІР° РїР°РїРєР° РїСЂРёР»РѕР¶РµРЅРёСЏ РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ (РµС‘ РјРѕР¶РЅРѕ РїРѕРїРѕР»РЅСЏС‚СЊ Р±РµР· РїРµСЂРµСЃР±РѕСЂРєРё APK),
 * Р·Р°С‚РµРј assets РІРЅСѓС‚СЂРё APK. Р РµРіРёСЃС‚СЂ Р±СѓРєРІ Рё СЂР°СЃС€РёСЂРµРЅРёРµ РЅРµ СЃС‚СЂРѕРіРѕ: aris.png РЅР°Р№РґС‘С‚СЃСЏ
 * Рё РєР°Рє aris.PNG, Рё РєР°Рє Aris.jpg, Рё РєР°Рє aris.webp.
 *
 * РџР°РїРєР° РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ: Android/data/com.example.baassistant/files/
 *   в”њв”Ђв”Ђ characters.json   (РЅРµРѕР±СЏР·Р°С‚РµР»СЊРЅРѕ: СЃРІРѕСЏ РІРµСЂСЃРёСЏ СЃРїРёСЃРєР° РїРµСЂСЃРѕРЅР°Р¶РµР№)
 *   в”њв”Ђв”Ђ sprites/          (РєР°СЂС‚РёРЅРєРё PNG СЃ РїСЂРѕР·СЂР°С‡РЅС‹Рј С„РѕРЅРѕРј)
 *   в””в”Ђв”Ђ voices/           (РѕР·РІСѓС‡РєР° mp3/ogg)
 */
object MediaFiles {
    private const val TAG = "MediaFiles"
    private val IMAGE_EXTS = listOf("png", "webp", "jpg", "jpeg")
    private val AUDIO_EXTS = listOf("mp3", "ogg", "wav", "m4a", "flac")

    /** РџР°РїРєР° РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ РґР»СЏ РєР°СЂС‚РёРЅРѕРє/Р·РІСѓРєР°/СЃРІРѕРµРіРѕ characters.json. */
    fun userDir(context: Context, kind: String): File? =
        context.getExternalFilesDir(null)?.let { root ->
            if (kind.isEmpty()) root else File(root, kind).apply { mkdirs() }
        }

    fun openSprite(context: Context, name: String): InputStream? =
        open(context, "sprites", name, IMAGE_EXTS)

    fun openVoice(context: Context, name: String): InputStream? =
        open(context, "voices", name, AUDIO_EXTS)

    /**
     * Р”РµРєРѕРґРёСЂСѓРµС‚ РєР°СЂС‚РёРЅРєСѓ СЃ РѕРіСЂР°РЅРёС‡РµРЅРёСЏРјРё: РїРѕ Р±РѕР»СЊС€РµР№ СЃС‚РѕСЂРѕРЅРµ Рё РїРѕ РїР»РѕС‰Р°РґРё РІ РїРёРєСЃРµР»СЏС…
     * (Р±СЋРґР¶РµС‚ РЅСѓР¶РµРЅ, РїРѕС‚РѕРјСѓ С‡С‚Рѕ RemoteViews РїРµСЂРµРґР°СЋС‚СЃСЏ С‡РµСЂРµР· Binder вЂ” С‚Р°Рј Р»РёРјРёС‚ ~1 РњР‘).
     */
    fun loadBitmap(context: Context, name: String, maxSide: Int, maxAreaPx: Long = Long.MAX_VALUE): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = openSprite(context, name) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) } // С‡РёС‚Р°РµС‚ С‚РѕР»СЊРєРѕ СЂР°Р·РјРµСЂС‹
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "РљР°СЂС‚РёРЅРєР° $name РЅРµ С‡РёС‚Р°РµС‚СЃСЏ")
            return null
        }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }

        val bitmap = openSprite(context, name)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        return fit(bitmap, maxSide, maxAreaPx)
    }

    private fun fit(bitmap: Bitmap, maxSide: Int, maxAreaPx: Long): Bitmap {
        val sideScale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        val area = bitmap.width.toLong() * bitmap.height
        val areaScale = if (area > maxAreaPx) sqrt(maxAreaPx.toDouble() / area).toFloat() else 1f
        val scale = min(sideScale, areaScale)
        if (scale >= 1f) return bitmap
        return Bitmap.createScaledBitmap(
            bitmap,
            maxOf(1, (bitmap.width * scale).toInt()),
            maxOf(1, (bitmap.height * scale).toInt()),
            true
        )
    }

    private fun open(context: Context, kind: String, name: String, exts: List<String>): InputStream? {
        val wanted = name.trim().trimStart('/').replace('\\', '/')
        val stem = wanted.substringBeforeLast('.', wanted)

        userDir(context, kind)?.let { dir ->
            findOnDisk(dir, wanted, stem, exts)?.let { return it.inputStream() }
        }
        findInAssets(context, kind, wanted, stem, exts)?.let { return it }

        Log.w(TAG, "Р¤Р°Р№Р» '$name' РЅРµ РЅР°Р№РґРµРЅ РЅРё РІ ${userDir(context, kind)?.path}, РЅРё РІ assets/$kind")
        return null
    }

    private fun findOnDisk(dir: File, wanted: String, stem: String, exts: List<String>): File? {
        File(dir, wanted).takeIf { it.isFile }?.let { return it }
        exts.firstNotNullOfOrNull { ext -> File(dir, "$stem.$ext").takeIf(File::isFile) }?.let { return it }
        val files = dir.listFiles()?.filter { it.isFile } ?: return null
        files.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.let { return it }
        return files.firstOrNull { it.nameWithoutExtension.equals(stem, ignoreCase = true) }
    }

    private fun findInAssets(
        context: Context, kind: String, wanted: String, stem: String, exts: List<String>
    ): InputStream? {
        val assets = context.assets
        return try {
            try {
                return assets.open("$kind/$wanted")
            } catch (_: Exception) {
            }
            for (ext in exts) {
                try {
                    return assets.open("$kind/$stem.$ext")
                } catch (_: Exception) {
                }
            }
            val names = assets.list(kind) ?: return null
            names.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return assets.open("$kind/$it") }
            names.firstOrNull { it.substringBeforeLast('.', it).equals(stem, ignoreCase = true) }
                ?.let { return assets.open("$kind/$it") }
            null
        } catch (e: Exception) {
            Log.w(TAG, "assets/$kind: $e")
            null
        }
    }
}
