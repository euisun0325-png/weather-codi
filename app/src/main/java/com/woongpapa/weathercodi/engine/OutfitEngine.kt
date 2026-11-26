package com.woongpapa.weathercodi.engine

import com.woongpapa.weathercodi.data.Category
import com.woongpapa.weathercodi.data.ClothingItem
import com.woongpapa.weathercodi.data.DayForecast
import com.woongpapa.weathercodi.data.Recommendation
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 규칙 기반 코디 엔진 (AI 키가 없어도 동작).
 * 기온별 옷차림 기준 + 일교차·비·바람·최근 착용 기록을 점수로 계산합니다.
 */
object OutfitEngine {

    data class Target(val top: Int, val bottom: Int, val outer: Int, val label: String)

    /** 체감 기준 기온 → 필요한 두께(1~5). outer 0 = 아우터 불필요 */
    fun target(f: DayForecast): Pair<Target, Double> {
        var t = (f.minTemp + f.maxTemp) / 2.0
        if (f.maxWind >= 4.0) t -= 2.0      // 바람 강하면 더 춥게
        if (f.rain || f.snow) t -= 1.0
        val tg = when {
            t >= 28 -> Target(1, 1, 0, "한여름 날씨")
            t >= 23 -> Target(1, 2, 0, "더운 날씨")
            t >= 20 -> Target(2, 2, 0, "선선한 날씨")
            t >= 17 -> Target(2, 2, 1, "가벼운 겉옷이 필요한 날씨")
            t >= 12 -> Target(3, 3, 2, "쌀쌀한 날씨")
            t >= 9 -> Target(3, 3, 3, "꽤 쌀쌀한 날씨")
            t >= 5 -> Target(4, 3, 4, "추운 날씨")
            else -> Target(4, 4, 5, "한겨울 날씨")
        }
        // 일교차가 크면 얇은 겉옷이라도 추가
        val gap = f.maxTemp - f.minTemp
        val adjusted = if (tg.outer == 0 && gap >= 10 && t < 25) tg.copy(outer = 1) else tg
        return adjusted to t
    }

    fun recommend(wardrobe: List<ClothingItem>, f: DayForecast, random: Random = Random.Default): Recommendation {
        val (tg, feels) = target(f)
        val day = LocalDate.parse(f.date)
        val missing = mutableListOf<String>()

        fun base(item: ClothingItem, want: Int): Double {
            var s = -abs(item.warmth - want) * 10.0
            s -= recentPenalty(item, day)
            s += random.nextDouble(0.0, 3.0) // 매번 같은 조합만 나오지 않게
            return s
        }

        val top = wardrobe.filter { it.category == Category.TOP }
            .maxByOrNull { base(it, tg.top) }
        if (top == null) { missing += "상의" }

        val bottom = wardrobe.filter { it.category == Category.BOTTOM }
            .maxByOrNull { base(it, tg.bottom) + matchScore(it, top) }
        if (bottom == null) { missing += "하의" }

        val outer = if (tg.outer == 0) null else {
            val o = wardrobe.filter { it.category == Category.OUTER }
                .maxByOrNull {
                    base(it, tg.outer) + matchScore(it, top) +
                        (if ((f.rain || f.snow) && it.waterproof) 6.0 else 0.0)
                }
            if (o == null) {
                missing += warmthName(tg.outer) + " 아우터"
            } else if (abs(o.warmth - tg.outer) >= 2) {
                missing += warmthName(tg.outer) + " 아우터(지금 옷장엔 두께가 맞는 게 없어요)"
            }
            o
        }

        val shoes = wardrobe.filter { it.category == Category.SHOES }
            .maxByOrNull {
                var s = -recentPenalty(it, day) * 0.5 + matchScore(it, top) + random.nextDouble(0.0, 3.0)
                if (f.rain || f.snow) s += if (it.waterproof) 15.0 else -10.0
                // 추울수록 두꺼운 신발(부츠 등) 선호
                s -= abs(it.warmth - (if (feels < 5) 4 else if (feels < 15) 3 else 2)) * 3.0
                s
            }
        if (shoes == null) { missing += "신발" }

        val tips = mutableListOf<String>()
        if (f.rainChance >= 60 || f.rain) tips += "☔ 강수확률 ${f.rainChance}% — 우산 챙기세요"
        if (f.snow) tips += "❄️ 눈 소식 — 미끄럽지 않은 신발이 좋아요"
        if (f.maxTemp - f.minTemp >= 10) tips += "🌡️ 일교차 ${(f.maxTemp - f.minTemp).roundToInt()}도 — 벗고 입기 쉬운 레이어드 추천"
        if (f.maxWind >= 6) tips += "💨 바람이 강해요(최대 ${f.maxWind.roundToInt()}m/s)"
        if (feels < 0) tips += "🧣 목도리·장갑도 챙기세요"

        val parts = listOfNotNull(top, bottom, outer, shoes).joinToString(" + ") { it.displayName() }
        val reason = "내일은 ${f.summary}, ${f.minTemp.roundToInt()}~${f.maxTemp.roundToInt()}℃로 ${tg.label}예요. " +
            if (parts.isNotBlank()) "$parts 조합을 추천해요." else "옷장에 옷을 먼저 등록해 주세요."

        return Recommendation(
            forecast = f, topId = top?.id, bottomId = bottom?.id, outerId = outer?.id, shoesId = shoes?.id,
            reason = reason, tips = tips, missing = missing, byAi = false,
        )
    }

    private fun recentPenalty(item: ClothingItem, day: LocalDate): Double {
        val worn = item.lastWorn?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return 0.0
        val days = ChronoUnit.DAYS.between(worn, day)
        return when {
            days <= 1 -> 12.0
            days <= 3 -> 6.0
            days <= 6 -> 2.0
            else -> 0.0
        }
    }

    private val NEUTRALS = listOf("블랙", "검정", "화이트", "흰", "아이보리", "그레이", "회색", "네이비", "남색", "베이지", "데님", "청", "카키", "브라운", "갈색")

    private fun isNeutral(color: String) = color.isBlank() || NEUTRALS.any { color.contains(it) }

    /** 격식·색 조화 점수 */
    private fun matchScore(item: ClothingItem, top: ClothingItem?): Double {
        if (top == null) return 0.0
        var s = -abs(item.formality - top.formality) * 4.0
        if (!isNeutral(item.color) && !isNeutral(top.color) && item.color != top.color) s -= 5.0
        return s
    }

    private fun warmthName(w: Int) = when (w) {
        1 -> "얇은"
        2 -> "가벼운"
        3 -> "적당한 두께의"
        4 -> "두꺼운"
        else -> "아주 두꺼운(패딩급)"
    }
}

fun ClothingItem.displayName(): String =
    listOf(color, name).filter { it.isNotBlank() }.joinToString(" ").ifBlank { category.label }
