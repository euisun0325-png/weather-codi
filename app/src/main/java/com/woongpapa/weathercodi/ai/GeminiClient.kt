package com.woongpapa.weathercodi.ai

import android.util.Base64
import com.woongpapa.weathercodi.data.Category
import com.woongpapa.weathercodi.data.ClothingItem
import com.woongpapa.weathercodi.data.DayForecast
import com.woongpapa.weathercodi.data.Recommendation
import com.woongpapa.weathercodi.data.optStringOrNull
import com.woongpapa.weathercodi.data.toStringList
import com.woongpapa.weathercodi.weather.Http
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Google Gemini API (aistudio.google.com 에서 무료 키 발급).
 * 1) 옷 사진 → 종류/색/두께/격식 자동 태그
 * 2) 옷장 + 내일 날씨 + 전신사진 → 코디 선택과 이유
 */
class GeminiClient(private val apiKey: String) {
    private val model = "gemini-2.5-flash"
    private val endpoint get() =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    data class Tag(
        val category: Category, val name: String, val color: String,
        val warmth: Int, val formality: Int, val waterproof: Boolean,
    )

    fun tagClothing(photoPath: String): Tag {
        val prompt = """
            사진 속 옷(또는 신발) 하나를 분석해서 아래 JSON 으로만 답하세요.
            {
              "category": "TOP|BOTTOM|OUTER|SHOES 중 하나",
              "name": "짧은 한국어 이름 (예: 니트 스웨터, 슬랙스, 롱패딩, 첼시부츠)",
              "color": "대표 색 한 단어 (예: 블랙, 네이비, 베이지)",
              "warmth": 1~5 정수 (1 반팔·린넨, 2 셔츠·얇은 긴팔, 3 맨투맨·니트·가디건, 4 두꺼운 니트·코트, 5 패딩·기모),
              "formality": 1~3 정수 (1 캐주얼, 2 세미정장, 3 정장),
              "waterproof": true/false (방수 재질이면 true)
            }
        """.trimIndent()
        val o = JSONObject(generate(prompt, listOf(photoPath)))
        return Tag(
            category = Category.from(o.optString("category")),
            name = o.optString("name"),
            color = o.optString("color"),
            warmth = o.optInt("warmth", 3).coerceIn(1, 5),
            formality = o.optInt("formality", 1).coerceIn(1, 3),
            waterproof = o.optBoolean("waterproof", false),
        )
    }

    /**
     * 규칙 엔진 결과(base)를 참고해 AI 가 최종 코디를 고릅니다.
     * 옷장에 없는 ID 를 고르면 규칙 엔진 결과를 그대로 씁니다.
     */
    fun recommend(
        wardrobe: List<ClothingItem>, f: DayForecast, base: Recommendation, bodyPhotoPath: String?,
    ): Recommendation {
        val list = JSONArray()
        wardrobe.forEach {
            list.put(JSONObject().apply {
                put("id", it.id); put("category", it.category.name); put("name", it.name)
                put("color", it.color); put("warmth", it.warmth); put("formality", it.formality)
                put("waterproof", it.waterproof); put("lastWorn", it.lastWorn ?: "")
            })
        }
        val prompt = """
            당신은 퍼스널 스타일리스트입니다. 사용자가 가진 옷장 안에서만 내일 입을 코디를 골라주세요.
            ${if (bodyPhotoPath != null) "첨부한 사진은 사용자의 전신사진입니다. 체형과 분위기에 어울리는 핏·색을 고려하세요." else ""}

            [내일 날씨] ${f.toJson()}
            [옷장] $list
            [규칙 엔진 추천(참고용)] top=${base.topId}, bottom=${base.bottomId}, outer=${base.outerId}, shoes=${base.shoesId}

            규칙:
            - 날씨(기온, 일교차, 비/눈, 바람)에 맞는 두께(warmth)를 우선합니다.
            - 최근 1~3일 안에 입은 옷(lastWorn)은 가능하면 피합니다.
            - 비/눈이면 waterproof 신발을 우선합니다.
            - 아우터가 필요 없으면 outerId 는 null.
            - 반드시 위 옷장의 id 만 사용하세요.

            아래 JSON 으로만 답하세요:
            {"topId": "...", "bottomId": "...", "outerId": "... 또는 null", "shoesId": "...",
             "reason": "2~3문장 한국어 설명 (날씨와 어울림 포인트)",
             "tips": ["짧은 팁", ...]}
        """.trimIndent()

        val o = JSONObject(generate(prompt, listOfNotNull(bodyPhotoPath)))
        val ids = wardrobe.associateBy { it.id }
        fun pick(key: String, cat: Category, fallback: String?): String? {
            val id = o.optStringOrNull(key)
            return if (id != null && ids[id]?.category == cat) id else fallback
        }
        val outerId = if (o.isNull("outerId")) null else pick("outerId", Category.OUTER, base.outerId)
        val aiTips = o.optJSONArray("tips").toStringList()
        return base.copy(
            topId = pick("topId", Category.TOP, base.topId),
            bottomId = pick("bottomId", Category.BOTTOM, base.bottomId),
            outerId = outerId,
            shoesId = pick("shoesId", Category.SHOES, base.shoesId),
            reason = o.optString("reason").ifBlank { base.reason },
            tips = (base.tips + aiTips).distinct(),
            byAi = true,
        )
    }

    private fun generate(prompt: String, imagePaths: List<String>): String {
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        imagePaths.forEach { path ->
            val bytes = File(path).readBytes()
            parts.put(JSONObject().put("inline_data", JSONObject().apply {
                put("mime_type", "image/jpeg")
                put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
            }))
        }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        }
        val res = JSONObject(Http.postJson(endpoint, body.toString()))
        val text = res.getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        // 혹시 ```json ... ``` 으로 감싸져 오면 벗겨냄
        return text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }
}
