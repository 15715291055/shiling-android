package com.fuguang.localdiary.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.ui.theme.CardWhite
import com.fuguang.localdiary.ui.theme.Clay
import com.fuguang.localdiary.ui.theme.Ink
import com.fuguang.localdiary.ui.theme.InkSoft
import com.fuguang.localdiary.ui.theme.Line
import com.fuguang.localdiary.ui.theme.Mist
import com.fuguang.localdiary.ui.theme.Paper
import com.fuguang.localdiary.ui.theme.Sage
import com.fuguang.localdiary.ui.theme.SageLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID



@Composable
fun EditorScreen(
    initial: DiaryEntity?,
    onBack: () -> Unit,
    onSave: suspend (DiaryEntity) -> Unit,
    onDelete: suspend (DiaryEntity) -> Unit,
    customMoods: List<com.fuguang.localdiary.data.MoodEntity> = emptyList(),
    knownTags: List<com.fuguang.localdiary.data.TagEntity> = emptyList()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var date by rememberSaveable(initial?.id) { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var time by rememberSaveable(initial?.id) { mutableStateOf(initial?.time ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var body by rememberSaveable(initial?.id) { mutableStateOf(initial?.body ?: "") }
    var tags by rememberSaveable(initial?.id) { mutableStateOf(initial?.tags ?: "") }
    var mood by rememberSaveable(initial?.id) { mutableStateOf(initial?.mood ?: "") }
    var moodId by rememberSaveable(initial?.id) { mutableStateOf(initial?.moodId ?: "") }
    var moodIcon by rememberSaveable(initial?.id) { mutableStateOf(initial?.moodIcon ?: "") }
    var moodColor by rememberSaveable(initial?.id) { mutableStateOf(initial?.moodColor ?: 0L) }
    val viewer = LocalPhotoViewer.current
    val images = rememberSaveable(initial?.id, saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) {
        initial?.images().orEmpty().toMutableStateList()
    }
    var saving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val dirty = initial == null && (body.isNotBlank() || tags.isNotBlank() || mood.isNotBlank() || images.isNotEmpty()) ||
        initial != null && (body != initial.body || date != initial.date || time != initial.time || tags != initial.tags || mood != initial.mood || images.toList() != initial.images())

    fun discardAndClose() {
        val originalImages = initial?.images().orEmpty()
        images.toList().filterNot { it in originalImages }.forEach { File(it).delete() }
        onBack()
    }

    fun requestBack() {
        if (dirty) showDiscard = true else discardAndClose()
    }

    BackHandler(enabled = true) { requestBack() }

    LaunchedEffect(initial?.id) {
        if (initial == null) focusRequester.requestFocus()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris: List<Uri> ->
        if (uris.isNotEmpty()) scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    val directory = File(context.filesDir, "diary-images").apply { mkdirs() }
                    uris.mapNotNull { uri ->
                        runCatching {
                            val extension = when (context.contentResolver.getType(uri)) {
                                "image/png" -> ".png"
                                "image/webp" -> ".webp"
                                "image/gif" -> ".gif"
                                else -> ".jpg"
                            }
                            val file = File(directory, "${UUID.randomUUID()}$extension")
                            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                            file.absolutePath.takeIf { file.exists() && file.length() > 0 }
                        }.getOrNull()
                    }
                }
                val remaining = (12 - images.size).coerceAtLeast(0)
                saved.drop(remaining).forEach { File(it).delete() }
                images.addAll(saved.take(remaining))
            } catch (error: Exception) {
                errorText = "图片添加失败：${error.message ?: "请检查图片访问权限"}"
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("放弃这次编辑？") },
            text = { Text("还有内容没有保存，离开后这些修改不会保留。") },
            confirmButton = { TextButton(onClick = { showDiscard = false; discardAndClose() }) { Text("放弃修改", color = Clay) } },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("继续编辑") } }
        )
    }
    if (showDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这篇日记？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    scope.launch { onDelete(initial) }
                }) { Text("删除", color = Clay) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { requestBack() }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = Ink)
            }
            Column(Modifier.weight(1f).padding(start = 5.dp)) {
                Text(if (initial == null) "写下此刻" else "编辑日记", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = com.fuguang.localdiary.ui.theme.TitleInk)
                Text("只保存在这台设备", fontSize = 10.sp, color = InkSoft)
            }
            if (initial != null) {
                IconButton(onClick = { showDelete = true }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除日记", tint = Clay)
                }
            }
            Button(
                onClick = {
                    if (saving) return@Button
                    if (body.isBlank()) {
                        errorText = "先写下一点内容，再保存这篇日记。"
                        return@Button
                    }
                    if (runCatching { LocalDate.parse(date); LocalTime.parse(time) }.isFailure) {
                        errorText = "请填写有效的日期和时间，例如 2026-11-22、09:30。"
                        return@Button
                    }
                    saving = true
                    errorText = null
                    val entry = if (initial == null) {
                        DiaryEntity(date = date, time = time, body = body.trim(), tags = tags, mood = mood, moodId = moodId, moodIcon = moodIcon, moodColor = moodColor, imagePaths = images.joinToString("\n"))
                    } else {
                        initial.copy(date = date, time = time, body = body.trim(), tags = tags, mood = mood, moodId = moodId, moodIcon = moodIcon, moodColor = moodColor, imagePaths = images.joinToString("\n"), updatedAt = System.currentTimeMillis())
                    }
                    scope.launch {
                        try {
                            onSave(entry)
                        } catch (error: Exception) {
                            saving = false
                            errorText = "保存失败：${error.message ?: "请检查存储空间后重试"}"
                        }
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 9.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sage)
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(15.dp), color = CardWhite, strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(if (saving) "保存中" else "保存", modifier = Modifier.padding(start = 6.dp), fontSize = 13.sp)
            }
        }

        Column(
            Modifier.weight(1f).testTag("editor-scroll").verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            if (!errorText.isNullOrBlank()) {
                Text(errorText!!, color = Clay, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("日期") }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = com.fuguang.localdiary.ui.theme.DateInk), modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("时间") }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = com.fuguang.localdiary.ui.theme.DateInk), modifier = Modifier.width(116.dp), singleLine = true, shape = RoundedCornerShape(14.dp))
            }
            Box {
                androidx.compose.foundation.text.BasicTextField(
                    value = body,
                    onValueChange = { body = it; errorText = null },
                    modifier = Modifier.fillMaxWidth().testTag("editor-body").heightIn(min = 180.dp).padding(vertical = 10.dp).focusRequester(focusRequester),
                    decorationBox = { field -> Box { if (body.isEmpty()) Text("今天发生了什么？\n\n不必写得完整，从一句话开始就好。", color = InkSoft); field() } },
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Sage),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = com.fuguang.localdiary.ui.theme.BodyInk, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 17.sp, lineHeight = 29.sp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("此刻的心情", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    customMoods.forEach { item ->
                        FilterChip(selected = mood == item.name, onClick = {
                            if (mood == item.name) { mood = ""; moodId = ""; moodIcon = ""; moodColor = 0L }
                            else { mood = item.name; moodId = item.id; moodIcon = item.icon; moodColor = item.color }
                        }, label = { Text("${item.icon} ${item.name}", fontSize = 12.sp) })
                    }
                }
            }
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标签") },
                placeholder = { Text("用逗号分隔，例如：生活, 朋友, 秋天") },
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
            if (knownTags.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                knownTags.forEach { tag -> androidx.compose.material3.AssistChip(onClick = { tags = (tags.split(",").map { it.trim() }.filter { it.isNotBlank() } + tag.name).distinct().joinToString(",") }, label = { Text("# ${tag.name}") }) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("图片", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${images.size}/12", color = InkSoft, fontSize = 11.sp)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    images.forEach { path ->
                        Box {
                            AsyncImage(model = File(path), contentDescription = "日记图片", modifier = Modifier.size(100.dp).clip(RoundedCornerShape(14.dp)).clickable { viewer(images.toList(), images.indexOf(path)) })
                            IconButton(onClick = {
                                images.remove(path)
                                if (path !in initial?.images().orEmpty()) File(path).delete()
                            }, modifier = Modifier.align(Alignment.TopEnd).size(27.dp).background(Color.Black.copy(alpha = .48f), CircleShape)) {
                                Icon(Icons.Outlined.Close, contentDescription = "移除图片", tint = CardWhite, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    if (images.size < 12) {
                        Surface(
                            modifier = Modifier.size(100.dp).clickable { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            color = Mist,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = Sage, modifier = Modifier.size(25.dp))
                                Text("添加图片", color = InkSoft, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                }
            }
            Text("保存后，内容和图片都会留在本机。", color = InkSoft, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
        }
    }
}
