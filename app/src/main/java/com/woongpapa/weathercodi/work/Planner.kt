package com.woongpapa.weathercodi.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.woongpapa.weathercodi.ai.GeminiClient
import com.woongpapa.weathercodi.data.Recommendation
import com.woongpapa.weathercodi.data.Repository
import com.woongpapa.weathercodi.engine.OutfitEngine
import com.woongpapa.weathercodi.engine.displayName
import com.woongpapa.weathercodi.ui.MainActivity
import com.woongpapa.weathercodi.weather.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** 날씨 받기 → 규칙 엔진 → (키 있으면) AI 다듬기 → 저장 */
object Planner {
    suspend fun planTomorrow(context: Context): Recommendation = withContext(Dispatchers.IO) {
        val repo = Repository(context)
        val forecast = WeatherService.tomorrow(repo.lat, repo.lon, repo.kmaKey)
        val wardrobe = repo.loadWardrobe()
        var rec = OutfitEngine.recommend(wardrobe, forecast)
        if (repo.geminiKey.isNotBlank() && wardrobe.isNotEmpty()) {
            rec = try {
                val body = repo.bodyPhotoPath?.takeIf { java.io.File(it).exists() }
                GeminiClient(repo.geminiKey).recommend(wardrobe, forecast, rec, body)
            } catch (e: Exception) {
                rec // AI 실패해도 규칙 엔진 결과로 알림
            }
        }
        rec = withLocationTips(rec, wardrobe)
        repo.lastRecommendation = rec
        rec
    }

    /** 계절 보관 중인 옷이 뽑히면 "어디서 꺼내세요" 팁 추가 */
    private fun withLocationTips(rec: Recommendation, wardrobe: List<com.woongpapa.weathercodi.data.ClothingItem>): Recommendation {
        val byId = wardrobe.associateBy { it.id }
        val storedTips = rec.ids().mapNotNull { byId[it] }.filter { it.stored }.map {
            "📦 ${it.displayName()}은(는) ${it.location.ifBlank { "보관함" }}에 있어요 — 오늘 밤 미리 꺼내두세요"
        }
        return if (storedTips.isEmpty()) rec else rec.copy(tips = storedTips + rec.tips)
    }
}

object Notifier {
    private const val CHANNEL = "daily_codi"

    fun show(context: Context, rec: Recommendation) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "내일 코디 알림", NotificationManager.IMPORTANCE_DEFAULT))

        val wardrobe = Repository(context).loadWardrobe().associateBy { it.id }
        val outfit = rec.ids().mapNotNull { wardrobe[it] }.joinToString(" + ") {
            if (it.location.isBlank()) it.displayName() else "${it.displayName()}(${it.location})"
        }
        val f = rec.forecast
        val title = "내일 ${f.summary} ${f.minTemp.toInt()}~${f.maxTemp.toInt()}℃ · 코디 도착 👕"
        val text = buildString {
            append(if (outfit.isNotBlank()) outfit else "옷장에 옷을 등록하면 코디를 추천해 드려요")
            rec.tips.firstOrNull()?.let { append("\n").append(it) }
        }
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1001, n)
        } catch (e: SecurityException) { }
    }
}

class DailyCodiWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val rec = Planner.planTomorrow(applicationContext)
            Notifier.show(applicationContext, rec)
            Scheduler.scheduleNext(applicationContext, policy = ExistingWorkPolicy.APPEND_OR_REPLACE) // 다음 날 같은 시각 예약
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else {
                Scheduler.scheduleNext(applicationContext, policy = ExistingWorkPolicy.APPEND_OR_REPLACE)
                Result.success() // failure 로 끝내면 이어 붙인 다음 예약까지 취소되므로 success 처리
            }
        }
    }
}

object Scheduler {
    private const val NAME = "daily_codi"

    /**
     * 다음 알림 시각(설정한 시)까지 기다렸다가 실행.
     * - 워커 안에서: APPEND_OR_REPLACE (실행 중인 자신을 취소하지 않도록 뒤에 이어 붙임)
     * - 앱 실행 시: KEEP (이미 예약돼 있으면 그대로)
     * - 설정에서 알림 시각 변경: REPLACE
     */
    fun scheduleNext(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val hour = Repository(context).notifyHour
        val now = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()

        val req = OneTimeWorkRequestBuilder<DailyCodiWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, req)
    }
}
