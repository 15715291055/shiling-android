package com.fuguang.localdiary.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuguang.localdiary.backup.BackupManager
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.data.DiaryViewModel
import com.fuguang.localdiary.data.FootprintEntity
import com.fuguang.localdiary.data.rememberDailyQuote
import com.fuguang.localdiary.ui.theme.JournalAppearance
import com.fuguang.localdiary.location.LocationHelper
import com.fuguang.localdiary.ui.theme.CardWhite
import com.fuguang.localdiary.ui.theme.Ink
import com.fuguang.localdiary.ui.theme.InkSoft
import com.fuguang.localdiary.ui.theme.Line
import com.fuguang.localdiary.ui.theme.Paper
import com.fuguang.localdiary.ui.theme.PaperLight
import com.fuguang.localdiary.ui.theme.Sage
import com.fuguang.localdiary.ui.theme.SageLight
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.saveable.listSaver

private val diarySaver = listSaver<DiaryEntity?, String>(
    save = { entry -> entry?.let { listOf(it.id.toString(), it.date, it.time, it.body, it.tags, it.mood, it.imagePaths, it.createdAt.toString(), it.updatedAt.toString(), it.moodId, it.moodIcon, it.moodColor.toString()) }.orEmpty() },
    restore = { values -> if (values.isEmpty()) null else DiaryEntity(values[0].toLong(), values[1], values[2], values[3], values[4], values[5], values[6], values[7].toLong(), values[8].toLong(), values.getOrElse(9) { "" }, values.getOrElse(10) { "" }, values.getOrElse(11) { "0" }.toLong()) }
)

enum class MainTab(val label: String, val icon: ImageVector) {
    JOURNAL("日记", Icons.Outlined.AutoStories),
    CALENDAR("回忆", Icons.Outlined.CalendarMonth),
    FOOTPRINT("足迹", Icons.Outlined.Place),
    MINE("我的", Icons.Outlined.Settings)
}

@Composable
fun FuguangApp(viewModel: DiaryViewModel) {
    val context = LocalContext.current
    val diaries by viewModel.diaries.collectAsStateWithLifecycle()
    val footprints by viewModel.footprints.collectAsStateWithLifecycle()
    val moods by viewModel.moods.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(MainTab.JOURNAL) }
    var readerDate by rememberSaveable { mutableStateOf<String?>(null) }
    var readerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var shareDate by rememberSaveable { mutableStateOf<String?>(null) }
    var showMoods by rememberSaveable { mutableStateOf(false) }
    var photoPaths by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var photoIndex by rememberSaveable { mutableStateOf(0) }
    if (photoPaths.isNotEmpty()) PhotoViewer(photoPaths, photoIndex) { photoPaths = arrayListOf() }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable(stateSaver = diarySaver) { mutableStateOf<DiaryEntity?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var locationBusy by remember { mutableStateOf(false) }
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var locationEnabled by remember {
        mutableStateOf(context.getSharedPreferences("fuguang_settings", Context.MODE_PRIVATE).getBoolean("location_enabled", true))
    }
    var showClearAll by remember { mutableStateOf(false) }
    var showExportWarning by remember { mutableStateOf(false) }
    var showClearFootprints by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var appearance by remember { mutableStateOf(JournalAppearance.read(context)) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showQuoteInfo by rememberSaveable { mutableStateOf(false) }
    val dailyQuote = rememberDailyQuote(context)

    if (showQuoteInfo && dailyQuote != null) {
        AlertDialog(
            onDismissRequest = { showQuoteInfo = false },
            title = { Text("每日一言") },
            text = { Column {
                Text(dailyQuote.text)
                Spacer(Modifier.height(14.dp))
                Text("${dailyQuote.author} ·《${dailyQuote.work}》", color = InkSoft)
                if (dailyQuote.translator.isNotBlank()) Text("译者：${dailyQuote.translator}", color = InkSoft)
            } },
            confirmButton = { TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dailyQuote.sourceUrl))) }
            }) { Text("查看出处") } },
            dismissButton = { TextButton(onClick = { showQuoteInfo = false }) { Text("关闭") } }
        )
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun recordLocation() {
        if (!locationEnabled) {
            locationMessage = "足迹记录已关闭。你可以先打开定位开关，再记录今日位置。"
            return
        }
        scope.launch {
            locationBusy = true
            locationMessage = null
            try {
                val location = LocationHelper.currentLocation(context)
                val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                val date = LocalDate.now().toString()
                viewModel.addFootprint(
                    FootprintEntity(
                        date = date,
                        time = now,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy
                    )
                )
                locationMessage = "今日位置已记录到本机"
                snackbar.showSnackbar("今日位置已记录到本机")
            } catch (error: Exception) {
                val reason = when (error) {
                    is SecurityException -> "定位权限未开启。请在系统设置中允许拾零使用位置信息。"
                    is TimeoutCancellationException -> "定位超时。请到室外或靠近窗边，确认系统定位已开启后重试。"
                    else -> error.message ?: "暂时无法获取位置，请检查定位服务后重试。"
                }
                locationMessage = reason
                snackbar.showSnackbar(reason)
            } finally {
                locationBusy = false
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) recordLocation()
        else locationMessage = "你没有授予定位权限。拾零不会反复弹窗；需要时可在系统设置中手动开启。"
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val count = BackupManager.export(context, uri, viewModel)
                snackbar.showSnackbar("备份已导出，包含 $count 篇日记")
            } catch (error: Exception) {
                snackbar.showSnackbar("导出失败：${error.message ?: "请检查文件保存权限和存储空间"}")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val (diaryCount, footprintCount) = BackupManager.import(context, uri, viewModel)
                snackbar.showSnackbar("导入完成：新增 $diaryCount 篇日记、$footprintCount 条足迹；已有记录不会被覆盖")
            } catch (error: Exception) {
                snackbar.showSnackbar("导入失败：${error.message ?: "文件可能已损坏或格式不正确"}")
            }
        }
    }

    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            title = { Text("备份包含敏感位置数据") },
            text = { Text("本次 ZIP 备份会包含日记、图片和足迹坐标。请确认保存到可信位置，不要随意分享备份文件。") },
            confirmButton = {
                TextButton(onClick = {
                    showExportWarning = false
                    exportLauncher.launch("拾零备份_${LocalDate.now()}.zip")
                }) { Text("继续导出") }
            },
            dismissButton = { TextButton(onClick = { showExportWarning = false }) { Text("取消") } }
        )
    }
    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text("清空全部数据？") },
            text = { Text("这会删除本机保存的全部日记、图片和足迹，且无法撤销。建议先导出备份。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearAll = false
                    scope.launch {
                        viewModel.clearAll()
                        selectedDate = null
                        snackbar.showSnackbar("本机数据已清空")
                    }
                }) { Text("确认清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearAll = false }) { Text("取消") } }
        )
    }
    if (showClearFootprints) {
        AlertDialog(
            onDismissRequest = { showClearFootprints = false },
            title = { Text("清除全部足迹？") },
            text = { Text("只会删除本机保存的位置记录，不会删除日记。此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearFootprints = false
                    scope.launch {
                        viewModel.deleteFootprints()
                        snackbar.showSnackbar("足迹数据已清除")
                    }
                }) { Text("清除足迹", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearFootprints = false }) { Text("取消") } }
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于拾零") },
            text = { Text("拾零是一款以本地保存为原则的日记应用。日记、图片和足迹保存在这台设备上，不需要账号，也不提供云同步。\n\n地图底图由 OpenStreetMap 提供，加载瓦片会访问网络并暴露正在查看的地图区域；你的足迹记录不会上传。卸载应用或清除应用数据可能导致本机记录丢失，请定期导出备份。") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("知道了") } }
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalPhotoViewer provides { paths, index -> photoPaths = ArrayList(paths); photoIndex = index }) {
    AnimatedContent(
        targetState = when { editorOpen -> "editor"; showAppearance -> "appearance"; showMoods -> "moods"; shareDate != null -> "share"; readerDate != null -> "reader"; else -> "main" },
        transitionSpec = {
            if (targetState != "main") {
                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 5 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 8 })
            } else {
                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 5 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 8 })
            }
        },
        label = "editor-transition"
    ) { showEditor ->
    if (showEditor == "appearance") {
        AppearanceScreen(appearance, { appearance = it; it.save(context) }, { showAppearance = false })
    } else if (showEditor == "moods") {
        MoodSettings(moods, viewModel::saveMood, viewModel::deleteMood, viewModel::moodAsTag) { showMoods = false }
    } else if (showEditor == "share") {
        ShareScreen(diaries, shareDate ?: LocalDate.now().toString(), { shareDate = null })
    } else if (showEditor == "reader") {
        DayReader(diaries, readerDate ?: LocalDate.now().toString(), readerId,
            onDate = { readerDate = it; selectedDate = it; readerId = null },
            onBack = { readerDate = null; readerId = null },
            onEdit = { editing = it; editorOpen = true }, onSave = { viewModel.save(it) }, onShare = { shareDate = it })
    } else if (showEditor == "editor") {
        EditorScreen(
            initial = editing,
            customMoods = moods,
            knownTags = tags,
            onBack = { editorOpen = false; editing = null },
            onSave = { entry ->
                viewModel.save(entry)
                selectedDate = entry.date
                if (readerDate != null) readerDate = entry.date
                editorOpen = false
                editing = null
                snackbar.showSnackbar("已保存到本机")
            },
            onDelete = { entry ->
                viewModel.delete(entry)
                editorOpen = false
                editing = null
                snackbar.showSnackbar("日记已删除")
            }
        )
    } else {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(modifier = Modifier.glassEffect(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), navigation = true), containerColor = androidx.compose.ui.graphics.Color.Transparent, tonalElevation = 0.dp) {
                    MainTab.entries.forEach { item ->
                        NavigationBarItem(
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(indicatorColor = SageLight.copy(alpha = com.fuguang.localdiary.ui.theme.AppearanceStore.current.cardOpacity)),
                            selected = tab == item,
                            onClick = {
                                tab = item
                                if (item != MainTab.JOURNAL) {
                                    searchOpen = false
                                    query = ""
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            alwaysShowLabel = true
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == MainTab.JOURNAL) {
                    FloatingActionButton(
                        onClick = { editing = null; editorOpen = true },
                        containerColor = Sage,
                        contentColor = CardWhite,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.AutoStories, contentDescription = null)
                            Text("写日记", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 16 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 16 })
                },
                label = "tab-transition"
            ) { currentTab ->
                when (currentTab) {
                    MainTab.JOURNAL -> DateBoundary(selectedDate ?: LocalDate.now().toString(), diaries,
                        enabled = query.isBlank(), onDate = { selectedDate = it }, modifier = Modifier.padding(padding)) {
                    JournalScreen(
                        diaries = diaries,
                        query = query,
                        onQueryChange = { query = it },
                        searchOpen = searchOpen,
                        onSearchToggle = { val opening = !searchOpen; searchOpen = opening; if (!opening) query = "" },
                        selectedDate = if (query.isBlank()) selectedDate ?: LocalDate.now().toString() else null,
                        onClearDate = { selectedDate = LocalDate.now().toString() },
                        onEdit = { readerDate = it.date; readerId = it.id },
                        modifier = Modifier,
                        appearance = appearance,
                        dailyQuote = dailyQuote,
                        onQuoteInfo = { showQuoteInfo = true },
                        onJumpDate = { selectedDate = it }
                    )
                    }
                    MainTab.CALENDAR -> CalendarScreen(
                        diaries = diaries,
                        onSelectDate = { date -> selectedDate = date; readerDate = date; readerId = null },
                        selectedDate = selectedDate,
                        modifier = Modifier.padding(padding)
                    )
                    MainTab.FOOTPRINT -> FootprintScreen(
                        footprints = footprints,
                        locationEnabled = locationEnabled,
                        onLocationEnabledChange = {
                            locationEnabled = it
                            context.getSharedPreferences("fuguang_settings", Context.MODE_PRIVATE)
                                .edit().putBoolean("location_enabled", it).apply()
                        },
                        locationBusy = locationBusy,
                        locationMessage = locationMessage,
                        onRecordLocation = {
                            if (hasLocationPermission()) recordLocation()
                            else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        onDeleteFootprint = { date -> scope.launch { viewModel.deleteFootprint(date); snackbar.showSnackbar("已删除 $date 的足迹") } },
                        onClearAll = { showClearFootprints = true },
                        modifier = Modifier.padding(padding)
                    )
                    MainTab.MINE -> MineScreen(
                        diaries = diaries,
                        footprints = footprints,
                        onExport = { showExportWarning = true },
                        onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        onClearAll = { showClearAll = true },
                        onAbout = { showAbout = true },
                        onFootprints = { tab = MainTab.FOOTPRINT },
                        modifier = Modifier.padding(padding),
                        appearance = appearance,
                        onAppearance = { showAppearance = true },
                        onMoods = { showMoods = true },
                        onShare = { shareDate = selectedDate ?: LocalDate.now().toString() }
                    )
                }
            }
        }
    }
    }
}
}
