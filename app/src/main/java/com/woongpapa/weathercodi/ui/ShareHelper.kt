package com.woongpapa.weathercodi.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.woongpapa.weathercodi.data.ClothingItem
import com.woongpapa.weathercodi.data.Recommendation
import com.woongpapa.weathercodi.engine.displayName
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** 카카오톡(없으면 공유 메뉴)으로 보내기 */
object ShareHelper {
    private const val KAKAO = "com.kakao.talk"
    const val APP_LINK = "https://github.com/euisun0325-png/weather-codi/releases/latest/download/WeatherCodi.apk"

    private fun isKakaoInstalled(ctx: Context) = try {
        ctx.packageManager.getPackageInfo(KAKAO, 0); true
    } catch (e: Exception) {
        false
    }

    private fun send(ctx: Context, intent: Intent, title: String) {
        if (isKakaoInstalled(ctx)) {
            intent.setPackage(KAKAO)
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
            } catch (e: Exception) {
                intent.setPackage(null)
            }
        }
        ctx.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 추천 코디를 한 장짜리 이미지로 만들어 보내기 */
    fun shareOutfit(ctx: Context, rec: Recommendation, byId: Map<String, ClothingItem>) {
        val f = rec.forecast
        val slots = listOf(
            "상의" to rec.topId, "하의" to rec.bottomId, "아우터" to rec.outerId, "신발" to rec.shoesId,
        ).map { (label, id) -> label to id?.let { byId[it] } }

        val file = drawCard(ctx, rec, slots)
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

        val outfit = slots.mapNotNull { (_, item) -> item?.displayName() }.joinToString(" + ")
        val text = "👕 내일 코디 (${f.date.takeLast(5).replace('-', '/')})\n" +
            "${f.summary} ${f.minTemp.roundToInt()}~${f.maxTemp.roundToInt()}℃, 강수 ${f.rainChance}%\n" +
            outfit + (rec.tips.firstOrNull()?.let { "\n$it" } ?: "") + "\n\n— 날씨코디"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        send(ctx, intent, "코디 보내기")
    }

    /** 앱 설치 링크 보내기 */
    fun shareApp(ctx: Context) {
        val text = "👕 날씨코디 — 내 옷장과 내일 날씨로 전날 밤 코디를 알려주는 앱이에요.\n\n" +
            "안드로이드 설치 파일: $APP_LINK\n(받은 뒤 '출처를 알 수 없는 앱 허용'을 켜고 설치하세요)"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        send(ctx, intent, "앱 공유하기")
    }

    // ---------------- 코디 카드 이미지 그리기 ----------------
    private fun drawCard(ctx: Context, rec: Recommendation, slots: List<Pair<String, ClothingItem?>>): File {
        val w = 1080; val h = 1440
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(244, 245, 249))

        val f = rec.forecast
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(20, 24, 43) }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(139, 145, 163) }

        // 상단 날씨 배너
        val banner = RectF(48f, 48f, w - 48f, 300f)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                f.snow -> Color.rgb(127, 168, 217)
                f.rain -> Color.rgb(62, 76, 107)
                f.summary == "흐림" -> Color.rgb(94, 107, 130)
                else -> Color.rgb(47, 107, 255)
            }
        }
        c.drawRoundRect(banner, 48f, 48f, bg)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        white.textSize = 40f
        c.drawText("내일 ${f.date.takeLast(5).replace('-', '/')} · ${f.summary}", 96f, 130f, white)
        white.textSize = 110f; white.typeface = Typeface.DEFAULT_BOLD
        c.drawText("${f.maxTemp.roundToInt()}° / ${f.minTemp.roundToInt()}°", 92f, 255f, white)
        white.textSize = 38f; white.typeface = Typeface.DEFAULT
        c.drawText("강수 ${f.rainChance}%", w - 330f, 255f, white)

        // 2 x 2 타일
        val gap = 32f; val left = 48f; val top = 340f
        val tileW = (w - left * 2 - gap) / 2f
        val tileH = tileW + 120f
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        slots.forEachIndexed { i, (label, item) ->
            val x = left + (i % 2) * (tileW + gap)
            val y = top + (i / 2) * (tileH + gap)
            c.drawRoundRect(RectF(x, y, x + tileW, y + tileH), 40f, 40f, card)
            val img = RectF(x + 16f, y + 16f, x + tileW - 16f, y + 16f + tileW - 32f)
            val photo = item?.photoPath?.let { BitmapFactory.decodeFile(it) }
            if (photo != null) {
                c.save()
                c.clipPath(Path().apply { addRoundRect(img, 28f, 28f, Path.Direction.CW) })
                c.drawBitmap(photo, centerCrop(photo, img.width(), img.height()), img, null)
                c.restore()
            } else {
                c.drawRoundRect(img, 28f, 28f, Paint().apply { color = Color.rgb(239, 241, 246) })
                sub.textSize = 40f
                c.drawText(if (item == null) "없음" else label, img.centerX() - 40f, img.centerY() + 14f, sub)
            }
            sub.textSize = 30f
            c.drawText(label, x + 28f, img.bottom + 48f, sub)
            bold.textSize = 40f
            c.drawText(ellipsize(item?.displayName() ?: "필요 없어요", bold, tileW - 56f), x + 28f, img.bottom + 98f, bold)
        }

        sub.textSize = 30f
        c.drawText("날씨코디", w - 190f, h - 40f, sub)

        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "codi.jpg")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return out
    }

    private fun centerCrop(b: Bitmap, tw: Float, th: Float): Rect {
        val ratio = tw / th
        return if (b.width.toFloat() / b.height > ratio) {
            val cw = (b.height * ratio).toInt()
            val x = (b.width - cw) / 2
            Rect(x, 0, x + cw, b.height)
        } else {
            val ch = (b.width / ratio).toInt()
            val y = (b.height - ch) / 2
            Rect(0, y, b.width, y + ch)
        }
    }

    private fun ellipsize(text: String, p: Paint, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }
}
