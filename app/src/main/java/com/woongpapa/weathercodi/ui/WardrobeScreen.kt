@file:OptIn(ExperimentalMaterial3Api::class)

package com.woongpapa.weathercodi.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
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
        ScreenHeader("내 옷장", "옷 ${wardrobe.size}벌", Modifier.padding(horizontal = 20.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentPill("전체", filter == null) { filter = null }
            Category.entries.forEach { c ->
                SegmentPill("${c.label} ${wardrobe.count { it.category == c }}", filter == c) { filter = c }
            }
        }
        if (places.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                places.forEach { p ->
                    val on = place == p
                    Pill("📍 $p", if (on) Codi.PrimarySoft else Color.Transparent, if (on) Codi.Primary else Codi.Sub,
                        Modifier.clip(Codi.PillShape).clickable { place = if (on) null else p })
                }
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(HangerIcon, null, tint = Codi.Line, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("아직 옷이 없어요", style = MaterialTheme.typography.titleMedium)
                    Text("아래 ‘옷 추가’로 찍어서 등록하세요", color = Codi.Sub, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shown, key = { it.id }) { item ->
                    SoftCard(onClick = { onClick(item) }, padding = 8) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(0.9f).clip(Codi.TileShape).background(Codi.Field),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (item.photoPath != null) {
                                AsyncImage(model = File(item.photoPath), contentDescription = item.name,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else Text(item.category.label, color = Codi.Sub)
                            Pill(thicknessLabel(item.warmth), Color.White.copy(alpha = 0.9f), Codi.Ink,
                                Modifier.align(Alignment.TopEnd).padding(6.dp))
                            if (item.stored) {
                                Pill("📦", Codi.Accent, Color.White, Modifier.align(Alignment.TopStart).padding(6.dp))
                            }
                        }
                        Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp)) {
                            Text(item.displayName(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                if (item.location.isNotBlank()) "📍 ${item.location}" else item.category.label,
                                style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

fun thicknessLabel(w: Int) = when {
    w <= 2 -> "얇음"
    w == 3 -> "보통"
    w == 4 -> "두꺼움"
    else -> "패딩"
}

/** 선택형 알약 버튼 (선택 시 진한 색) */
@Composable
fun SegmentPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.clip(Codi.PillShape)
            .background(if (selected) Codi.Ink else Color.White)
            .border(1.dp, if (selected) Codi.Ink else Codi.Line, Codi.PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        color = if (selected) Color.White else Codi.Ink,
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
    )
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
        containerColor = Codi.Bg,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Codi.Bg),
                title = { Text(if (isNew) "옷 추가" else "옷 수정", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { TextButton(onClick = onDone) { Text("닫기") } },
            )
        },
        bottomBar = {
            Box(Modifier.background(Codi.Bg).navigationBarsPadding().padding(20.dp)) {
                PrimaryButton(
                    text = if (photo == null) "먼저 사진을 찍어주세요" else "저장하기",
                    onClick = { save() },
                    enabled = !tagging && photo != null,
                )
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 1. 사진
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.1f).clip(Codi.CardShape)
                    .background(Color.White).border(1.dp, Codi.Line, Codi.CardShape)
                    .clickable { val u = newCameraUri(ctx); cameraUri = u; camera.launch(u) },
                contentAlignment = Alignment.Center,
            ) {
                val p = photo
                if (p != null) {
                    AsyncImage(model = File(p), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 44.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("눌러서 옷 사진 찍기", style = MaterialTheme.typography.titleMedium)
                        Text("밝은 곳에서 옷만 나오게 찍으면 좋아요", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (tagging) CircularProgressIndicator()
            }
            TextButton(onClick = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, modifier = Modifier.fillMaxWidth()) { Text("또는 갤러리에서 고르기", color = Codi.Sub) }
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
                    SegmentPill(pl, location == pl) { location = if (location == pl) "" else pl }
                }
            }
            OutlinedTextField(location, { location = it }, placeholder = { Text("직접 입력 (예: 안방 장롱 2칸)") },
                singleLine = true, shape = FieldShape, colors = codiFieldColors(), modifier = Modifier.fillMaxWidth())

            // 5. 더 자세히 (접어둠)
            TextButton(onClick = { showMore = !showMore }) {
                Text(if (showMore) "자세히 접기  ▲" else "더 자세히 (이름·색·격식·방수)  ▼", color = Codi.Sub)
            }
            if (showMore) {
                OutlinedTextField(name, { name = it }, label = { Text("이름 (예: 니트, 슬랙스)") },
                    singleLine = true, shape = FieldShape, colors = codiFieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(color, { color = it }, label = { Text("색 (예: 네이비)") },
                    singleLine = true, shape = FieldShape, colors = codiFieldColors(), modifier = Modifier.fillMaxWidth())
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
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
}

/** 크게 누르기 쉬운 선택 버튼 줄 */
@Composable
fun <T> BigChoices(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) Codi.Ink else Color.White)
                    .border(1.dp, if (on) Codi.Ink else Codi.Line, RoundedCornerShape(14.dp))
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Color.White else Codi.Ink, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

fun newCameraUri(ctx: Context): Uri {
    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
}
