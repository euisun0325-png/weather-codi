@file:OptIn(ExperimentalMaterial3Api::class)

package com.woongpapa.weathercodi.ui

import android.content.Intent
import android.net.Uri
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
import coil.compose.AsyncImage
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
    onGoWardrobe: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var wornDone by remember(rec) { mutableStateOf(false) }

    fun load() {
        loading = true; error = null
        scope.launch {
            try {
                onRecChange(Planner.planTomorrow(ctx))
            } catch (e: Exception) {
                error = "날씨를 가져오지 못했어요: ${e.message}"
            }
            loading = false
        }
    }

    // 처음 열었는데 추천이 없으면 바로 받아오기
    LaunchedEffect(Unit) { if (rec == null && wardrobe.isNotEmpty()) load() }

    val byId = wardrobe.associateBy { it.id }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (wardrobe.isEmpty()) {
            Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("옷장이 비어 있어요", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("가지고 있는 상의·하의·아우터·신발을 사진으로 등록하면 매일 저녁 내일 날씨에 맞는 코디를 알려드려요.")
                    Button(onClick = onGoWardrobe) { Text("옷 등록하러 가기") }
                }
            }
        }

        rec?.let { r -> ForecastCard(r) }

        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp)); Text("내일 날씨 보고 코디 고르는 중…")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        rec?.let { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("추천 코디", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (r.byAi) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = {}, label = { Text("AI 스타일리스트") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot("아우터", r.outerId?.let { byId[it] }, Modifier.weight(1f))
                Slot("상의", r.topId?.let { byId[it] }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot("하의", r.bottomId?.let { byId[it] }, Modifier.weight(1f))
                Slot("신발", r.shoesId?.let { byId[it] }, Modifier.weight(1f))
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(r.reason)
                    r.tips.forEach { Text(it, fontSize = 14.sp) }
                }
            }

            if (r.missing.isNotEmpty()) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🛍️ 이런 옷이 있으면 더 좋아요", fontWeight = FontWeight.Bold)
                        r.missing.forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m, Modifier.weight(1f))
                                TextButton(onClick = {
                                    val q = URLEncoder.encode(m.substringBefore("("), "UTF-8")
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://search.shopping.naver.com/search/all?query=$q")))
                                }) { Text("쇼핑 보기") }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { load() }, enabled = !loading, modifier = Modifier.weight(1f)) {
                    Text("다른 코디")
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
                    modifier = Modifier.weight(1f),
                ) { Text(if (wornDone) "저장했어요 ✓" else "이걸로 입을게요") }
            }
            Text("‘이걸로 입을게요’를 누르면 같은 옷이 며칠 연속 추천되지 않아요.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }

        if (rec == null && wardrobe.isNotEmpty() && !loading) {
            Button(onClick = { load() }) { Text("내일 코디 받기") }
        }
    }
}

@Composable
private fun ForecastCard(r: Recommendation) {
    val f = r.forecast
    val emoji = when {
        f.snow -> "❄️"
        f.rain -> "🌧️"
        f.summary == "흐림" -> "☁️"
        f.summary == "구름많음" -> "⛅"
        else -> "☀️"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 44.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("내일 (${f.date.takeLast(5).replace('-', '/')}) · ${f.summary}", fontWeight = FontWeight.Bold)
                Text("${f.minTemp.roundToInt()}℃ / ${f.maxTemp.roundToInt()}℃", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("강수 ${f.rainChance}% · 바람 ${f.maxWind.roundToInt()}m/s · ${f.source}", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Slot(label: String, item: ClothingItem?, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(8.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
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
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Text(item?.displayName() ?: "필요 없음/없음", fontSize = 14.sp, maxLines = 1)
            if (item != null && item.location.isNotBlank()) {
                Text((if (item.stored) "📦 " else "📍 ") + item.location, fontSize = 12.sp, maxLines = 1,
                    color = if (item.stored) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
            }
        }
    }
}
