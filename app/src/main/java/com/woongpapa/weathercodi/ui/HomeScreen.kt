@file:OptIn(ExperimentalMaterial3Api::class)

package com.woongpapa.weathercodi.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import coil.compose.AsyncImage
import com.woongpapa.weathercodi.data.Category
import com.woongpapa.weathercodi.data.ClothingItem
import com.woongpapa.weathercodi.data.Recommendation
import com.woongpapa.weathercodi.data.Repository
import com.woongpapa.weathercodi.engine.displayName
import com.woongpapa.weathercodi.work.Planner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
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

    // ---- 시작하기 체크리스트 상태 ----
    var notifOn by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    var locSet by remember { mutableStateOf(repo.locationName != "서울(기본)") }
    val hasBasics = Category.entries.filter { it != Category.OUTER }
        .all { c -> wardrobe.any { it.category == c } }
    val setupDone = notifOn && locSet && hasBasics

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) setCurrentLocation(ctx, repo, scope) { ok, _ -> if (ok) locSet = true }
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

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!setupDone) {
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("시작하기 — 3가지만 하면 돼요", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Step("1", "🔔 전날 밤 알림 받기", notifOn, "켜기") {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else notifOn = true
                    }
                    Step("2", "📍 내 동네 날씨로 설정", locSet, "설정") {
                        locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    Step("3", "👕 상의·하의·신발 1개씩 찍기", hasBasics, "찍기") { onAddItem() }
                }
            }
        }

        if (wardrobe.isNotEmpty()) {
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp)); Text("내일 코디 고르는 중…")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        rec?.let { r ->
            val f = r.forecast
            // 날씨 한 줄
            Text(
                "${weatherEmoji(r)} 내일 ${f.minTemp.roundToInt()}~${f.maxTemp.roundToInt()}℃ ${f.summary}" +
                    if (f.rainChance >= 60) " · 비 ${f.rainChance}%" else "",
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )

            // 코디 2x2
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot("상의", r.topId?.let { byId[it] }, Modifier.weight(1f))
                Slot("하의", r.bottomId?.let { byId[it] }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot("아우터", r.outerId?.let { byId[it] }, Modifier.weight(1f))
                Slot("신발", r.shoesId?.let { byId[it] }, Modifier.weight(1f))
            }

            // 팁 (최대 2개)
            r.tips.take(2).forEach { Text(it, fontSize = 15.sp) }

            // 부족한 옷 → 쇼핑
            r.missing.firstOrNull()?.let { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛍️ ${m.substringBefore("(")}이(가) 없어요", Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = {
                        val q = URLEncoder.encode(m.substringBefore("("), "UTF-8")
                        ctx.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://search.shopping.naver.com/search/all?query=$q")))
                    }) { Text("쇼핑 보기") }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.markWorn(r.ids(), r.forecast.date) }
                        wornDone = true
                        onRefresh()
                    }
                },
                enabled = !wornDone && r.ids().isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(if (wornDone) "좋아요! 내일 이렇게 입어요 ✓" else "👍 이걸로 입을게요", fontSize = 16.sp) }
            TextButton(onClick = { load() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text("🔄 다른 코디 보기")
            }
        }

        if (rec == null && wardrobe.isNotEmpty() && !loading) {
            Button(onClick = { load() }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text("내일 코디 보기", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Step(num: String, text: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (done) "✅" else "$num.", fontSize = 18.sp, modifier = Modifier.width(32.dp))
        Text(text, Modifier.weight(1f), fontSize = 15.sp,
            color = if (done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
        if (!done) Button(onClick = onClick) { Text(action) }
    }
}

private fun weatherEmoji(r: Recommendation): String {
    val f = r.forecast
    return when {
        f.snow -> "❄️"
        f.rain -> "🌧️"
        f.summary == "흐림" -> "☁️"
        f.summary == "구름많음" -> "⛅"
        else -> "☀️"
    }
}

@Composable
private fun Slot(label: String, item: ClothingItem?, modifier: Modifier) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val path = item?.photoPath
            if (path != null) {
                AsyncImage(model = File(path), contentDescription = item?.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(if (item == null) "—" else "👕", fontSize = 28.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(item?.displayName() ?: "$label 없음", fontSize = 14.sp, maxLines = 1)
        if (item != null && item.location.isNotBlank()) {
            Text((if (item.stored) "📦 " else "📍 ") + item.location, fontSize = 12.sp, maxLines = 1,
                color = if (item.stored) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
        }
    }
}
