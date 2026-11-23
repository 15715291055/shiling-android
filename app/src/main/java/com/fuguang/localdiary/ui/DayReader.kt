package com.fuguang.localdiary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

@Composable
fun DayReader(
    diaries: List<DiaryEntity>, date: String, initialId: Long?=null, onDate:(String)->Unit,
    onBack:()->Unit, onEdit:(DiaryEntity)->Unit, onSave:suspend(DiaryEntity)->Unit, onShare:(String)->Unit
) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var original by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable(stateSaver=TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var exitAfterDiscard by remember { mutableStateOf(false) }
    var skipEmpty by rememberSaveable { mutableStateOf(false) }
    var jump by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val keyboard=LocalSoftwareKeyboardController.current
    val entries=remember(diaries,date) { diaries.filter { it.date==date }.sortedWith(compareBy<DiaryEntity>{it.time}.thenBy{it.id}) }
    val state=rememberLazyListState()
    LaunchedEffect(date,initialId) { state.scrollToItem(entries.indexOfFirst { it.id==initialId }.coerceAtLeast(0)) }
    fun finish() { editingId=null;keyboard?.hide();error=null }
    fun back() { if(saving) return; if(editingId!=null && draft.text!=original) { exitAfterDiscard=true;discard=true } else { finish();onBack() } }
    BackHandler { back() }
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("放弃未保存的修改？")},text={Text("原来的日记会保持不变。")},confirmButton={ TextButton(onClick={discard=false;finish();if(exitAfterDiscard)onBack();exitAfterDiscard=false}) { Text("放弃修改") } },dismissButton={TextButton(onClick={discard=false}) { Text("继续编辑") }})
    if(jump) DateJumpDialog(date,{jump=false}) { onDate(it);jump=false }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick={back()}) { Icon(Icons.Outlined.ArrowBack,"返回") }
            Column(Modifier.weight(1f).clickable(enabled=editingId==null){jump=true}.padding(8.dp)) {
                Text(date,color=TitleInk,fontFamily=FontFamily.Serif,fontSize=20.sp)
                Text(if(editingId==null) "${entries.size} 篇 · 双击正文即可修改" else "正在原位编辑",color=InkSoft,fontSize=11.sp)
            }
            if(editingId==null) {
                IconButton(onClick={onShare(date)},enabled=entries.isNotEmpty()) { Icon(Icons.Outlined.Share,"分享当天日记") }
            } else {
                TextButton(onClick={exitAfterDiscard=false;if(draft.text!=original)discard=true else finish()},enabled=!saving) { Text("取消") }
                TextButton(onClick={
                    val entry=entries.firstOrNull { it.id==editingId } ?: return@TextButton
                    if(draft.text.isBlank()) { error="正文不能为空";return@TextButton }
                    saving=true
                    scope.launch { try { onSave(entry.copy(body=draft.text,updatedAt=System.currentTimeMillis()));finish() } catch(e:Exception) {error="保存失败：${e.message}"} finally {saving=false} }
                },enabled=!saving) { Text(if(saving)"保存中" else "保存") }
            }
        }
        if(error!=null) Text(error!!,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(16.dp))
        if(editingId==null) Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick={onDate(LocalDate.now().toString())}) { Text("回到今天") }
            Spacer(Modifier.weight(1f))
            Text("跳过空白日期",fontSize=11.sp,color=InkSoft)
            Switch(checked=skipEmpty,onCheckedChange={skipEmpty=it},modifier=Modifier.padding(start=6.dp))
        }
        DateBoundary(date,diaries,enabled=editingId==null,skipEmpty=skipEmpty,onDate=onDate,modifier=Modifier.weight(1f)) {
            LazyColumn(state=state,modifier=Modifier.fillMaxSize().testTag("reader-scroll"),contentPadding=PaddingValues(horizontal=24.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(30.dp)) {
                if(entries.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical=60.dp)) { Text("这一天还没有记录",color=Ink,fontSize=21.sp,fontFamily=FontFamily.Serif);Text("下拉看前一天，上拉看后一天",color=InkSoft,modifier=Modifier.padding(top=12.dp)) } }
                items(entries,key={it.id}) { entry ->
                    Column(Modifier.fillMaxWidth().testTag("reading-${entry.id}"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Text(entry.time,color=DateInk,fontFamily=FontFamily.Serif,modifier=Modifier.weight(1f))
                            if(entry.mood.isNotBlank()) Text("${entry.moodIcon} ${entry.mood}",color=if(entry.moodColor==0L)InkSoft else readable(Color(entry.moodColor),Paper),fontSize=12.sp)
                            IconButton(onClick={onEdit(entry)},enabled=editingId==null) {Icon(Icons.Outlined.Edit,"编辑日期、心情和图片")}
                        }
                        EditableReadingBody(entry.body,editingId==entry.id,draft,{draft=it},onDoubleTap={offset ->
                            if(editingId==null) { original=entry.body;draft=TextFieldValue(entry.body,TextRange(offset));editingId=entry.id }
                        },tag="reader-body-${entry.id}")
                        val viewer=LocalPhotoViewer.current
                        entry.images().forEachIndexed { index,path ->
                            AsyncImage(File(path),"放大查看日记图片",contentScale=ContentScale.FillWidth,modifier=Modifier.fillMaxWidth().heightIn(min=120.dp,max=420.dp).clickable {viewer(entry.images(),index)})
                        }
                        if(entry.tags.isNotBlank()) Text(entry.tagList().joinToString("  "){"# $it"},color=InkSoft,fontSize=12.sp)
                        HorizontalDivider(color=Line)
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditableReadingBody(text:String,editing:Boolean,value:TextFieldValue,onChange:(TextFieldValue)->Unit,onDoubleTap:(Int)->Unit,tag:String) {
    val style=TextStyle(color=BodyInk,fontFamily=FontFamily.Serif,fontSize=17.sp,lineHeight=29.sp)
    val focus=remember { FocusRequester() }
    val bring=remember { BringIntoViewRequester() }
    val keyboard=LocalSoftwareKeyboardController.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density=LocalDensity.current
    var cursor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    if(editing) {
        BasicTextField(value=value,onValueChange=onChange,textStyle=style,cursorBrush=SolidColor(Sage),
            modifier=Modifier.fillMaxWidth().testTag(tag).focusRequester(focus).bringIntoViewRequester(bring),
            onTextLayout={ result -> cursor=result.getCursorRect(value.selection.end.coerceIn(0,value.text.length)) })
        LaunchedEffect(Unit) {focus.requestFocus();keyboard?.show()}
        val ime=WindowInsets.ime.getBottom(density)
        LaunchedEffect(cursor,ime) { cursor?.let { bring.bringIntoView(it) } }
    } else Text(text,style=style,onTextLayout={layout=it},modifier=Modifier.fillMaxWidth().testTag(tag).pointerInput(text) {detectTapGestures(onDoubleTap={ onDoubleTap(layout?.getOffsetForPosition(it) ?: 0) })})
}

@Composable
fun DateJumpDialog(current:String,onDismiss:()->Unit,onSelect:(String)->Unit) {
    var value by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("跳转到日期")},text={Column {OutlinedTextField(value,onValueChange={value=it;error=false},label={Text("年-月-日")},singleLine=true,isError=error);if(error)Text("请输入有效日期，例如 2026-11-23",color=MaterialTheme.colorScheme.error)}},confirmButton={TextButton(onClick={if(runCatching {LocalDate.parse(value)}.isSuccess)onSelect(value) else error=true}) {Text("跳转")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}
