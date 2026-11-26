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
import kotlin.math.roundToInt

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
    var warmth by remember { mutableFloatStateOf(initial.warmth.toFloat()) }
    var formality by remember { mutableFloatStateOf(initial.formality.toFloat()) }
    var waterproof by remember { mutableStateOf(initial.waterproof) }
    var location by remember { mutableStateOf(initial.location) }
    var stored by remember { mutableStateOf(initial.stored) }
    val knownPlaces = remember {
        (repo.knownLocations() + listOf("안방 장롱", "옷방 행거", "서랍장", "신발장", "리빙박스", "베란다 수납장")).distinct()
    }
    var tagging by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun autoTag(path: String) {
        val key = repo.geminiKey
        if (key.isBlank()) {
            msg = "설정에서 Gemini API 키를 넣으면 사진만 찍어도 자동으로 채워져요."
            return
        }
        tagging = true; msg = null
        scope.launch {
            try {
                val t = withContext(Dispatchers.IO) { GeminiClient(key).tagClothing(path) }
                category = t.category; name = t.name; color = t.color
                warmth = t.warmth.toFloat(); formality = t.formality.toFloat(); waterproof = t.waterproof
                msg = "AI가 채웠어요. 틀린 부분만 고쳐주세요."
            } catch (e: Exception) {
                msg = "자동 태그 실패: ${e.message?.take(80)}"
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

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (isNew) "옷 추가" else "옷 수정") },
            navigationIcon = { TextButton(onClick = onDone) { Text("취소") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val p = photo
                if (p != null) AsyncImage(model = File(p), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("📷 사진을 추가하세요", color = MaterialTheme.colorScheme.outline)
                if (tagging) CircularProgressIndicator()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val uri = newCameraUri(ctx); cameraUri = uri; camera.launch(uri)
                }, modifier = Modifier.weight(1f)) { Text("카메라") }
                OutlinedButton(onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.weight(1f)) { Text("갤러리") }
            }
            msg?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }

            Text("종류", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text("이름 (예: 니트, 슬랙스, 롱패딩)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(color, { color = it }, label = { Text("색상 (예: 네이비)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            val w = warmth.roundToInt()
            Text("두께: $w — " + listOf("", "아주 얇음(반팔·린넨)", "얇음(셔츠·긴팔)", "보통(맨투맨·니트)", "두꺼움(코트·두꺼운 니트)", "아주 두꺼움(패딩·기모)")[w],
                fontWeight = FontWeight.Bold)
            Slider(warmth, { warmth = it }, valueRange = 1f..5f, steps = 3)

            val fm = formality.roundToInt()
            Text("격식: " + listOf("", "캐주얼", "세미정장", "정장")[fm], fontWeight = FontWeight.Bold)
            Slider(formality, { formality = it }, valueRange = 1f..3f, steps = 1)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("방수 (비 오는 날 우선 추천)", Modifier.weight(1f))
                Switch(waterproof, { waterproof = it })
            }

            Text("보관 위치", fontWeight = FontWeight.Bold)
            OutlinedTextField(location, { location = it },
                label = { Text("예: 안방 장롱 2칸, 옷방 서랍 3단") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                knownPlaces.forEach { p ->
                    AssistChip(onClick = { location = p }, label = { Text(p) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("계절 보관 중 📦")
                    Text("박스나 깊은 칸에 넣어둔 옷이면 켜두세요. 추천될 때 전날 꺼내라고 알려드려요.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Switch(stored, { stored = it })
            }

            Button(
                onClick = {
                    val item = initial.copy(
                        location = location.trim(), stored = stored,
                        category = category, name = name.trim(), color = color.trim(),
                        warmth = warmth.roundToInt(), formality = formality.roundToInt(),
                        waterproof = waterproof, photoPath = photo,
                    )
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.upsert(item) }
                        onDone()
                    }
                },
                enabled = !tagging && (photo != null || name.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }

            if (!isNew) {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.delete(initial) }
                        onDone()
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

fun newCameraUri(ctx: Context): Uri {
    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
}
