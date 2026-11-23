package com.fuguang.localdiary.ui

import android.preference.PreferenceManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.fuguang.localdiary.ui.GlassCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.data.FootprintEntity
import com.fuguang.localdiary.ui.theme.CardWhite
import com.fuguang.localdiary.ui.theme.Clay
import com.fuguang.localdiary.ui.theme.Ink
import com.fuguang.localdiary.ui.theme.InkSoft
import com.fuguang.localdiary.ui.theme.Line
import com.fuguang.localdiary.ui.theme.Mist
import com.fuguang.localdiary.ui.theme.PaperLight
import com.fuguang.localdiary.ui.theme.Sage
import com.fuguang.localdiary.ui.theme.SageLight
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun JournalScreen(
    diaries: List<DiaryEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    searchOpen: Boolean,
    onSearchToggle: () -> Unit,
    selectedDate: String?,
    onClearDate: () -> Unit,
    onEdit: (DiaryEntity) -> Unit,
    modifier: Modifier = Modifier,
    appearance: com.fuguang.localdiary.ui.theme.JournalAppearance = com.fuguang.localdiary.ui.theme.JournalAppearance.TIMELINE,
    dailyQuote: com.fuguang.localdiary.data.DailyQuote? = null,
    onQuoteInfo: () -> Unit = {},
    onJumpDate: (String) -> Unit = {}
) {
    var showDateJump by remember { mutableStateOf(false) }
    if (showDateJump) DateJumpDialog(selectedDate ?: LocalDate.now().toString(), { showDateJump = false }) { onJumpDate(it); showDateJump = false }
    val filtered = remember(diaries, query, selectedDate) {
        diaries.filter { item ->
            (selectedDate == null || item.date == selectedDate) &&
                (query.isBlank() || item.body.contains(query, true) || item.tags.contains(query, true) || item.date.contains(query, true) || item.time.contains(query, true))
        }.sortedWith(compareBy<DiaryEntity> { it.date }.thenBy { it.time }.thenBy { it.id })
    }
    val journalState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(selectedDate) { journalState.scrollToItem(0) }
    LazyColumn(
        state = journalState,
        modifier = modifier.fillMaxSize().testTag("journal-scroll").semantics { stateDescription = appearance.title },
        contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp)
    ) {
        item(key = "journal-header") {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("拾零", fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        fontSize = 32.sp, fontWeight = FontWeight.Medium, color = com.fuguang.localdiary.ui.theme.TitleInk)
                    Box(Modifier.padding(top = 6.dp).width(25.dp).height(2.dp).background(Sage))
                    if (dailyQuote != null) {
                        Column(Modifier.padding(top = 11.dp).clickable(onClick = onQuoteInfo).testTag("daily-quote")) {
                            Text(dailyQuote.text, fontSize = 12.sp, lineHeight = 19.sp, color = InkSoft,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text("— ${dailyQuote.author}", fontSize = 10.sp, color = Sage, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
                IconButton(onClick = onSearchToggle, modifier = Modifier.size(46.dp)) {
                    Icon(if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search, contentDescription = "搜索日记", tint = Ink)
                }
            }
        }
        if (searchOpen) {
            item(key = "journal-search") {
                OutlinedTextField(value = query, onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
                    placeholder = { Text("搜索正文、日期或标签") }, singleLine = true, shape = RoundedCornerShape(13.dp))
            }
        }
        if (selectedDate != null) {
            item(key = "date-filter") {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).glassEffect(RoundedCornerShape(50), tint = SageLight)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedDate, color = com.fuguang.localdiary.ui.theme.DateInk, fontSize = 12.sp, modifier = Modifier.clickable { showDateJump = true })
                    IconButton(onClick = onClearDate, modifier = Modifier.size(28.dp)) {
                        Text("今天", color = Sage, fontSize = 11.sp)
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item(key = "journal-empty") { EmptyJournalState(query.isNotBlank(), selectedDate != null) }
        } else {
            filtered.groupBy { it.date.take(7) }.forEach { (month, entries) ->
                item(key = "month-$month") {
                    val label = runCatching { YearMonth.parse(month).format(DateTimeFormatter.ofPattern("yyyy年M月")) }.getOrDefault(month)
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(label + " ▾", fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 20.sp, color = com.fuguang.localdiary.ui.theme.DateInk, modifier = Modifier.clickable { showDateJump = true })
                        Box(Modifier.padding(top = 13.dp).fillMaxWidth().height(.6.dp).background(Line))
                    }
                }
                entries.forEachIndexed { index, entry ->
                    item(key = entry.id) {
                        JournalEntry(entry, appearance, index == 0 || entries[index - 1].date != entry.date) { onEdit(entry) }
                    }
                }
            }
        }
    }
}
@Composable
private fun EmptyJournalState(isSearch: Boolean, isFiltered: Boolean) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(62.dp).clip(CircleShape).background(SageLight), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoStories, contentDescription = null, tint = Sage, modifier = Modifier.size(27.dp))
                }
                Text(
                    when {
                        isSearch -> "没有找到这段记忆"
                        isFiltered -> "这一天还没有记录"
                        else -> "今天还没有记录"
                    },
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )
                Text(
                    when {
                        isSearch -> "试试其他关键词，或换一个日期。"
                        isFiltered -> "也许可以从那天的一件小事开始回想。"
                        else -> "写下第一句话，把此刻留在这里。"
                    },
                    color = InkSoft,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun DiaryCard(entry: DiaryEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.date, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.width(9.dp))
                Text(entry.time, color = InkSoft, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                if (entry.mood.isNotBlank()) {
                    Surface(color = SageLight, shape = RoundedCornerShape(50)) {
                        Text(entry.mood, color = Sage, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }
            Text(entry.body, color = Ink, fontSize = 15.sp, lineHeight = 24.sp)
            if (entry.images().isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    entry.images().take(3).forEach { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "日记图片",
                            modifier = Modifier.size(92.dp).clip(RoundedCornerShape(13.dp))
                        )
                    }
                    if (entry.images().size > 3) {
                        Box(Modifier.size(92.dp).clip(RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                            Text("+${entry.images().size - 3}", color = InkSoft, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (entry.tagList().isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    entry.tagList().take(5).forEach { tag ->
                        Surface(color = Mist, shape = RoundedCornerShape(7.dp)) {
                            Text("# $tag", color = InkSoft, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(diaries: List<DiaryEntity>, onSelectDate: (String) -> Unit, modifier: Modifier = Modifier, selectedDate: String? = null) {
    var month by remember(selectedDate) { mutableStateOf(selectedDate?.let { YearMonth.from(LocalDate.parse(it)) } ?: YearMonth.now()) }
    var selected by remember(selectedDate) { mutableStateOf(selectedDate ?: LocalDate.now().toString()) }
    val diaryDates = remember(diaries) { diaries.map { it.date }.toSet() }
    val today = LocalDate.now()
    val monthDays = remember(month) {
        val firstOffset = month.atDay(1).dayOfWeek.value % 7
        List<LocalDate?>(firstOffset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    }
    val todayEntries = diaries.filter { it.date == today.toString() }
    val todayWords = todayEntries.sumOf { it.body.count { char -> !char.isWhitespace() } }
    val todayPhotos = todayEntries.sumOf { it.images().size }
    val totalDays = diaries.map { it.date }.distinct().size

    Column(modifier.fillMaxSize().testTag("calendar-scroll").verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("回忆", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = com.fuguang.localdiary.ui.theme.TitleInk, modifier = Modifier.padding(top = 25.dp))
        Text("在日历里，重新遇见那些日子", color = InkSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), border = BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${month.year}年${month.monthValue}月", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { month = month.minusMonths(1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月") }
                    IconButton(onClick = { month = month.plusMonths(1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.ChevronRight, contentDescription = "下个月") }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEach { Text(it, color = InkSoft, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
                Spacer(Modifier.height(7.dp))
                Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    monthDays.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                        (week + List<LocalDate?>(7 - week.size) { null }).forEach { date ->
                        if (date == null) {
                            Box(Modifier.weight(1f).padding(3.dp).aspectRatio(1f))
                        } else {
                            val isToday = date == today
                            val hasEntry = date.toString() in diaryDates
                            val isSelected = date.toString() == selected
                            Column(
                                modifier = Modifier.weight(1f).padding(3.dp).aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) SageLight else Color.Transparent)
                                    .pressScaleClickable(onClick = { selected = date.toString(); onSelectDate(date.toString()) }),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    color = if (isToday) Sage else com.fuguang.localdiary.ui.theme.DateInk,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(3.dp))
                                Box(Modifier.size(5.dp).clip(CircleShape).background(if (hasEntry) Clay else Color.Transparent))
                            }
                        }
                        }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Clay))
                    Text(" 有日记", color = InkSoft, fontSize = 11.sp, modifier = Modifier.padding(start = 5.dp))
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Sage))
                    Text(" 今天", color = InkSoft, fontSize = 11.sp, modifier = Modifier.padding(start = 5.dp))
                }
            }
        }
        Text("今天", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MetricCard("记录", todayEntries.size.toString(), "篇", Modifier.weight(1f))
            MetricCard("字数", todayWords.toString(), "字", Modifier.weight(1f))
            MetricCard("图片", todayPhotos.toString(), "张", Modifier.weight(1f))
        }
        Text("累计记录", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), border = BorderStroke(1.dp, Line)) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("记录过的日子", color = InkSoft, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("$totalDays 天", color = Sage, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 13.dp)) {
            Text(title, color = InkSoft, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = Sage, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(unit, color = InkSoft, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
            }
        }
    }
}

@Composable
fun FootprintScreen(
    footprints: List<FootprintEntity>,
    locationEnabled: Boolean,
    onLocationEnabledChange: (Boolean) -> Unit,
    locationBusy: Boolean,
    locationMessage: String?,
    onRecordLocation: () -> Unit,
    onDeleteFootprint: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("footprint-scroll"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item(key = "header") {
            Column {
                Text("足迹", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = com.fuguang.localdiary.ui.theme.TitleInk, modifier = Modifier.padding(start = 22.dp, top = 25.dp))
                Text("只记录你愿意留下的地点", color = InkSoft, fontSize = 12.sp, modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 15.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("每日位置记录", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("不会持续后台追踪", color = InkSoft, fontSize = 11.sp)
                    }
                    Switch(checked = locationEnabled, onCheckedChange = onLocationEnabledChange)
                }
                Button(
                    onClick = onRecordLocation,
                    enabled = locationEnabled && !locationBusy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sage)
                ) {
                    if (locationBusy) CircularProgressIndicator(Modifier.size(17.dp), color = CardWhite, strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (locationBusy) "正在定位…" else "记录今日位置", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                }
                if (!locationMessage.isNullOrBlank()) {
                    Text(locationMessage, color = InkSoft, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
                }
            }
        }
        if (footprints.isEmpty()) {
            item(key = "empty") {
                Card(modifier = Modifier.fillMaxWidth().padding(22.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), border = BorderStroke(1.dp, Line)) {
                    Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Place, contentDescription = null, tint = Sage, modifier = Modifier.size(34.dp))
                        Text("还没有留下足迹", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                        Text("需要时点一下“记录今日位置”，位置只会保存在本机。", color = InkSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        } else {
            item(key = "map") {
                Column {
                    MapCard(footprints = footprints, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(250.dp))
                    Text("© OpenStreetMap contributors · 地图需联网；足迹坐标仅保存在本机。", color = InkSoft, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 23.dp, vertical = 5.dp))
                }
            }
            item(key = "history") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("历史足迹", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearAll) { Text("清除全部", color = Clay, fontSize = 12.sp) }
                }
            }
            items(footprints, key = { it.date }) { item ->
                Card(modifier = Modifier.padding(horizontal = 20.dp), shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), border = BorderStroke(1.dp, Line)) {
                    Row(Modifier.fillMaxWidth().padding(start = 15.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.date, color = com.fuguang.localdiary.ui.theme.DateInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${item.time} · ${"%.5f".format(Locale.US, item.latitude)}, ${"%.5f".format(Locale.US, item.longitude)}", color = InkSoft, fontSize = 11.sp)
                            Text("定位精度约 ${item.accuracyMeters.toInt()} 米", color = InkSoft, fontSize = 10.sp)
                        }
                        IconButton(onClick = { onDeleteFootprint(item.date) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除足迹", tint = InkSoft)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapCard(footprints: List<FootprintEntity>, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    AndroidView(
        modifier = modifier.clip(RoundedCornerShape(22.dp)),
        factory = { ctx ->
            Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
            val tileDirectory = java.io.File(ctx.filesDir, "osmdroid").apply { mkdirs() }
            Configuration.getInstance().osmdroidBasePath = tileDirectory
            Configuration.getInstance().osmdroidTileCache = java.io.File(tileDirectory, "tiles").apply { mkdirs() }
            Configuration.getInstance().userAgentValue = ctx.packageName
            val view = MapView(ctx)
            view.setTileSource(TileSourceFactory.MAPNIK)
            view.setMultiTouchControls(true)
            view.controller.setZoom(11.5)
            mapViewState.value = view
            view
        },
        update = { map ->
            map.overlays.clear()
            footprints.forEach { item ->
                val marker = Marker(map).apply {
                    position = GeoPoint(item.latitude, item.longitude)
                    title = item.date
                    snippet = "${item.time} · 精度约 ${item.accuracyMeters.toInt()} 米"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(marker)
            }
            footprints.firstOrNull()?.let { latest ->
                map.controller.setCenter(GeoPoint(latest.latitude, latest.longitude))
            }
            map.invalidate()
        }
    )
    val mapView = mapViewState.value
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
        }
    }
}

@Composable
fun MineScreen(
    diaries: List<DiaryEntity>,
    footprints: List<FootprintEntity>,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearAll: () -> Unit,
    onAbout: () -> Unit,
    onFootprints: () -> Unit,
    modifier: Modifier = Modifier,
    appearance: com.fuguang.localdiary.ui.theme.JournalAppearance = com.fuguang.localdiary.ui.theme.JournalAppearance.TIMELINE,
    onAppearance: () -> Unit = {},
    onMoods: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    val totalWords = diaries.sumOf { it.body.count { char -> !char.isWhitespace() } }
    val totalDays = diaries.map { it.date }.distinct().size
    Column(modifier.fillMaxSize().testTag("mine-scroll").verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text("我的", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = com.fuguang.localdiary.ui.theme.TitleInk, modifier = Modifier.padding(start = 22.dp, top = 25.dp))
        Text("所有记录，都留在自己的设备里", color = InkSoft, fontSize = 12.sp, modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 18.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, Line)
        ) {
            Row(Modifier.fillMaxWidth().padding(17.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                ProfileMetric("记录天数", "$totalDays")
                ProfileMetric("日记篇数", "${diaries.size}")
                ProfileMetric("累计字数", "$totalWords")
            }
        }
        Text("外观", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 9.dp))
        Column(Modifier.padding(horizontal = 20.dp).glassEffect(RoundedCornerShape(20.dp))) {
            SettingRow(Icons.Outlined.AutoStories, "主题外观", appearance.title + " · 背景与配色", onAppearance)
            DividerLine()
            SettingRow(Icons.Outlined.AutoStories, "自定义心情", "图标、名称、颜色与标签", onMoods)
            DividerLine()
            SettingRow(Icons.Outlined.AutoStories, "每日分享", "选择日记，生成本地长图", onShare)
        }
        Text("本地数据管理", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 9.dp))
        Column(Modifier.padding(horizontal = 20.dp).glassEffect(RoundedCornerShape(20.dp))) {
            SettingRow(Icons.Outlined.Download, "导出备份", "日记、图片与足迹打包为 ZIP", onExport)
            DividerLine()
            SettingRow(Icons.Outlined.Upload, "导入备份", "合并导入，不覆盖已有记录", onImport)
            DividerLine()
            SettingRow(Icons.Outlined.Place, "足迹数据", "当前保存 ${footprints.size} 条位置记录", onFootprints)
            DividerLine()
            SettingRow(Icons.Outlined.Info, "应用说明", "本地保存与隐私说明", onAbout)
            DividerLine()
            SettingRow(Icons.Outlined.DeleteOutline, "清空全部数据", "删除本机的日记、图片和足迹", onClearAll, danger = true)
        }
        Spacer(Modifier.height(14.dp))
        Text("卸载应用或清除应用数据可能导致记录丢失。更换手机前，请先导出备份。足迹属于敏感数据，导出前请确认备份文件的保存位置。",
            color = InkSoft, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 23.dp))
    }
}

@Composable
private fun ProfileMetric(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Sage, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(title, color = InkSoft, fontSize = 10.sp)
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().pressScaleClickable(onClick).padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(if (danger) Color(0xFFF5E9E4) else SageLight), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (danger) Clay else Sage, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(title, color = if (danger) Clay else Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = InkSoft, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = InkSoft, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DividerLine() {
    Box(Modifier.fillMaxWidth().padding(start = 62.dp, end = 15.dp).height(1.dp).background(Line))
}
