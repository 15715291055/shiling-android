package com.fuguang.localdiary.ui

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

enum class ShareTemplate(val title:String,val description:String,val editorial:Boolean=false) {
    POETRY("纸页诗意","撕纸照片 · 大日期 · 轻盈时间线",true),
    EDITORIAL("生活杂志","照片主视觉 · 图文分栏 · 复古刊头",true),
    MEMORY("胶片记忆","深色纸感 · 错落叠照 · 奶油文字",true),
    FROST("雾色书信","照片雾化背景 · 半透信笺 · 双图排版",true),
    PAPER("纸页手账","暖纸纹理 · 书页感"), MINIMAL("极简排版","清爽留白 · 适合长文"), FILM("胶片拼贴","深色胶片 · 双照片拼贴"),
    LITERARY("文学日签","大日期 · 衬线文字 · 文艺留白"), MAGAZINE("经典杂志","杂志刊头 · 照片在前 · 图文叙事"),
    NOTE("便笺卡片","淡紫底色 · 白色便笺 · 轻盈简洁"), MOOD("心情小报","柔和暖色 · 心情大图标"), POSTCARD("旅行明信片","邮戳日期 · 蓝色边框 · 照片先行")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShareScreen(diaries:List<DiaryEntity>,initialDate:String,onBack:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var date by rememberSaveable{mutableStateOf(initialDate)}
    val entries=remember(diaries,date){diaries.filter{it.date==date}.sortedWith(compareBy<DiaryEntity>{it.time}.thenBy{it.id})}
    var ids by rememberSaveable(date){mutableStateOf(arrayListOf<Long>())}
    var initialized by rememberSaveable(date){mutableStateOf(false)}
    LaunchedEffect(entries.map{it.id}) { if(!initialized && entries.isNotEmpty()){ids=ArrayList(entries.map{it.id});initialized=true} }
    var template by rememberSaveable{mutableStateOf(ShareTemplate.POETRY)}
    var classicStyles by rememberSaveable { mutableStateOf(false) }
    var font by rememberSaveable{mutableFloatStateOf(36f)}
    var spacing by rememberSaveable{mutableFloatStateOf(1.6f)}
    var files by remember {mutableStateOf<List<File>>(emptyList())}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var jump by remember{mutableStateOf(false)}
    var previewIndex by remember { mutableIntStateOf(0) }
    var previewError by remember(files) { mutableStateOf(false) }
    val viewer=LocalPhotoViewer.current
    val previewBring=remember { BringIntoViewRequester() }
    LaunchedEffect(files) { if(files.isNotEmpty()){withFrameNanos{};previewBring.bringIntoView()} }
    fun saveImages() { scope.launch {busy=true;try{withContext(Dispatchers.IO){ShareImages.save(context,files)};message="已保存 ${files.size} 张图片到相册"}catch(e:Exception){message="保存失败：${e.message}"}finally{busy=false}} }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)saveImages() else message="没有相册写入权限，仍可使用系统分享。"}
    if(jump) DateJumpDialog(date,{jump=false}){date=it;files=emptyList();jump=false}
    LaunchedEffect(ids.toList(),template,font,spacing,date){files=emptyList()}
    SettingsPage("每日分享",onBack) {
        TextButton(onClick={jump=true},enabled=!busy){Text("$date ▾")}
        Text("选择需要分享的记录，全文会完整排版。较长内容自动分成多张图片。",color=InkSoft)
        if(entries.isEmpty()) Text("这一天还没有日记",color=InkSoft)
        entries.forEach{entry->Row(Modifier.fillMaxWidth().clickable(enabled=!busy){ids=ArrayList(if(entry.id in ids)ids-entry.id else ids+entry.id)},verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            Checkbox(entry.id in ids,onCheckedChange={checked->ids=ArrayList(if(checked)ids+entry.id else ids-entry.id)},enabled=!busy)
            Column(Modifier.weight(1f)){Text("${entry.time}  ${entry.moodIcon} ${entry.mood}",color=DateInk);Text(entry.body.take(60),maxLines=2,color=BodyInk)}
        }}
        Text("分享风格",color=Ink)
        ShareTemplate.entries.filter{it.editorial||classicStyles}.forEach{item->Row(Modifier.fillMaxWidth().testTag("share-style-${item.name}").clickable(enabled=!busy){template=item}.padding(vertical=4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){RadioButton(template==item,onClick={template=item},enabled=!busy);Column{Text(item.title,color=Ink);Text(item.description,color=InkSoft,fontSize=11.sp)}}}
        TextButton(onClick={classicStyles=!classicStyles}){Text(if(classicStyles)"收起经典风格" else "更多经典风格（8）")}
        Text("字号 ${font.toInt()} · 分享图片中的大小",color=InkSoft)
        Slider(font,{font=it},valueRange=28f..48f,enabled=!busy)
        Text("行距 ${String.format("%.1f",spacing)}",color=InkSoft)
        Slider(spacing,{spacing=it},valueRange=1.2f..2f,enabled=!busy)
        Button(enabled=!busy&&ids.isNotEmpty(),onClick={
            val selected=entries.filter{it.id in ids};val selectedTemplate=template;val selectedFont=font;val selectedSpacing=spacing
            busy=true;message="正在本机排版…"
            scope.launch{try{files=withContext(Dispatchers.Default){ShareImages.generate(context,date,selected,selectedTemplate,selectedFont,selectedSpacing)};previewIndex=0;message="已生成 ${files.size} 张图片，请预览后保存或分享"}catch(e:Exception){message="生成失败：${e.message}"}finally{busy=false}}
        }){Text(if(busy)"处理中…" else "生成分享图片")}
        if(message.isNotBlank()) Text(message,color=InkSoft)
        if(files.isNotEmpty()){
            val index=previewIndex.coerceIn(0,files.lastIndex)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(enabled=!busy,onClick={if(Build.VERSION.SDK_INT<29 && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else saveImages()}){Text("保存到相册")}
                Button(enabled=!busy,onClick={runCatching{ShareImages.share(context,files)}.onFailure{message="无法打开分享：${it.message}"}}){Text("系统分享")}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                TextButton(enabled=index>0,onClick={previewIndex=index-1}){Text("上一张")}
                Text("${index+1} / ${files.size}",color=InkSoft,modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(enabled=index<files.lastIndex,onClick={previewIndex=index+1}){Text("下一张")}
            }
            OutlinedButton(onClick={viewer(files.map{it.absolutePath},index)},modifier=Modifier.fillMaxWidth().bringIntoViewRequester(previewBring).testTag("share-fullscreen")){Text("全屏预览 · 可缩放")}
            // Explicit dimensions prevent an unbounded scroll container from stalling image sizing.
            // Decode one preview at a time, including for very long days with many exported pages.
            var ratio by remember(files[index]) { mutableFloatStateOf(ShareImages.WIDTH.toFloat()/ShareImages.HEIGHT) }
            LaunchedEffect(files[index]) {
                ratio=withContext(Dispatchers.IO){val options=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(files[index].absolutePath,options);if(options.outHeight>0)options.outWidth.toFloat()/options.outHeight else ShareImages.WIDTH.toFloat()/ShareImages.HEIGHT}
            }
            AsyncImage(files[index],"分享图片预览 ${index+1}",onError={previewError=true},
                modifier=Modifier.fillMaxWidth().aspectRatio(ratio)
                    .testTag("share-preview").clickable{viewer(files.map{it.absolutePath},index)})
            if(previewError) Text("缩略图暂时无法加载，请点上方全屏预览重试。",color=MaterialTheme.colorScheme.error)
        }
    }
}

object ShareImages {
    const val WIDTH=1080
    const val HEIGHT=2800
    fun generate(context:Context,date:String,entries:List<DiaryEntity>,template:ShareTemplate,font:Float,lineSpacing:Float):List<File> {
        require(entries.isNotEmpty()){"请至少选择一篇日记"}
        val root=File(context.cacheDir,"shares").apply{mkdirs()}
        root.listFiles()?.filter{it.isDirectory&&System.currentTimeMillis()-it.lastModified()>7*86400000L}?.forEach{it.deleteRecursively()}
        val directory=File(root,UUID.randomUUID().toString()).apply{mkdirs()}
        if(template.editorial) return try {
            EditorialShareRenderer.render(context,date,entries,template,font,lineSpacing,directory)
        } catch(error:Throwable) { directory.deleteRecursively();throw error }
        val files=mutableListOf<File>()
        val margin=when(template){ShareTemplate.MINIMAL,ShareTemplate.NOTE->100f;ShareTemplate.POSTCARD->96f;else->72f}
        val ink=if(template==ShareTemplate.FILM)Color.rgb(242,236,220) else Color.rgb(40,48,40)
        val muted=when(template){ShareTemplate.FILM->Color.rgb(187,185,164);ShareTemplate.POSTCARD->Color.rgb(57,86,114);ShareTemplate.NOTE->Color.rgb(116,95,150);ShareTemplate.MOOD->Color.rgb(170,102,81);ShareTemplate.MAGAZINE->Color.rgb(121,70,54);else->Color.rgb(104,118,85)}
        var bitmap:Bitmap?=null;var canvas:Canvas?=null;var y=0f
        val footer=HEIGHT-100f
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        fun page() {
            bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);canvas=Canvas(bitmap!!)
            canvas!!.drawColor(when(template){ShareTemplate.PAPER,ShareTemplate.LITERARY->Color.rgb(247,245,237);ShareTemplate.MINIMAL,ShareTemplate.MAGAZINE->Color.WHITE;ShareTemplate.FILM->Color.rgb(36,39,34);ShareTemplate.NOTE->Color.rgb(238,234,245);ShareTemplate.MOOD->Color.rgb(252,240,232);ShareTemplate.POSTCARD->Color.rgb(247,241,226);else->Color.WHITE})
            if(template==ShareTemplate.PAPER){paint.color=Color.rgb(235,232,220);for(i in 0..HEIGHT step 8)canvas!!.drawLine(0f,i.toFloat(),WIDTH.toFloat(),i.toFloat(),paint)}
            paint.color=muted;paint.textSize=26f;paint.typeface=Typeface.create("serif",Typeface.NORMAL)
            when(template){
                ShareTemplate.LITERARY->{
                    paint.textSize=136f;canvas!!.drawText(LocalDate.parse(date).dayOfMonth.toString().padStart(2,'0'),margin,175f,paint)
                    paint.textSize=29f;canvas!!.drawText(date,margin+210,107f,paint)
                    canvas!!.drawText("星期"+listOf("一","二","三","四","五","六","日")[LocalDate.parse(date).dayOfWeek.value-1],margin+210,164f,paint)
                    paint.strokeWidth=2f;canvas!!.drawLine(margin,220f,WIDTH-margin,220f,paint);y=275f
                }
                ShareTemplate.MAGAZINE->{
                    paint.color=ink;paint.textSize=106f;paint.typeface=Typeface.create("serif",Typeface.BOLD);canvas!!.drawText("SHILING",margin,150f,paint)
                    paint.textSize=24f;paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);canvas!!.drawText("日常生活特刊   /   $date",margin,202f,paint)
                    paint.strokeWidth=5f;canvas!!.drawLine(margin,229f,WIDTH-margin,229f,paint);y=285f
                }
                ShareTemplate.NOTE->{
                    paint.color=Color.WHITE;canvas!!.drawRoundRect(38f,110f,WIDTH-38f,HEIGHT-100f,28f,28f,paint)
                    paint.color=muted;canvas!!.drawText("拾零  /  $date",margin,77f,paint);y=173f
                }
                ShareTemplate.MOOD->{
                    paint.color=Color.rgb(245,219,200);canvas!!.drawCircle(WIDTH-160f,145f,83f,paint)
                    paint.color=ink;paint.textSize=70f;canvas!!.drawText(entries.firstOrNull()?.moodIcon?.ifBlank{"☀"}?:"☀",WIDTH-205f,169f,paint)
                    paint.color=muted;paint.textSize=46f;canvas!!.drawText("今天的心情小报",margin,116f,paint)
                    paint.textSize=26f;canvas!!.drawText(date,margin,180f,paint);y=276f
                }
                ShareTemplate.POSTCARD->{
                    paint.color=muted;paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;canvas!!.drawRect(30f,30f,WIDTH-30f,HEIGHT-30f,paint)
                    canvas!!.drawCircle(WIDTH-170f,150f,75f,paint);canvas!!.drawCircle(WIDTH-170f,150f,65f,paint)
                    paint.style=Paint.Style.FILL;paint.textSize=25f;canvas!!.drawText("拾 零",WIDTH-201f,159f,paint)
                    paint.textSize=46f;canvas!!.drawText("寄给日常的明信片",margin,120f,paint)
                    paint.textSize=25f;canvas!!.drawText(date,margin,186f,paint);y=276f
                }
                else->{canvas!!.drawText("拾零  /  $date",margin,80f,paint);y=140f}
            }
        }
        fun finish() {
            val target=bitmap?:return
            val minHeight=when(template){ShareTemplate.MAGAZINE->1440;ShareTemplate.LITERARY,ShareTemplate.MOOD,ShareTemplate.POSTCARD,ShareTemplate.FILM->1350;else->1000}
            val usedHeight=maxOf(minHeight,(y+150).toInt()).coerceAtMost(HEIGHT)
            // Trim unused paper on the last page while keeping a comfortable social-card proportion.
            val output=if(usedHeight<HEIGHT)Bitmap.createBitmap(target,0,0,WIDTH,usedHeight) else target
            val finalCanvas=Canvas(output)
            if(template==ShareTemplate.NOTE){paint.color=Color.rgb(238,234,245);finalCanvas.drawRect(0f,usedHeight-95f,WIDTH.toFloat(),usedHeight.toFloat(),paint)}
            if(template==ShareTemplate.POSTCARD){paint.color=muted;paint.strokeWidth=3f;finalCanvas.drawLine(30f,usedHeight-30f,WIDTH-30f,usedHeight-30f,paint)}
            paint.color=muted;paint.textSize=24f;paint.typeface=Typeface.create("serif",Typeface.NORMAL)
            finalCanvas.drawText("拾零 · ${files.size+1}",margin,usedHeight-48f,paint)
            val file=File(directory,"${date}-${files.size+1}.jpg")
            file.outputStream().use{check(output.compress(Bitmap.CompressFormat.JPEG,94,it))}
            files+=file;if(output!==target)output.recycle();target.recycle();bitmap=null;canvas=null
        }
        fun next() {finish();page()}
        fun text(value:String,size:Float,color:Int) {
            val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size;typeface=Typeface.create(if(template in listOf(ShareTemplate.MINIMAL,ShareTemplate.NOTE,ShareTemplate.MOOD))"sans-serif" else "serif",Typeface.NORMAL)}
            val width=(WIDTH-margin*2).toInt()
            val layout=StaticLayout.Builder.obtain(value,0,value.length,tp,width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(0f,lineSpacing).build()
            var line=0
            while(line<layout.lineCount){
                val top=layout.getLineTop(line)
                var end=line
                while(end<layout.lineCount && layout.getLineBottom(end)-top<=footer-y)end++
                if(end==line){next();continue}
                val h=(layout.getLineBottom(end-1)-top).toFloat()
                canvas!!.save();canvas!!.clipRect(margin,y,WIDTH-margin,y+h);canvas!!.translate(margin,y-top);layout.draw(canvas!!);canvas!!.restore()
                y+=h;line=end
                if(line<layout.lineCount)next()
            }
            y+=22f
        }
        fun photo(path:String) {
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(path,bounds)
            if(bounds.outWidth<=0||bounds.outHeight<=0){text("［这张图片无法读取］",28f,muted);return}
            var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1600)sample*=2
            val photo=BitmapFactory.decodeFile(path,BitmapFactory.Options().apply{inSampleSize=sample})?:error("图片读取失败")
            try{
                val width=WIDTH-margin*2-32
                val h=minOf(width*photo.height/photo.width,1500f)
                val actualWidth=h*photo.width/photo.height
                if(y+h+48>footer)next()
                val left=(WIDTH-actualWidth)/2
                paint.color=if(template==ShareTemplate.FILM)Color.rgb(13,15,12) else Color.WHITE
                canvas!!.drawRect(left-16,y-16,left+actualWidth+16,y+h+24,paint)
                if(template==ShareTemplate.FILM){paint.color=Color.rgb(222,213,181);for(i in 0..5){val x=left+i*actualWidth/5;canvas!!.drawRect(x,y-13,x+8,y-4,paint)}}
                canvas!!.drawBitmap(photo,null,RectF(left,y,left+actualWidth,y+h),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                y+=h+64
            }finally{photo.recycle()}
        }
        fun filmPair(paths:List<String>) {
            if(paths.size==1){photo(paths.single());return}
            val rowHeight=650f
            if(y+rowHeight+60>footer)next()
            paths.forEachIndexed { index,path ->
                val options=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(path,options)
                if(options.outWidth<=0||options.outHeight<=0)throw IllegalArgumentException("有照片无法读取，请先在日记中检查")
                var sample=1;while(maxOf(options.outWidth,options.outHeight)/sample>1000)sample*=2
                val image=BitmapFactory.decodeFile(path,BitmapFactory.Options().apply{inSampleSize=sample})?:error("照片无法读取")
                try{
                    val frameWidth=(WIDTH-margin*2-36)/2
                    val x=margin+index*(frameWidth+36)
                    canvas!!.save();canvas!!.rotate(if(index==0)-2f else 2f,x+frameWidth/2,y+rowHeight/2)
                    paint.color=Color.rgb(232,225,206)
                    canvas!!.drawRect(x,y,x+frameWidth,y+rowHeight,paint)
                    val slot=RectF(x+14,y+14,x+frameWidth-14,y+rowHeight-56)
                    val scale=minOf(slot.width()/image.width,slot.height()/image.height)
                    val w=image.width*scale;val h=image.height*scale
                    paint.color=Color.rgb(26,29,25);canvas!!.drawRect(slot,paint)
                    canvas!!.drawBitmap(image,null,RectF(slot.centerX()-w/2,slot.centerY()-h/2,slot.centerX()+w/2,slot.centerY()+h/2),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    canvas!!.restore()
                }finally{image.recycle()}
            }
            y+=rowHeight+60
        }
        try {
            page()
            entries.forEachIndexed{index,entry->
                if(y>footer-180)next()
                if(index>0){paint.color=muted;canvas!!.drawLine(margin,y,WIDTH-margin,y,paint);y+=45}
                text("${entry.time}   ${entry.moodIcon} ${entry.mood}",28f,muted)
                val photosFirst=template==ShareTemplate.MAGAZINE||template==ShareTemplate.POSTCARD
                if(photosFirst)entry.images().forEach{photo(it)}
                text(entry.body,font,ink)
                if(!photosFirst){if(template==ShareTemplate.FILM)entry.images().chunked(2).forEach{filmPair(it)} else entry.images().forEach{photo(it)}}
                if(entry.tags.isNotBlank())text(entry.tagList().joinToString("  "){"# $it"},26f,muted)
                y+=40
            }
            finish();return files
        }catch(e:Throwable){bitmap?.recycle();directory.deleteRecursively();throw e}
    }

    fun share(context:Context,files:List<File>) {
        val uris=ArrayList(files.map{FileProvider.getUriForFile(context,"${context.packageName}.files",it)})
        val intent=Intent(if(uris.size==1)Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply{
            type="image/jpeg";addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if(uris.size==1)putExtra(Intent.EXTRA_STREAM,uris[0]) else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
            clipData=ClipData.newRawUri("拾零每日分享",uris[0]).apply{uris.drop(1).forEach{addItem(ClipData.Item(it))}}
        }
        context.startActivity(Intent.createChooser(intent,"分享这一天"))
    }
    fun save(context:Context,files:List<File>):List<android.net.Uri> {
        val saved=mutableListOf<android.net.Uri>()
        files.forEach{file->
            val values=ContentValues().apply{
                put(MediaStore.Images.Media.DISPLAY_NAME,"拾零-${System.currentTimeMillis()}-${file.name}")
                put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
                if(Build.VERSION.SDK_INT>=29){put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/拾零");put(MediaStore.Images.Media.IS_PENDING,1)}
            }
            val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?:error("无法创建相册图片")
            try{
                context.contentResolver.openOutputStream(uri)?.use{out->file.inputStream().use{it.copyTo(out)}}?:error("无法写入相册")
                if(Build.VERSION.SDK_INT>=29)context.contentResolver.update(uri,ContentValues().apply{put(MediaStore.Images.Media.IS_PENDING,0)},null,null)
                saved+=uri
            }catch(e:Exception){context.contentResolver.delete(uri,null,null);throw e}
        }
        return saved
    }
}
