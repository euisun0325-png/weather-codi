package com.woongpapa.weathercodi.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Category(val label: String) {
    TOP("상의"),
    BOTTOM("하의"),
    OUTER("아우터"),
    SHOES("신발");

    companion object {
        fun from(value: String?): Category =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.label == value } ?: TOP
    }
}

/**
 * 옷장 아이템 하나.
 * warmth: 1(아주 얇음) ~ 5(아주 두꺼움)
 * formality: 1(캐주얼) ~ 3(정장)
 */
data class ClothingItem(
    val id: String = UUID.randomUUID().toString(),
    val category: Category = Category.TOP,
    val name: String = "",
    val color: String = "",
    val warmth: Int = 3,
    val formality: Int = 1,
    val waterproof: Boolean = false,
    val photoPath: String? = null,
    val lastWorn: String? = null, // yyyy-MM-dd
    val location: String = "",    // 보관 위치 (예: 안방 장롱 2칸, 옷방 서랍 3단)
    val stored: Boolean = false,  // 계절 보관 중(박스·깊은 칸) → 추천되면 꺼내라고 알림
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("category", category.name)
        put("name", name)
        put("color", color)
        put("warmth", warmth)
        put("formality", formality)
        put("waterproof", waterproof)
        put("photoPath", photoPath ?: JSONObject.NULL)
        put("lastWorn", lastWorn ?: JSONObject.NULL)
        put("location", location)
        put("stored", stored)
    }

    companion object {
        fun fromJson(o: JSONObject) = ClothingItem(
            id = o.optString("id", UUID.randomUUID().toString()),
            category = Category.from(o.optString("category")),
            name = o.optString("name"),
            color = o.optString("color"),
            warmth = o.optInt("warmth", 3).coerceIn(1, 5),
            formality = o.optInt("formality", 1).coerceIn(1, 3),
            waterproof = o.optBoolean("waterproof", false),
            photoPath = o.optStringOrNull("photoPath"),
            lastWorn = o.optStringOrNull("lastWorn"),
            location = o.optString("location", ""),
            stored = o.optBoolean("stored", false),
        )
    }
}

/** 내일 하루 예보 요약 */
data class DayForecast(
    val date: String,          // yyyy-MM-dd
    val minTemp: Double,
    val maxTemp: Double,
    val rainChance: Int,       // 최대 강수확률 %
    val rain: Boolean,
    val snow: Boolean,
    val maxWind: Double,       // m/s
    val summary: String,       // 맑음, 흐림, 비 ...
    val source: String,        // 기상청 / Open-Meteo
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("date", date); put("minTemp", minTemp); put("maxTemp", maxTemp)
        put("rainChance", rainChance); put("rain", rain); put("snow", snow)
        put("maxWind", maxWind); put("summary", summary); put("source", source)
    }

    companion object {
        fun fromJson(o: JSONObject) = DayForecast(
            date = o.optString("date"),
            minTemp = o.optDouble("minTemp"),
            maxTemp = o.optDouble("maxTemp"),
            rainChance = o.optInt("rainChance"),
            rain = o.optBoolean("rain"),
            snow = o.optBoolean("snow"),
            maxWind = o.optDouble("maxWind"),
            summary = o.optString("summary"),
            source = o.optString("source"),
        )
    }
}

data class Recommendation(
    val forecast: DayForecast,
    val topId: String?,
    val bottomId: String?,
    val outerId: String?,
    val shoesId: String?,
    val reason: String,
    val tips: List<String>,
    val missing: List<String>,
    val byAi: Boolean,
) {
    fun ids(): List<String> = listOfNotNull(topId, bottomId, outerId, shoesId)

    fun toJson(): JSONObject = JSONObject().apply {
        put("forecast", forecast.toJson())
        put("topId", topId ?: JSONObject.NULL)
        put("bottomId", bottomId ?: JSONObject.NULL)
        put("outerId", outerId ?: JSONObject.NULL)
        put("shoesId", shoesId ?: JSONObject.NULL)
        put("reason", reason)
        put("tips", JSONArray(tips))
        put("missing", JSONArray(missing))
        put("byAi", byAi)
    }

    companion object {
        fun fromJson(o: JSONObject) = Recommendation(
            forecast = DayForecast.fromJson(o.getJSONObject("forecast")),
            topId = o.optStringOrNull("topId"),
            bottomId = o.optStringOrNull("bottomId"),
            outerId = o.optStringOrNull("outerId"),
            shoesId = o.optStringOrNull("shoesId"),
            reason = o.optString("reason"),
            tips = o.optJSONArray("tips").toStringList(),
            missing = o.optJSONArray("missing").toStringList(),
            byAi = o.optBoolean("byAi"),
        )
    }
}

fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }
}
