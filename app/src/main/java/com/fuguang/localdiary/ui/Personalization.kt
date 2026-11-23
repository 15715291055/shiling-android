package com.fuguang.localdiary.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuguang.localdiary.data.MoodEntity
import com.fuguang.localdiary.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun SettingsPage(title:String,onBack:()->Unit,content:@Composable ColumnScope.()->Unit) {
    BackHandler(onBack=onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"返回")}
            Text(title,color=TitleInk,fontSize=23.sp,fontFamily=FontFamily.Serif)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
    }
}

@Composable
fun AppearanceScreen(appearance:JournalAppearance,onStyle:(JournalAppearance)->Unit,onBack:()->Unit) {
    val context=LocalContext.current
    val s=AppearanceStore.current
    val scope=rememberCoroutineScope()
    var error by remember {mutableStateOf<String?>(null)}
    var choosing by remember {mutableStateOf(false)}
    var reset by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    fun update(value:AppearanceSettings)=AppearanceStore.update(context,value)
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null) scope.launch {
            busy=true
            try {
                val path=withContext(Dispatchers.IO) {
                    val file=File(context.filesDir,"background-${UUID.randomUUID()}.jpg")
                    try {
                        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                        context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
                        require(bounds.outWidth>0 && bounds.outHeight>0){"无法读取这张图片"}
                        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2048)sample*=2
                        val bitmap=context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})} ?: error("无法读取图片")
                        file.outputStream().use{check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,92,it))};bitmap.recycle()
                        file.absolutePath
                    } catch(e:Exception){file.delete();throw e}
                }
                val previous=AppearanceStore.current.image
                update(AppearanceStore.current.copy(image=path))
                if(previous.isNotBlank()) File(previous).takeIf{it.parentFile==context.filesDir && it.name.startsWith("background-")}?.delete()
            } catch(e:Exception){error=e.message?:"背景添加失败"} finally{busy=false}
        }
    }
    if(choosing) ThemeChooser(appearance,onStyle,{choosing=false})
    if(reset) AlertDialog(onDismissRequest={reset=false},title={Text("恢复默认主题？")},text={Text("配色、背景和时间线样式将恢复默认。日记与图片不受影响。")},confirmButton={TextButton(onClick={
        val old=s.image;update(AppearanceSettings());onStyle(JournalAppearance.TIMELINE)
        if(old.isNotBlank()) File(old).takeIf{it.parentFile==context.filesDir&&it.name.startsWith("background-")}?.delete()
        reset=false
    }){Text("恢复默认")}},dismissButton={TextButton(onClick={reset=false}){Text("取消")}})
    SettingsPage("主题与背景",onBack) {
        Text("改动即时预览并自动保存。过浅的文字会自动调整到可读的深浅。",color=InkSoft,fontSize=12.sp)
        OutlinedButton(onClick={choosing=true},modifier=Modifier.fillMaxWidth()) { Text("时间线样式 · ${appearance.title}") }
        Surface(modifier=Modifier.glassEffect(MaterialTheme.shapes.large),color=Color.Transparent,shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("拾零 · 预览",color=TitleInk,fontSize=23.sp,fontFamily=FontFamily.Serif)
                Text("今天的小事",color=Ink)
                Text("17:38 · 心情平静",color=DateInk,fontSize=12.sp)
                Text("把沿途的片刻，慢慢写成自己的日子。",color=BodyInk,fontFamily=FontFamily.Serif)
                Text("只保存在这台设备",color=InkSoft,fontSize=11.sp)
                Button(onClick={}) {Text("主题强调色")}
            }
        }
        Text("背景",color=Ink,fontSize=18.sp)
        ColorSetting("背景颜色",s.background){update(s.copy(background=it))}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!busy){Text(if(busy)"正在处理" else "从相册选择背景")}
            TextButton(onClick={val old=s.image;update(s.copy(image=""));if(old.isNotBlank())File(old).takeIf{it.parentFile==context.filesDir&&it.name.startsWith("background-")}?.delete()}){Text("移除图片")}
        }
        Text("图片不透明度 ${(s.opacity*100).toInt()}%",color=InkSoft)
        Slider(value=s.opacity,onValueChange={update(s.copy(opacity=it))},valueRange=0f..1f,enabled=s.image.isNotBlank())
        Text("背景模糊 ${s.blur.toInt()}",color=InkSoft)
        Slider(value=s.blur,onValueChange={update(s.copy(blur=it))},valueRange=0f..25f,enabled=s.image.isNotBlank())
        Text("毛玻璃",color=Ink,fontSize=18.sp)
        Text("卡片透明度 ${((1-s.cardOpacity)*100).toInt()}%",color=InkSoft)
        Slider(value=1-s.cardOpacity,onValueChange={update(s.copy(cardOpacity=1-it))},valueRange=0f..1f,modifier=Modifier.testTag("glass-card-opacity"))
        Text("导航栏透明度 ${((1-s.navigationOpacity)*100).toInt()}%",color=InkSoft)
        Slider(value=1-s.navigationOpacity,onValueChange={update(s.copy(navigationOpacity=1-it))},valueRange=0f..1f,modifier=Modifier.testTag("glass-navigation-opacity"))
        Text("磨砂强度 ${s.glassBlur.toInt()}",color=InkSoft)
        Slider(value=s.glassBlur,onValueChange={update(s.copy(glassBlur=it))},valueRange=0f..30f,modifier=Modifier.testTag("glass-blur"))
        Text("背景图片本身的模糊与玻璃的磨砂分别调节。文字、图标保持清晰。",color=InkSoft,fontSize=12.sp)
        Text("颜色",color=Ink,fontSize=18.sp)
        ColorSetting("主题强调色",s.accent){update(s.copy(accent=it))}
        ColorSetting("页面主标题",s.title){update(s.copy(title=it))}
        ColorSetting("功能标题",s.section){update(s.copy(section=it))}
        ColorSetting("辅助说明",s.auxiliary){update(s.copy(auxiliary=it))}
        ColorSetting("日记正文",s.body){update(s.copy(body=it))}
        ColorSetting("日期与时间",s.date){update(s.copy(date=it))}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        TextButton(onClick={reset=true}){Text("恢复默认主题")}
    }
}

@Composable
fun ColorSetting(label:String,value:Long,onChange:(Long)->Unit) {
    var open by remember {mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().testTag("color-$label").clickable{open=true}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,color=Ink,modifier=Modifier.weight(1f))
        Surface(Modifier.size(30.dp),color=Color(value),shape=CircleShape,border=androidx.compose.foundation.BorderStroke(1.dp,Line)){}
    }
    if(open) ColorDialog(label,value,{open=false},onChange)
}

@Composable
private fun ColorDialog(label:String,value:Long,onDismiss:()->Unit,onChange:(Long)->Unit) {
    var hex by remember {mutableStateOf(String.format("%06X",value and 0xffffff))}
    val selected=colorPresets.flatMap{it.second}.firstOrNull{it.second==value}
    AlertDialog(onDismissRequest=onDismiss,title={Text(label)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("${selected?.first ?: "自定义颜色"} · #${String.format("%06X",value and 0xffffff)}",color=InkSoft,fontSize=12.sp)
        Column(Modifier.fillMaxWidth().heightIn(max=280.dp).verticalScroll(rememberScrollState()).testTag("color-presets"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            colorPresets.forEach{(group,colors)->
                Text(group,color=InkSoft,fontSize=11.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    colors.forEach{(name,color)->
                        Column(Modifier.weight(1f).selectable(selected=value==color,role=Role.RadioButton,onClick={hex=String.format("%06X",color and 0xffffff);onChange(color)})
                            .testTag("preset-$name").padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
                            Surface(Modifier.size(40.dp),color=Color(color),shape=CircleShape,border=androidx.compose.foundation.BorderStroke(if(value==color)2.dp else 1.dp,if(value==color)Sage else Line)){
                                if(value==color) Box(contentAlignment=Alignment.Center){Icon(Icons.Outlined.Check,"已选择$name",tint=readable(Color.White,Color(color)),modifier=Modifier.size(19.dp))}
                            }
                            Text(name,color=Ink,fontSize=10.sp,maxLines=1)
                        }
                    }
                }
            }
        }
        OutlinedTextField(hex,onValueChange={input->hex=input.removePrefix("#").take(6);if(hex.matches(Regex("[0-9a-fA-F]{6}")))onChange(0xff000000L or hex.toLong(16))},label={Text("自定义色值 #RRGGBB")},singleLine=true)
    }},confirmButton={TextButton(onClick=onDismiss){Text("完成")}})
}

private val colorPresets:List<Pair<String,List<Pair<String,Long>>>> = listOf(
    "莫兰迪 · 灰粉" to listOf("烟粉" to 0xFFBE9C9B,"灰藕" to 0xFFA58B94,"豆沙" to 0xFFB28F8C,"玫瑰灰" to 0xFF9E8588),
    "莫兰迪 · 灰绿" to listOf("灰绿" to 0xFF9BA89A,"鼠尾草灰" to 0xFF8F9B8D,"雾青" to 0xFF8EA4A0,"橄榄" to 0xFF9C9D86),
    "莫兰迪 · 蓝紫" to listOf("雾霾蓝" to 0xFF91A1AE,"灰蓝" to 0xFF8A99A6,"灰紫" to 0xFFA29AAE,"藕紫" to 0xFFA293A5),
    "莫兰迪 · 奶咖" to listOf("奶咖" to 0xFFB3A596,"燕麦" to 0xFFC1B5A4,"砂岩" to 0xFFAAA296,"暖灰" to 0xFFA7A29A),
    "自然绿" to listOf("鼠尾草" to 0xFF71815A,"苔绿" to 0xFF4F765C,"青竹" to 0xFF3B8B7C,"薄荷" to 0xFF86A98C),
    "海天蓝" to listOf("雾蓝" to 0xFF638DAD,"海盐蓝" to 0xFF4F83AD,"湖蓝" to 0xFF397FA0,"靛青" to 0xFF486594),
    "花园紫" to listOf("雾紫" to 0xFF9B88BA,"鸢尾" to 0xFF7D69A6,"藤萝" to 0xFF9472A5,"蓝紫" to 0xFF6966A8),
    "玫瑰粉" to listOf("藕粉" to 0xFFBC8595,"玫瑰" to 0xFFB76583,"珊瑚粉" to 0xFFC37378,"莓果" to 0xFFA64F6F),
    "暖阳橙" to listOf("蜂蜜" to 0xFFBB8D55,"金杏" to 0xFFD8A24D,"杏橙" to 0xFFCA875E,"朱砂" to 0xFFC96B4E),
    "大地棕" to listOf("焦糖" to 0xFFA47C5B,"拿铁" to 0xFF8B6F57,"陶棕" to 0xFF91655A,"可可" to 0xFF73564C),
    "安静灰" to listOf("石墨" to 0xFF263429,"岩灰" to 0xFF59646B,"雾灰" to 0xFF7E8586,"橄榄灰" to 0xFF8E927D),
    "柔浅色" to listOf("浅苔" to 0xFFC2CEC0,"浅薰衣草" to 0xFFC9BEDC,"月白" to 0xFFF7F5EF,"暖白" to 0xFFFCFAF5)
)

@Composable
fun MoodSettings(moods:List<MoodEntity>,onSave:suspend(MoodEntity)->Unit,onDelete:suspend(String)->Unit,onTag:suspend(MoodEntity)->Unit,onBack:()->Unit) {
    var editing by remember {mutableStateOf<MoodEntity?>(null)}
    var deleting by remember {mutableStateOf<MoodEntity?>(null)}
    var message by remember {mutableStateOf("")}
    val scope=rememberCoroutineScope()
    SettingsPage("心情管理",onBack) {
        Text("删除或改名不会改变旧日记中的心情记录。",color=InkSoft,fontSize=12.sp)
        Button(onClick={editing=MoodEntity(UUID.randomUUID().toString(),"","🍃",0xFF71815A)}){Text("新增心情")}
        if(message.isNotBlank()) Text(message,color=InkSoft)
        moods.forEach{mood->Surface(modifier=Modifier.glassEffect(MaterialTheme.shapes.large),color=Color.Transparent,shape=MaterialTheme.shapes.large){Column(Modifier.fillMaxWidth().padding(12.dp)){
            Text("${mood.icon} ${mood.name}",color=readable(Color(mood.color),CardWhite),fontSize=18.sp)
            Row { TextButton(onClick={editing=mood}){Text("修改")};TextButton(onClick={scope.launch{try{onTag(mood);message="已添加标签：${mood.name}"}catch(e:Exception){message="添加失败"}}}){Text("存为标签")};TextButton(onClick={deleting=mood}){Text("删除")} }
        }}}
    }
    deleting?.let{mood->AlertDialog(onDismissRequest={deleting=null},title={Text("删除“${mood.name}”？")},text={Text("旧日记的名称、图标和颜色会保留。")},confirmButton={TextButton(onClick={scope.launch{try{onDelete(mood.id);deleting=null}catch(e:Exception){message="删除失败"}}}){Text("删除")}},dismissButton={TextButton(onClick={deleting=null}){Text("取消")}})}
    editing?.let { mood ->
        var name by remember(mood.id){mutableStateOf(mood.name)}
        var icon by remember(mood.id){mutableStateOf(mood.icon)}
        var color by remember(mood.id){mutableLongStateOf(mood.color)}
        var busy by remember {mutableStateOf(false)}
        AlertDialog(onDismissRequest={if(!busy)editing=null},title={Text("自定义心情")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(name,{name=it.take(20)},label={Text("心情名称")},singleLine=true)
            OutlinedTextField(icon,{icon=it.take(12)},label={Text("图标 · 输入表情或符号")},singleLine=true)
            ColorSetting("心情颜色",color){color=it}
        }},confirmButton={TextButton(enabled=name.isNotBlank()&&icon.isNotBlank()&&!busy,onClick={busy=true;scope.launch{try{onSave(mood.copy(name=name.trim(),icon=icon,color=color));editing=null}catch(e:Exception){message="保存失败"}finally{busy=false}}}){Text("保存")}},dismissButton={TextButton(enabled=!busy,onClick={editing=null}){Text("取消")}})
    }
}
