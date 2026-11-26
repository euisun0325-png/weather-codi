@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.woongpapa.weathercodi.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import coil.compose.AsyncImage
import com.woongpapa.weathercodi.data.Category
import com.woongpapa.weathercodi.data.ClothingItem
import com.woongpapa.weathercodi.data.DayForecast
import com.woongpapa.weathercodi.data.Recommendation
import com.woongpapa.weathercodi.data.Repository
import com.woongpapa.weathercodi.engine.displayName
import com.woongpapa.weathercodi.work.Planner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    modifier: Modifier,
    repo: Repository,
    wardrobe: List<ClothingItem>,
    rec: Recommendation?,
    onRecChange: (Recommendation) -> Unit,
    onRefresh: () -> Unit,
    onAddItem: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var wornDone by remember(rec) { mutableStateOf(false) }

    // ---- 시작하기 체크리스트 ----
    var notifOn by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    var locSet by remember { mutableStateOf(repo.locationName != "서울(기본)") }
    var locName by remember { mutableStateOf(repo.locationName) }
    val hasBasics = Category.entries.filter { it != Category.OUTER }.all { c -> wardrobe.any { it.category == c } }
    val setupDone = notifOn && locSet && hasBasics

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) setCurrentLocation(ctx, repo, scope) { ok, name -> if (ok) { locSet = true; locName = name } }
    }

    fun load() {
        loading = true; error = null
        scope.launch {
            try {
                onRecChange(Planner.planTomorrow(ctx))
            } catch (e: Exception) {
                error = "날씨를 못 가져왔어요. 인터넷 연결을 확인해 주세요."
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { if (rec == null && wardrobe.isNotEmpty()) load() }

    val byId = wardrobe.associateBy { it.id }
    val tomorrow = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1)
    val dow = tomorrow.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader("내일 뭐 입지?", "${tomorrow.monthValue}월 ${tomorrow.dayOfMonth}일 $dow · ${locName.removeSuffix("(기본)")}")

        if (!setupDone) {
            SoftCard {
                Text("시작하기", style = MaterialTheme.typography.titleLarge)
                Text("3가지만 하면 매일 밤 코디가 도착해요", style = MaterialTheme.typography.bodyMedium, color = Codi.Sub)
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SetupRow("1", "전날 밤 알림 받기", notifOn, "켜기") {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else notifOn = true
                    }
                    SetupRow("2", "내 동네 날씨로 설정", locSet, "설정") {
                        locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    SetupRow("3", "상의·하의·신발 1개씩 찍기", hasBasics, "찍기") { onAddItem() }
                }
            }
        }

        val r = rec
        if (r != null) {
            WeatherHero(r.forecast)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("추천 코디", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (r.byAi) Pill("✦ AI 추천", Codi.PrimarySoft, Codi.Primary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutfitTile("상의", r.topId?.let { byId[it] }, Modifier.weight(1f))
                OutfitTile("하의", r.bottomId?.let { byId[it] }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutfitTile("아우터", r.outerId?.let { byId[it] }, Modifier.weight(1f))
                OutfitTile("신발", r.shoesId?.let { byId[it] }, Modifier.weight(1f))
            }

            if (r.tips.isNotEmpty() || r.missing.isNotEmpty()) {
                SoftCard(padding = 16) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.tips.take(3).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        r.missing.firstOrNull()?.let { m ->
                            val item = m.substringBefore("(")
                            HorizontalDivider(color = Codi.Line)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🛍️  ${item}이(가) 없어요", style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f))
                                Pill("쇼핑 보기 →", Codi.AccentSoft, Codi.Accent,
                                    Modifier.clip(Codi.PillShape).clickable {
                                        val q = URLEncoder.encode(item, "UTF-8")
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW,
                                            Uri.parse("https://search.shopping.naver.com/search/all?query=$q")))
                                    })
                            }
                        }
                    }
                }
            }

            PrimaryButton(
                text = if (wornDone) "좋아요, 내일 이렇게 입어요 ✓" else "이걸로 입을게요",
                enabled = !wornDone && r.ids().isNotEmpty(),
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.markWorn(r.ids(), r.forecast.date) }
                        wornDone = true
                        onRefresh()
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { load() }, enabled = !loading,
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Codi.Line),
                ) { Text(if (loading) "고르는 중…" else "🔄 다른 코디", color = Codi.Ink) }
                Button(
                    onClick = { ShareHelper.shareOutfit(ctx, r, byId) },
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE500), contentColor = Color(0xFF191919)),
                    elevation = null,
                ) { Text("💬 카톡으로 보내기", fontWeight = FontWeight.SemiBold) }
            }
        } else if (wardrobe.isNotEmpty()) {
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Codi.Primary)
                    Spacer(Modifier.width(10.dp)); Text("내일 코디 고르는 중…", color = Codi.Sub)
                }
            } else {
                PrimaryButton("내일 코디 보기", onClick = { load() })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun WeatherHero(f: DayForecast) {
    val emoji = when {
        f.snow -> "❄️"
        f.rain -> "🌧️"
        f.summary == "흐림" -> "☁️"
        f.summary == "구름많음" -> "⛅"
        else -> "☀️"
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(weatherBrush(f.summary, f.rain, f.snow)).padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("내일 · ${f.summary}", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp,
                        fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${f.maxTemp.roundToInt()}°", color = Color.White, fontSize = 56.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
                        Text("  / ${f.minTemp.roundToInt()}°", color = Color.White.copy(alpha = 0.75f),
                            fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
                Text(emoji, fontSize = 64.sp)
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val glass = Color.White.copy(alpha = 0.2f)
                Pill("💧 강수 ${f.rainChance}%", glass, Color.White)
                Pill("💨 ${f.maxWind.roundToInt()}m/s", glass, Color.White)
                Pill("↕ 일교차 ${(f.maxTemp - f.minTemp).roundToInt()}°", glass, Color.White)
            }
        }
    }
}

@Composable
private fun SetupRow(num: String, text: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NumberDot(num, done)
        Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (done) Codi.Sub else Codi.Ink)
        if (!done) {
            Button(
                onClick = onClick, shape = Codi.PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = Codi.PrimarySoft, contentColor = Codi.Primary),
                elevation = null, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) { Text(action, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun OutfitTile(label: String, item: ClothingItem?, modifier: Modifier) {
    SoftCard(modifier, padding = 8) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(Codi.TileShape).background(Codi.Field),
            contentAlignment = Alignment.Center,
        ) {
            val path = item?.photoPath
            if (path != null) {
                AsyncImage(model = File(path), contentDescription = item?.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(if (item == null) "없음" else label, color = Codi.Sub, fontSize = 14.sp)
            }
            if (item?.stored == true) {
                Pill("📦 꺼내기", Codi.Accent, Color.White,
                    Modifier.align(Alignment.TopStart).padding(6.dp))
            }
        }
        Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(item?.displayName() ?: "필요 없어요", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (item != null && item.location.isNotBlank()) {
                Text("📍 ${item.location}", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}
