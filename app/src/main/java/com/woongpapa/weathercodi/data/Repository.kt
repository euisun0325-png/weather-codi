package com.woongpapa.weathercodi.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 모든 데이터는 휴대폰 안(앱 전용 폴더)에만 저장합니다.
 * - 옷장: files/wardrobe.json
 * - 사진: files/photos/ 폴더 (jpg)
 * - 설정: SharedPreferences
 */
class Repository(context: Context) {
    private val ctx = context.applicationContext
    private val wardrobeFile = File(ctx.filesDir, "wardrobe.json")
    private val photoDir = File(ctx.filesDir, "photos").apply { mkdirs() }
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---------- 옷장 ----------
    @Synchronized
    fun loadWardrobe(): List<ClothingItem> {
        if (!wardrobeFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(wardrobeFile.readText())
            (0 until arr.length()).map { ClothingItem.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveWardrobe(items: List<ClothingItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        wardrobeFile.writeText(arr.toString())
    }

    fun upsert(item: ClothingItem) {
        val list = loadWardrobe().toMutableList()
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(item)
        saveWardrobe(list)
    }

    fun delete(item: ClothingItem) {
        saveWardrobe(loadWardrobe().filterNot { it.id == item.id })
        item.photoPath?.let { File(it).delete() }
    }

    /** 지금까지 입력한 보관 위치 목록 (자주 쓴 순) */
    fun knownLocations(): List<String> =
        loadWardrobe().map { it.location.trim() }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    fun markWorn(ids: List<String>, date: String) {
        // 입기로 했으면 이미 꺼낸 옷이므로 '보관 중' 표시 해제
        saveWardrobe(loadWardrobe().map { if (it.id in ids) it.copy(lastWorn = date, stored = false) else it })
    }

    // ---------- 사진 ----------
    /** 갤러리/카메라 사진을 앱 폴더로 복사하면서 긴 변 1024px로 줄여 저장 */
    fun importPhoto(uri: Uri): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (src == null) null else {
            val scaled = rotateByExif(uri, scaleDown(src, 1024))
            val out = File(photoDir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            out.absolutePath
        }
    } catch (e: Exception) {
        null
    }

    /** 카메라 사진이 옆으로 누워 저장되는 문제(EXIF 회전) 보정 */
    private fun rotateByExif(uri: Uri, b: Bitmap): Bitmap {
        val orientation = try {
            ctx.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return b
        }
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    private fun scaleDown(b: Bitmap, maxSide: Int): Bitmap {
        val longSide = maxOf(b.width, b.height)
        if (longSide <= maxSide) return b
        val ratio = maxSide.toFloat() / longSide
        return Bitmap.createScaledBitmap(b, (b.width * ratio).toInt(), (b.height * ratio).toInt(), true)
    }

    // ---------- 설정 ----------
    var kmaKey: String
        get() = prefs.getString("kmaKey", "") ?: ""
        set(v) = prefs.edit().putString("kmaKey", v.trim()).apply()

    var geminiKey: String
        get() = prefs.getString("geminiKey", "") ?: ""
        set(v) = prefs.edit().putString("geminiKey", v.trim()).apply()

    /** 기본 위치: 서울 시청 */
    var lat: Double
        get() = prefs.getFloat("lat", 37.5665f).toDouble()
        set(v) = prefs.edit().putFloat("lat", v.toFloat()).apply()

    var lon: Double
        get() = prefs.getFloat("lon", 126.9780f).toDouble()
        set(v) = prefs.edit().putFloat("lon", v.toFloat()).apply()

    var locationName: String
        get() = prefs.getString("locationName", "서울(기본)") ?: "서울(기본)"
        set(v) = prefs.edit().putString("locationName", v).apply()

    /** 전날 알림 시각 (0~23시) */
    var notifyHour: Int
        get() = prefs.getInt("notifyHour", 21)
        set(v) = prefs.edit().putInt("notifyHour", v.coerceIn(0, 23)).apply()

    var bodyPhotoPath: String?
        get() = prefs.getString("bodyPhotoPath", null)
        set(v) = prefs.edit().putString("bodyPhotoPath", v).apply()

    var lastRecommendation: Recommendation?
        get() = prefs.getString("lastRec", null)?.let {
            try { Recommendation.fromJson(JSONObject(it)) } catch (e: Exception) { null }
        }
        set(v) = prefs.edit().putString("lastRec", v?.toJson()?.toString()).apply()
}
