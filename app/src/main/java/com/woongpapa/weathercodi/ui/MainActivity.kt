@file:OptIn(ExperimentalMaterial3Api::class)

package com.woongpapa.weathercodi.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.work.ExistingWorkPolicy
import com.woongpapa.weathercodi.data.ClothingItem
import com.woongpapa.weathercodi.data.Repository
import com.woongpapa.weathercodi.work.Scheduler

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        Scheduler.scheduleNext(this, ExistingWorkPolicy.KEEP)

        val repo = Repository(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF3B6FE0), secondary = Color(0xFFFF8A3D))) {
                App(repo)
            }
        }
    }
}

@Composable
fun App(repo: Repository) {
    var tab by remember { mutableIntStateOf(0) }
    var wardrobe by remember { mutableStateOf(repo.loadWardrobe()) }
    var rec by remember { mutableStateOf(repo.lastRecommendation) }
    var editing by remember { mutableStateOf<ClothingItem?>(null) }

    fun refresh() {
        wardrobe = repo.loadWardrobe()
        rec = repo.lastRecommendation
    }

    val current = editing
    if (current != null) {
        BackHandler { editing = null }
        EditItemScreen(
            initial = current,
            repo = repo,
            isNew = wardrobe.none { it.id == current.id },
            onDone = { editing = null; refresh() },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(listOf("내일 뭐 입지?", "내 옷장", "설정")[tab]) })
        },
        bottomBar = {
            NavigationBar {
                listOf("👕" to "내일 코디", "🗄️" to "옷장", "⚙️" to "설정").forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i; refresh() },
                        icon = { Text(icon) },
                        label = { Text(label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(onClick = { editing = ClothingItem() }) { Text("＋ 옷 추가") }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            0 -> HomeScreen(m, repo, wardrobe, rec, onRecChange = { rec = it }, onRefresh = { refresh() },
                onAddItem = { editing = ClothingItem() })
            1 -> WardrobeScreen(m, wardrobe, onClick = { editing = it })
            else -> SettingsScreen(m, repo)
        }
    }
}
