@file:OptIn(ExperimentalMaterial3Api::class)

package com.woongpapa.weathercodi.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.woongpapa.weathercodi.ai.GeminiClient
import com.woongpapa.weathercodi.data.Category
import com.woongpapa.weathercodi.data.ClothingItem
import com.woongpapa.weathercodi.data.Repository
import com.woongpapa.weathercodi.engine.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun WardrobeScreen(modifier: Modifier, wardrobe: List<ClothingItem>, onClick: (ClothingItem) -> Unit) {
    var filter by remember { mutableStateOf<Category?>(null) }
    var place by remember { mutableStateOf<String?>(null) }
    val places = wardrobe.map { it.location.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    val shown = wardrobe.filter {
        (filter == null || it.category == filter) && (place == null || it.location.trim() == place)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null },
                label = { Text("전체 ${wardrobe.size}") })
            Category.entries.forEach { c ->
                FilterChip(selected = filter == c, onClick = { filter = c },
                    label = { Text("${c.label} ${wardrobe.count { it.category == c }}") })
            }
        }
        if (places.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = place == null, onClick = { place = null }, label = { Text("📍 모든 장소") })
                places.forEach { p ->
                    FilterChip(selected = place == p, onClick = { place = if (place == p) null else p },
                        label = { Text("$p ${wardrobe.count { it.location.trim() == p }}") })
                }
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("오른쪽 아래 ‘＋ 옷 추가’로 등록해 보세요", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { item ->
                    Column(Modifier.clickable { onClick(item) }) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (item.photoPath != null) {
                                AsyncImage(model = File(item.photoPath), contentDescription = item.name,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else Text("👕", fontSize = 28.sp)
                        }
                        Text(item.displayName(), fontSize = 13.sp, maxLines = 1)
                        Text(
                            if (item.location.isNotBlank()) (if (item.stored) "📦 " else "📍 ") + item.location
                            else "${item.category.label} · 두께${item.warmth}",
                            fontSize = 11.sp, maxLines = 1,
                            color = if (item.stored) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditItemScreen(initial: ClothingItem, repo: Repository, isNew: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var photo by remember { mutableStateOf(initial.photoPath) }
    var category by remember { mutableStateOf(initial.category) }
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableStateOf(initial.color) }
    var warmth by remember { mutableIntStateOf(initial.warmth) }
    var formality by remember { mutableIntStateOf(initial.formality) }
    var waterproof by remember { mutableStateOf(initial.waterproof) }
    var location by remember { mutableStateOf(initial.location) }
    var stored by remember { mutableStateOf(initial.stored) }
    var showMore by remember { mutableStateOf(false) }
    val knownPlaces = remember {
        (repo.knownLocations() + listOf("장롱", "서랍장", "행거", "신발장", "리빙박스")).distinct().take(8)
    }
    var tagging by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun autoTag(path: String) {
        val key = repo.geminiKey
        if (key.isBlank()) return // AI 없으면 아래 버튼으로 직접 고르기
        tagging = true; msg = null
        scope.launch {
            try {
                val t = withContext(Dispatchers.IO) { GeminiClient(key).tagClothing(path) }
                category = t.category; name = t.name; color = t.color
                warmth = t.warmth; formality = t.formality; waterproof = t.waterproof
                msg = "✨ AI가 골라뒀어요. 맞으면 저장만 누르세요."
            } catch (e: Exception) {
                msg = null
            }
            tagging = false
        }
    }

    fun onPhoto(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val path = withContext(Dispatchers.IO) { repo.importPhoto(uri) }
            if (path != null) { photo = path; autoTag(path) } else { msg = "사진을 불러오지 못했어요" }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { onPhoto(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) onPhoto(cameraUri)
    }

    fun save() {
        val item = initial.copy(
            category = category, name = name.trim(), color = color.trim(),
            warmth = warmth, formality = formality, waterproof = waterproof,
            photoPath = photo, location = location.trim(), stored = stored,
        )
        scope.launch {
            withContext(Dispatchers.IO) { repo.upsert(item) }
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "옷 추가" else "옷 수정") },
                navigationIcon = { TextButton(onClick = onDone) { Text("닫기") } },
            )
        },
        bottomBar = {
            Button(
                onClick = { save() },
                enabled = !tagging && photo != null,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
            ) { Text(if (photo == null) "먼저 사진을 찍어주세요" else "저장", fontSize = 17.sp) }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 1. 사진
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { val u = newCameraUri(ctx); cameraUri = u; camera.launch(u) },
                contentAlignment = Alignment.Center,
            ) {
                val p = photo
                if (p != null) {
                    AsyncImage(model = File(p), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 48.sp)
                        Text("눌러서 옷 사진 찍기", fontSize = 16.sp)
                    }
                }
                if (tagging) CircularProgressIndicator()
            }
            TextButton(onClick = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("또는 갤러리에서 고르기") }
            msg?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary) }

            // 2. 종류
            Question("어떤 옷이에요?")
            BigChoices(
                options = listOf("👕 상의" to Category.TOP, "👖 하의" to Category.BOTTOM,
                    "🧥 아우터" to Category.OUTER, "👟 신발" to Category.SHOES),
                selected = category, onSelect = { category = it },
            )

            // 3. 두께
            Question("두께는요?")
            val thickness = listOf("얇음" to 1, "보통" to 3, "두꺼움" to 4, "패딩·기모" to 5)
            val nearest = thickness.minBy { kotlin.math.abs(it.second - warmth) }.second
            BigChoices(options = thickness, selected = nearest, onSelect = { warmth = it })

            // 4. 보관 위치
            Question("어디에 넣어둬요? (선택)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                knownPlaces.forEach { pl ->
                    FilterChip(selected = location == pl, onClick = { location = if (location == pl) "" else pl },
                        label = { Text(pl) })
                }
            }
            OutlinedTextField(location, { location = it }, placeholder = { Text("직접 입력 (예: 안방 장롱 2칸)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            // 5. 더 자세히 (접어둠)
            TextButton(onClick = { showMore = !showMore }) {
                Text(if (showMore) "▲ 자세히 접기" else "▼ 더 자세히 (이름·색·격식·방수)")
            }
            if (showMore) {
                OutlinedTextField(name, { name = it }, label = { Text("이름 (예: 니트, 슬랙스)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(color, { color = it }, label = { Text("색 (예: 네이비)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("격식", fontWeight = FontWeight.Bold)
                BigChoices(options = listOf("캐주얼" to 1, "세미정장" to 2, "정장" to 3),
                    selected = formality, onSelect = { formality = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("방수 (비 오는 날 추천)", Modifier.weight(1f)); Switch(waterproof, { waterproof = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📦 계절 보관 중 (추천되면 꺼내라고 알려줌)", Modifier.weight(1f)); Switch(stored, { stored = it })
                }
            }

            if (!isNew) {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.delete(initial) }
                        onDone()
                    }
                }) { Text("이 옷 삭제", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Question(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
}

/** 크게 누르기 쉬운 선택 버튼 줄 */
@Composable
fun <T> BigChoices(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (label, value) ->
            val on = value == selected
            if (on) {
                Button(onClick = { onSelect(value) }, modifier = Modifier.weight(1f).height(52.dp),
                    contentPadding = PaddingValues(2.dp)) { Text(label, fontSize = 14.sp, maxLines = 1) }
            } else {
                OutlinedButton(onClick = { onSelect(value) }, modifier = Modifier.weight(1f).height(52.dp),
                    contentPadding = PaddingValues(2.dp)) { Text(label, fontSize = 14.sp, maxLines = 1) }
            }
        }
    }
}

fun newCameraUri(ctx: Context): Uri {
    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
}
