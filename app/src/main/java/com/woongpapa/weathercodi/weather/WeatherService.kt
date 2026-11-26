package com.woongpapa.weathercodi.weather

import com.woongpapa.weathercodi.data.DayForecast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

/**
 * 내일 날씨를 가져옵니다.
 * - 기상청 API 키가 있으면: 기상청 단기예보(공공데이터포털)
 * - 키가 없거나 실패하면: Open-Meteo (키 없이 무료) 로 대체
 */
object WeatherService {
    private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    private val YMD = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun tomorrow(lat: Double, lon: Double, kmaKey: String): DayForecast {
        if (kmaKey.isNotBlank()) {
            try {
                return fromKma(lat, lon, kmaKey)
            } catch (e: Exception) {
                // 키 오류·점검 등 → Open-Meteo 로 대체
            }
        }
        return fromOpenMeteo(lat, lon)
    }

    // ------------------------- 기상청 -------------------------
    private fun fromKma(lat: Double, lon: Double, key: String): DayForecast {
        val (nx, ny) = toGrid(lat, lon)
        val now = LocalDateTime.now(KST)
        val (baseDate, baseTime) = kmaBase(now)
        val tomorrow = now.toLocalDate().plusDays(1)
        val tKey = tomorrow.format(YMD)

        // 포털에서 받은 '일반 인증키(Decoding)'를 넣으면 여기서 인코딩합니다.
        val encodedKey = if (key.contains("%")) key else URLEncoder.encode(key, "UTF-8")
        val url = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst" +
            "?serviceKey=$encodedKey&pageNo=1&numOfRows=1500&dataType=JSON" +
            "&base_date=$baseDate&base_time=$baseTime&nx=$nx&ny=$ny"

        val root = JSONObject(Http.get(url))
        val header = root.getJSONObject("response").getJSONObject("header")
        if (header.optString("resultCode") != "00") error("KMA ${header.optString("resultMsg")}")
        val items = root.getJSONObject("response").getJSONObject("body")
            .getJSONObject("items").getJSONArray("item")

        val temps = mutableListOf<Double>()
        var tmn: Double? = null
        var tmx: Double? = null
        var pop = 0
        var wind = 0.0
        var rain = false
        var snow = false
        val skyCount = IntArray(5)

        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            if (o.optString("fcstDate") != tKey) continue
            val hour = o.optString("fcstTime").take(2).toIntOrNull() ?: 0
            val v = o.optString("fcstValue")
            when (o.optString("category")) {
                "TMP" -> v.toDoubleOrNull()?.let { t -> temps.add(t) }
                "TMN" -> { tmn = v.toDoubleOrNull() }
                "TMX" -> { tmx = v.toDoubleOrNull() }
                "POP" -> { if (hour in 6..22) pop = max(pop, v.toIntOrNull() ?: 0) }
                "WSD" -> { if (hour in 6..22) wind = max(wind, v.toDoubleOrNull() ?: 0.0) }
                "PTY" -> {
                    if (hour in 6..22) {
                        val pty = v.toIntOrNull() ?: 0
                        if (pty == 1 || pty == 2 || pty == 4) rain = true
                        if (pty == 2 || pty == 3) snow = true
                    }
                }
                "SKY" -> {
                    val s = v.toIntOrNull() ?: 0
                    if (hour in 6..21 && s in 1..4) skyCount[s] += 1
                }
            }
        }
        if (temps.isEmpty() && tmn == null) error("KMA no data")

        val minT = tmn ?: temps.min()
        val maxT = tmx ?: temps.max()
        val summary = when {
            snow -> "눈"
            rain -> "비"
            skyCount[4] >= skyCount[1] && skyCount[4] >= skyCount[3] -> "흐림"
            skyCount[3] > skyCount[1] -> "구름많음"
            else -> "맑음"
        }
        return DayForecast(
            date = tomorrow.toString(), minTemp = minT, maxTemp = maxT,
            rainChance = pop, rain = rain || pop >= 60, snow = snow,
            maxWind = wind, summary = summary, source = "기상청",
        )
    }

    /** 단기예보 발표시각(02,05,...,23시, 발표 후 약 10분 뒤 제공) 중 가장 최근 것 */
    private fun kmaBase(now: LocalDateTime): Pair<String, String> {
        val bases = listOf(2, 5, 8, 11, 14, 17, 20, 23)
        val minutes = now.hour * 60 + now.minute
        val h = bases.lastOrNull { it * 60 + 15 <= minutes }
        return if (h == null) {
            now.toLocalDate().minusDays(1).format(YMD) to "2300"
        } else {
            now.toLocalDate().format(YMD) to String.format("%02d00", h)
        }
    }

    /** 위경도 → 기상청 격자(nx, ny) 변환 (기상청 제공 LCC 공식) */
    fun toGrid(lat: Double, lon: Double): Pair<Int, Int> {
        val RE = 6371.00877; val GRID = 5.0
        val SLAT1 = 30.0; val SLAT2 = 60.0; val OLON = 126.0; val OLAT = 38.0
        val XO = 43.0; val YO = 136.0
        val DEGRAD = PI / 180.0
        val re = RE / GRID
        val slat1 = SLAT1 * DEGRAD; val slat2 = SLAT2 * DEGRAD
        val olon = OLON * DEGRAD; val olat = OLAT * DEGRAD

        var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
        sn = ln(cos(slat1) / cos(slat2)) / ln(sn)
        var sf = tan(PI * 0.25 + slat1 * 0.5)
        sf = sf.pow(sn) * cos(slat1) / sn
        var ro = tan(PI * 0.25 + olat * 0.5)
        ro = re * sf / ro.pow(sn)

        var ra = tan(PI * 0.25 + lat * DEGRAD * 0.5)
        ra = re * sf / ra.pow(sn)
        var theta = lon * DEGRAD - olon
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn
        val x = floor(ra * sin(theta) + XO + 0.5).toInt()
        val y = floor(ro - ra * cos(theta) + YO + 0.5).toInt()
        return x to y
    }

    // ------------------------- Open-Meteo -------------------------
    private fun fromOpenMeteo(lat: Double, lon: Double): DayForecast {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max," +
            "precipitation_sum,snowfall_sum,wind_speed_10m_max,weather_code" +
            "&timezone=Asia%2FSeoul&forecast_days=2&wind_speed_unit=ms"
        val d = JSONObject(Http.get(url)).getJSONObject("daily")
        val i = 1 // 0 = 오늘, 1 = 내일
        val code = d.getJSONArray("weather_code").optInt(i)
        val pop = d.getJSONArray("precipitation_probability_max").optInt(i)
        val snowSum = d.getJSONArray("snowfall_sum").optDouble(i, 0.0)
        val precip = d.getJSONArray("precipitation_sum").optDouble(i, 0.0)
        val snow = snowSum > 0.0 || code in 71..77 || code in 85..86
        val rain = !snow && (precip >= 1.0 || code in 51..67 || code in 80..82 || code >= 95)
        val summary = when {
            snow -> "눈"
            code >= 95 -> "뇌우"
            rain -> "비"
            code in 45..48 -> "안개"
            code == 3 -> "흐림"
            code in 1..2 -> "구름많음"
            else -> "맑음"
        }
        return DayForecast(
            date = LocalDate.now(KST).plusDays(1).toString(),
            minTemp = d.getJSONArray("temperature_2m_min").optDouble(i),
            maxTemp = d.getJSONArray("temperature_2m_max").optDouble(i),
            rainChance = pop, rain = rain || pop >= 60, snow = snow,
            maxWind = d.getJSONArray("wind_speed_10m_max").optDouble(i, 0.0),
            summary = summary, source = "Open-Meteo",
        )
    }
}

object Http {
    fun get(url: String): String = request(url, null)

    fun postJson(url: String, body: String): String = request(url, body)

    private fun request(url: String, body: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        try {
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) error("HTTP $code: ${text.take(200)}")
            return text
        } finally {
            conn.disconnect()
        }
    }
}
