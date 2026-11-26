@file:OptIn(ExperimentalMaterial3Api::class)

package com.woongpapa.weathercodi.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.woongpapa.weathercodi.data.Repository
import com.woongpapa.weathercodi.weather.WeatherService
import com.woongpapa.weathercodi.work.Notifier
import com.woongpapa.weathercodi.work.Planner
import com.woongpapa.weathercodi.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(modifier: Modifier, repo: Repository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var body by remember { mutableStateOf(repo.bodyPhotoPath) }
    var locName by remember { mutableStateOf(repo.locationName) }
    var hour by remember { mutableFloatStateOf(repo.notifyHour.toFloat()) }
    var kma by remember { mutableStateOf(repo.kmaKey) }
    var gemini by remember { mutableStateOf(repo.geminiKey) }
    var msg by remember { mutableStateOf<String?>(null) }

    val bodyPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val p = withContext(Dispatchers.IO) { repo.importPhoto(uri) }
            if (p != null) {
                repo.bodyPhotoPath?.let { File(it).delete() }
                repo.bodyPhotoPath = p; body = p
            }
        }
    }

    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            msg = "위치 권한이 없어서 기본 위치(서울)를 사용해요."
        } else fetchLocation(ctx) { loc ->
            if (loc == null) {
                msg = "위치를 못 찾았어요. 위치(GPS)를 켜고 다시 눌러주세요."
            } else scope.launch {
                val name = withContext(Dispatchers.IO) { placeName(ctx, loc) }
                repo.lat = loc.latitude; repo.lon = loc.longitude; repo.locationName = name
                locName = name
                val (nx, ny) = WeatherService.toGrid(loc.latitude, loc.longitude)
                msg = "위치 저장: $name (기상청 격자 $nx, $ny)"
            }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Section("내 전신사진") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(90.dp, 130.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val p = body
                    if (p != null) AsyncImage(model = File(p), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text("🧍", fontSize = 36.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("AI가 체형·분위기에 맞는 핏과 색을 고를 때 참고해요. 사진은 이 휴대폰 안에만 저장돼요.",
                        fontSize = 13.sp)
                    OutlinedButton(onClick = {
                        bodyPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(if (body == null) "사진 등록" else "사진 바꾸기") }
                }
            }
        }

        Section("위치") {
            Text("현재: $locName")
            Button(onClick = { locPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                Text("📍 현재 위치로 설정")
            }
        }

        Section("전날 알림 시각") {
            val h = hour.roundToInt()
            Text("매일 ${if (h < 12) "오전" else "오후"} ${if (h % 12 == 0) 12 else h % 12}시에 내일 코디를 알려드려요")
            Slider(hour, { hour = it }, valueRange = 17f..23f, steps = 5,
                onValueChangeFinished = {
                    repo.notifyHour = hour.roundToInt()
                    Scheduler.scheduleNext(ctx) // 새 시각으로 다시 예약
                    msg = "알림 시각을 바꿨어요"
                })
        }

        Section("API 키") {
            OutlinedTextField(gemini, { gemini = it }, label = { Text("Gemini API 키 (AI 태그·코디)") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth())
            LinkText(ctx, "→ 무료 키 받기: aistudio.google.com", "https://aistudio.google.com/apikey")
            OutlinedTextField(kma, { kma = it }, label = { Text("기상청 단기예보 키 (선택)") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth())
            LinkText(ctx, "→ 공공데이터포털 ‘기상청_단기예보 조회서비스’ 활용신청 (일반 인증키 Decoding)",
                "https://www.data.go.kr/data/15084084/openapi.do")
            Text("기상청 키가 없으면 무료 날씨(Open-Meteo)로 동작해요.", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline)
            Button(onClick = {
                repo.geminiKey = gemini; repo.kmaKey = kma; msg = "키를 저장했어요"
            }) { Text("키 저장") }
        }

        Section("테스트") {
            OutlinedButton(onClick = {
                msg = "내일 코디 만드는 중…"
                scope.launch {
                    msg = try {
                        val rec = Planner.planTomorrow(ctx)
                        Notifier.show(ctx, rec)
                        "알림을 보냈어요 (${rec.forecast.source})"
                    } catch (e: Exception) {
                        "실패: ${e.message?.take(100)}"
                    }
                }
            }) { Text("지금 알림 미리 받아보기") }
        }

        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Text("날씨코디 v0.2 · made by woongpapa", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
private fun LinkText(ctx: Context, label: String, url: String) {
    TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        contentPadding = PaddingValues(0.dp)) {
        Text(label, fontSize = 12.sp)
    }
}

@SuppressLint("MissingPermission")
private fun fetchLocation(ctx: Context, onResult: (Location?) -> Unit) {
    val lm = ctx.getSystemService(LocationManager::class.java)
    val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        .maxByOrNull { it.time }
    if (last != null) {
        onResult(last); return
    }
    if (Build.VERSION.SDK_INT >= 30 && runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
        try {
            lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, ctx.mainExecutor) { onResult(it) }
        } catch (e: Exception) {
            onResult(null)
        }
    } else {
        onResult(null)
    }
}

@Suppress("DEPRECATION")
private fun placeName(ctx: Context, loc: Location): String = try {
    val a = Geocoder(ctx, Locale.KOREA).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
    listOfNotNull(a?.locality ?: a?.adminArea, a?.subLocality ?: a?.thoroughfare)
        .joinToString(" ").ifBlank { "현재 위치" }
} catch (e: Exception) {
    "현재 위치"
}
