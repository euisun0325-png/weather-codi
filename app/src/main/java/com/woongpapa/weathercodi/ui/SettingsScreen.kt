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
import com.woongpapa.weathercodi.work.Notifier
import com.woongpapa.weathercodi.work.Planner
import com.woongpapa.weathercodi.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun SettingsScreen(modifier: Modifier, repo: Repository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var body by remember { mutableStateOf(repo.bodyPhotoPath) }
    var locName by remember { mutableStateOf(repo.locationName) }
    var hour by remember { mutableIntStateOf(repo.notifyHour) }
    var kma by remember { mutableStateOf(repo.kmaKey) }
    var gemini by remember { mutableStateOf(repo.geminiKey) }
    var showAdvanced by remember { mutableStateOf(false) }
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
            msg = "위치 권한이 없어서 서울 날씨를 사용해요."
        } else setCurrentLocation(ctx, repo, scope) { ok, name ->
            if (ok) { locName = name; msg = "📍 $name 날씨로 설정했어요" }
            else { msg = "위치를 못 찾았어요. 휴대폰 위치(GPS)를 켜고 다시 눌러주세요." }
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("설정")
        Section("알림 시간") {
            Text("전날 밤 이 시간에 내일 코디를 알려드려요", style = MaterialTheme.typography.bodyMedium, color = Codi.Sub)
            Spacer(Modifier.height(4.dp))
            BigChoices(
                options = listOf("저녁 7시" to 19, "8시" to 20, "9시" to 21, "10시" to 22),
                selected = hour,
                onSelect = {
                    hour = it
                    repo.notifyHour = it
                    Scheduler.scheduleNext(ctx)
                    msg = "매일 ${if (it == 19) "저녁 7" else (it - 12).toString()}시에 알려드릴게요"
                },
            )
        }

        Section("내 동네") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📍 $locName", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                SegmentPill("현재 위치로", false) { locPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
            }
        }

        Section("내 전신사진 (선택)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(72.dp, 100.dp).clip(Codi.TileShape).background(Codi.Field),
                    contentAlignment = Alignment.Center,
                ) {
                    val p = body
                    if (p != null) AsyncImage(model = File(p), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text("🧍", fontSize = 30.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("AI가 나에게 어울리는 코디를 고를 때 참고해요", style = MaterialTheme.typography.bodyMedium, color = Codi.Sub)
                    SegmentPill(if (body == null) "사진 등록" else "바꾸기", false) {
                        bodyPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            }
        }

        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "고급 설정 접기  ▲" else "고급 설정 (AI 키·테스트)  ▼", color = Codi.Sub)
        }
        if (showAdvanced) {
            Section("AI 키 (넣으면 더 똑똑해져요)") {
                Text("넣으면: 옷 사진만 찍어도 종류·두께가 자동 입력되고, AI가 코디를 골라줘요. 안 넣어도 앱은 동작해요.",
                    fontSize = 13.sp)
                OutlinedTextField(gemini, { gemini = it }, label = { Text("Gemini API 키") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    shape = FieldShape, colors = codiFieldColors(), modifier = Modifier.fillMaxWidth())
                LinkText(ctx, "→ 무료 키 받기 (aistudio.google.com)", "https://aistudio.google.com/apikey")
                OutlinedTextField(kma, { kma = it }, label = { Text("기상청 키 (없어도 됨)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    shape = FieldShape, colors = codiFieldColors(), modifier = Modifier.fillMaxWidth())
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
                            "알림을 보냈어요 (날씨: ${rec.forecast.source})"
                        } catch (e: Exception) {
                            "실패: ${e.message?.take(100)}"
                        }
                    }
                }) { Text("지금 알림 미리 받아보기") }
            }
        }
        Text("날씨코디 v0.4 · made by woongpapa", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
    }
}

/** 현재 위치를 찾아 저장 (onResult: 성공 여부, 동네 이름) */
fun setCurrentLocation(
    ctx: Context, repo: Repository, scope: kotlinx.coroutines.CoroutineScope,
    onResult: (Boolean, String) -> Unit,
) {
    fetchLocation(ctx) { loc ->
        if (loc == null) {
            onResult(false, "")
        } else scope.launch {
            val name = withContext(Dispatchers.IO) { placeName(ctx, loc) }
            repo.lat = loc.latitude; repo.lon = loc.longitude; repo.locationName = name
            onResult(true, name)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
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
