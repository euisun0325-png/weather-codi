@file:OptIn(ExperimentalMaterial3Api::class)

package com.woongpapa.weathercodi.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.work.ExistingWorkPolicy
import com.woongpapa.weathercodi.data.ClothingItem
import com.woongpapa.weathercodi.data.Repository
import com.woongpapa.weathercodi.work.Scheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
        )
        super.onCreate(savedInstanceState)
        Scheduler.scheduleNext(this, ExistingWorkPolicy.KEEP)

        val repo = Repository(this)
        setContent { CodiTheme { App(repo) } }
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
        containerColor = Codi.Bg,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Codi.Ink, selectedTextColor = Codi.Ink,
                    unselectedIconColor = Codi.Sub, unselectedTextColor = Codi.Sub,
                    indicatorColor = Codi.Field,
                )
                NavigationBarItem(tab == 0, { tab = 0; refresh() },
                    icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("내일 코디") }, colors = itemColors)
                NavigationBarItem(tab == 1, { tab = 1; refresh() },
                    icon = { Icon(HangerIcon, null) }, label = { Text("옷장") }, colors = itemColors)
                NavigationBarItem(tab == 2, { tab = 2; refresh() },
                    icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("설정") }, colors = itemColors)
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { editing = ClothingItem() },
                    containerColor = Codi.Ink, contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                ) { Text("＋  옷 추가") }
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
