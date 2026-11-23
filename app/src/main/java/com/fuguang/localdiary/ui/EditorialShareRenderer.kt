package com.fuguang.localdiary.ui

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.fuguang.localdiary.data.DiaryEntity
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Random
import kotlin.math.*

/** Dynamic renderers based on the four approved Imagegen art directions, not screenshots. */
internal object EditorialShareRenderer {
    data class Fragment(val id:Long,val start:Int,val end:Int,val page:Int)
    private const val W=ShareImages.WIDTH
    private const val H=ShareImages.HEIGHT

    fun render(context:Context,date:String,entries:List<DiaryEntity>,style:ShareTemplate,font:Float,spacing:Float,directory:File,audit:((Fragment)->Unit)?=null,photoAudit:((String)->Unit)?=null):List<File> {
        val files=mutableListOf<File>()
        val day=LocalDate.parse(date)
        val dark=style==ShareTemplate.MEMORY
        val ink=if(dark)0xfff0e7d3.toInt() else 0xff302e28.toInt()
        val accent=when(style){ShareTemplate.EDITORIAL->0xff9d4e32.toInt();ShareTemplate.MEMORY->0xffd4c9ae.toInt();ShareTemplate.FROST->0xff635a66.toInt();else->0xff818865.toInt()}
        val margin=if(style==ShareTemplate.FROST)126f else 62f
        val bottom=H-174f
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val serif=Typeface.create("serif",Typeface.NORMAL)
        var foreground:Bitmap?=null
        var canvas:Canvas?=null
        var y=0f
        var active:DiaryEntity?=null
        var firstOnPage=true
        val wallpaper=if(style==ShareTemplate.FROST)entries.asSequence().flatMap{it.images().asSequence()}.firstOrNull()?.let{path->runCatching{decode(path,360)}.getOrNull()} else null
        val blurred=wallpaper?.let { blur(it,18) }

        fun label(value:String,x:Float,baseline:Float,size:Float,color:Int=ink,bold:Boolean=false) {
            paint.color=color;paint.textSize=size;paint.typeface=if(bold)Typeface.create("serif",Typeface.BOLD) else serif
            paint.style=Paint.Style.FILL;paint.shader=null
            val room=W-48f-x
            if(room>0 && paint.measureText(value)>room)paint.textSize=size*room/paint.measureText(value)
            canvas!!.drawText(value,x,baseline,paint)
        }
        fun metadata(entry:DiaryEntity)=buildString {append(entry.time);if(entry.mood.isNotBlank()){append(" · ");append(entry.moodIcon);if(entry.moodIcon.isNotBlank())append(' ');append(entry.mood)}}
        fun page() {
            foreground=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);canvas=Canvas(foreground!!);firstOnPage=true
            when(style){
                ShareTemplate.POETRY->{
                    label("拾零",62f,110f,58f)
                    paint.color=accent;paint.strokeWidth=2f;canvas!!.drawLine(64f,132f,107f,132f,paint)
                    label(day.format(DateTimeFormatter.ofPattern("yyyy.MM.")),62f,214f,34f,accent)
                    label(day.dayOfMonth.toString().padStart(2,'0'),42f,400f,182f,accent)
                    y=470f
                }
                ShareTemplate.EDITORIAL->{
                    label("拾零",48f,111f,78f,ink,true)
                    paint.color=accent;paint.strokeWidth=2f;canvas!!.drawLine(245f,82f,W-50f,82f,paint)
                    label(day.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),44f,279f,125f,ink,true)
                    active?.let {label(metadata(it),48f,347f,35f,accent)}
                    y=395f
                }
                ShareTemplate.MEMORY->{
                    label("拾零",48f,111f,67f,ink)
                    val dateLabel=day.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                    paint.textSize=54f;paint.typeface=serif
                    label(dateLabel,W-50f-paint.measureText(dateLabel),100f,54f,ink)
                    active?.let{paint.textSize=31f;label(metadata(it),W-52f-paint.measureText(metadata(it)),163f,31f,accent)}
                    y=225f
                }
                ShareTemplate.FROST->{
                    label("拾零",116f,198f,61f)
                    paint.color=0xffd0a38d.toInt();paint.strokeWidth=4f;canvas!!.drawLine(118f,223f,181f,223f,paint)
                    label(day.dayOfMonth.toString().padStart(2,'0'),111f,463f,183f)
                    paint.color=accent;paint.strokeWidth=1f;canvas!!.drawLine(373f,331f,373f,455f,paint)
                    label(day.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),414f,371f,36f)
                    active?.let{label(metadata(it),414f,434f,29f)}
                    y=535f
                }
                else->error("Unsupported editorial style")
            }
        }
        fun background(c:Canvas,height:Int) {
            val base=if(dark)0xff292c23.toInt() else 0xfff8f5ef.toInt()
            c.drawColor(base)
            if(style==ShareTemplate.FROST){
                paint.shader=LinearGradient(0f,0f,W.toFloat(),height.toFloat(),intArrayOf(0xffe7d2c8.toInt(),0xffd8d9e4.toInt(),0xffbfc9dc.toInt()),null,Shader.TileMode.CLAMP)
                c.drawRect(0f,0f,W.toFloat(),height.toFloat(),paint);paint.shader=null
                blurred?.let{image->paint.alpha=66;drawCrop(c,image,RectF(0f,0f,W.toFloat(),height.toFloat()),paint);paint.alpha=255}
                val panel=RectF(55f,78f,W-55f,height-60f)
                paint.setShadowLayer(18f,0f,9f,0x18000000);paint.color=0xb5fffdfa.toInt();c.drawRoundRect(panel,42f,42f,paint);paint.clearShadowLayer()
                paint.shader=LinearGradient(0f,80f,W.toFloat(),height.toFloat(),intArrayOf(0x50ffffff,0x08ffffff,0x25ffffff),null,Shader.TileMode.CLAMP);c.drawRoundRect(panel,42f,42f,paint);paint.shader=null
                paint.color=0xd0ffffff.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f;c.drawRoundRect(panel,42f,42f,paint);paint.style=Paint.Style.FILL
            }else{
                val random=Random(428L)
                val points=FloatArray(24000){i->if(i%2==0)random.nextFloat()*W else random.nextFloat()*height}
                paint.color=if(dark)0x13c9b88b else 0x096b6046;paint.strokeWidth=1f;c.drawPoints(points,paint)
                if(style==ShareTemplate.POETRY){
                    // Quiet embossed botanical detail, away from the reading area.
                    val branch=Path().apply{moveTo(W-70f,210f);cubicTo(W-112f,125f,W-122f,70f,W-190f,42f)}
                    paint.color=0x55d8d4c9;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;c.drawPath(branch,paint)
                    for(i in 0..3){val x=W-85f-i*24;val yy=175f-i*33;val leaf=Path().apply{moveTo(x,yy);quadTo(x-42,yy-5,x-44,yy-33);quadTo(x-8,yy-29,x,yy)};paint.style=Paint.Style.FILL;paint.color=0x55e5e0d5;c.drawPath(leaf,paint)}
                    paint.style=Paint.Style.FILL
                }
            }
        }
        fun finish() {
            val source=foreground?:return
            val height=maxOf(1440,(y+154f).roundToInt()).coerceAtMost(H)
            val output=Bitmap.createBitmap(W,height,Bitmap.Config.ARGB_8888)
            try{
                val out=Canvas(output);background(out,height);out.drawBitmap(source,0f,0f,null)
                paint.shader=null;paint.color=accent;paint.strokeWidth=1f
                val footerInset=if(style==ShareTemplate.FROST)100f else 63f
                out.drawLine(margin,height-footerInset-43f,W-margin,height-footerInset-43f,paint)
                paint.textSize=25f;paint.typeface=serif
                out.drawText("拾零",margin,height-footerInset,paint)
                val pageNumber=(files.size+1).toString().padStart(2,'0')
                out.drawText(pageNumber,W-margin-paint.measureText(pageNumber),height-footerInset,paint)
                val file=File(directory,"$date-${files.size+1}.jpg")
                file.outputStream().use{check(output.compress(Bitmap.CompressFormat.JPEG,95,it))};files+=file
            }finally{output.recycle();source.recycle();foreground=null;canvas=null}
        }
        fun next(){finish();page()}

        data class FirstColumn(val x:Float,val width:Int,val bottom:Float)
        fun prose(value:String,size:Float=font,color:Int=ink,id:Long?=null,column:FirstColumn?=null) {
            if(value.isEmpty())return
            var offset=0;var firstColumn=column
            val textPaint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size;typeface=serif}
            while(offset<value.length){
                if(y>bottom-size*spacing){next();firstColumn=null}
                val col=firstColumn
                val x=col?.x?:margin
                val width=col?.width?:(W-margin*2).toInt()
                val remaining=value.substring(offset)
                val layout=StaticLayout.Builder.obtain(remaining,0,remaining.length,textPaint,width).setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f,spacing).build()
                val room=(minOf(bottom,col?.bottom?:bottom)-y).coerceAtLeast(0f)
                var lines=0
                while(lines<layout.lineCount && layout.getLineBottom(lines)<=room)lines++
                if(lines==0){if(col!=null){y=col.bottom+35;firstColumn=null}else next();continue}
                val height=layout.getLineBottom(lines-1).toFloat()
                canvas!!.save();canvas!!.clipRect(x,y,x+width,y+height);canvas!!.translate(x,y);layout.draw(canvas!!);canvas!!.restore()
                val count=layout.getLineEnd(lines-1)
                check(count>0){"文字分页异常"}
                if(id!=null)audit?.invoke(Fragment(id,offset,offset+count,files.size))
                offset+=count;y+=height+22
                if(col!=null){y=maxOf(y,col.bottom+35);firstColumn=null}
                else if(offset<value.length)next()
            }
        }
        fun photo(path:String,rect:RectF,angle:Float=0f,torn:Boolean=false,seed:Long=0,cornerCut:Boolean=false) {
            val image=decode(path,1500)
            try{
                canvas!!.save();if(angle!=0f)canvas!!.rotate(angle,rect.centerX(),rect.centerY())
                val border=RectF(rect).apply{inset(-9f,-9f)}
                val outline=if(cornerCut)cutPaper(rect,seed) else if(torn)tornRect(rect,seed) else Path().apply{addRect(rect,Path.Direction.CW)}
                paint.color=if(dark)0xffe8dfc9.toInt() else 0xfffffdf7.toInt();paint.setShadowLayer(8f,0f,5f,0x25000000)
                if(torn||cornerCut)canvas!!.drawPath(outline,paint) else canvas!!.drawRect(border,paint)
                paint.clearShadowLayer();canvas!!.clipPath(outline)
                drawCrop(canvas!!,image,rect,paint)
                canvas!!.restore()
                photoAudit?.invoke(path)
            }finally{image.recycle()}
        }
        fun remainingPhotos(paths:List<String>) {
            paths.chunked(2).forEachIndexed{row,images->
                val height=if(images.size==1)570f else 385f
                if(y+height+40>bottom)next()
                val width=(W-margin*2-(if(images.size==2)18 else 0))/images.size
                images.forEachIndexed{index,path->val x=margin+index*(width+18);photo(path,RectF(x,y,x+width,y+height),seed=(row*2+index).toLong())}
                y+=height+48
            }
        }
        try{
            active=entries.first();page()
            entries.forEachIndexed { index,entry ->
                active=entry
                if(y>bottom-240)next()
                if(index>0){paint.color=accent;paint.strokeWidth=1f;canvas!!.drawLine(margin,y,W-margin,y,paint);y+=48;prose(metadata(entry),28f,accent)}
                val images=entry.images();var used=0;var column:FirstColumn?=null
                if(index==0){
                    when(style){
                        ShareTemplate.POETRY->{
                            if(images.isNotEmpty()){
                                photo(images[0],RectF(55f,220f,W-75f,865f),torn=true,seed=entry.id,cornerCut=true);used=1;y=918f
                                if(images.size>1){photo(images[1],RectF(649f,648f,W-48f,925f),angle=-3f,seed=entry.id+1);used=2;y=984f}
                            }
                            prose(metadata(entry),29f,accent)
                        }
                        ShareTemplate.MEMORY->{
                            if(images.isNotEmpty()){
                                photo(images[0],RectF(54f,y,if(images.size>1)850f else W-54f,y+690f),angle=-3f,seed=entry.id);used=1
                                if(images.size>1){photo(images[1],RectF(665f,y+352f,W-48f,y+728f),angle=4f,seed=entry.id+1);used=2}
                                y+=808f
                            }
                        }
                        ShareTemplate.EDITORIAL->{
                            if(images.isNotEmpty()){
                                photo(images[0],RectF(32f,y,W-32f,y+542f),seed=entry.id);used=1;y+=584
                                if(images.size>1){
                                    photo(images[1],RectF(32f,y,437f,y+475f),seed=entry.id+1);used=2
                                    paint.color=accent;paint.strokeWidth=1.5f;canvas!!.drawLine(480f,y,480f,y+475f,paint)
                                    column=FirstColumn(525f,W-577,y+475f)
                                }
                            }
                        }
                        ShareTemplate.FROST->{
                            if(images.isNotEmpty()){
                                if(images.size>1){photo(images[0],RectF(110f,y,584f,y+340f));photo(images[1],RectF(596f,y,W-110f,y+340f));used=2;y+=407f}
                                else {photo(images[0],RectF(110f,y,W-110f,y+450f));used=1;y+=517f}
                            }
                        }
                        else->Unit
                    }
                }else if(images.isNotEmpty()){
                    remainingPhotos(images.take(2));used=minOf(2,images.size)
                }
                if(style==ShareTemplate.POETRY){
                    paint.color=accent;paint.strokeWidth=1f;canvas!!.drawCircle(38f,y+8,4f,paint);canvas!!.drawLine(38f,y,38f,minOf(bottom,y+250),paint)
                }
                prose(entry.body,id=entry.id,column=column)
                if(images.size>used)remainingPhotos(images.drop(used))
                if(entry.tags.isNotBlank())prose(entry.tagList().joinToString(" · "),25f,accent)
                y+=30
            }
            finish();return files
        }catch(error:Throwable){foreground?.recycle();files.forEach{it.delete()};throw error}
        finally{wallpaper?.recycle();blurred?.recycle()}
    }

    private fun decode(path:String,maxEdge:Int):Bitmap {
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(path,options)
        require(options.outWidth>0&&options.outHeight>0){"有照片无法读取，请在日记中检查后重试"}
        var sample=1;while(maxOf(options.outWidth,options.outHeight)/sample>maxEdge)sample*=2
        val source=BitmapFactory.decodeFile(path,BitmapFactory.Options().apply{inSampleSize=sample})?:error("照片读取失败")
        val orientation=runCatching{ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix=Matrix()
        when(orientation){
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL->matrix.setScale(-1f,1f)
            ExifInterface.ORIENTATION_ROTATE_180->matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL->matrix.setScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE->{matrix.setRotate(90f);matrix.postScale(-1f,1f)}
            ExifInterface.ORIENTATION_ROTATE_90->matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE->{matrix.setRotate(270f);matrix.postScale(-1f,1f)}
            ExifInterface.ORIENTATION_ROTATE_270->matrix.setRotate(270f)
        }
        if(matrix.isIdentity)return source
        val rotated=Bitmap.createBitmap(source,0,0,source.width,source.height,matrix,true);if(rotated!==source)source.recycle();return rotated
    }
    private fun drawCrop(canvas:Canvas,image:Bitmap,rect:RectF,paint:Paint) {
        val scale=maxOf(rect.width()/image.width,rect.height()/image.height)
        val width=rect.width()/scale;val height=rect.height()/scale
        val src=Rect(((image.width-width)/2).toInt(),((image.height-height)/2).toInt(),((image.width+width)/2).toInt(),((image.height+height)/2).toInt())
        canvas.drawBitmap(image,src,rect,paint)
    }
    private fun tornRect(r:RectF,seed:Long):Path {
        val random=Random(seed+71);val points=listOf(PointF(r.left,r.top),PointF(r.right,r.top),PointF(r.right,r.bottom),PointF(r.left,r.bottom))
        return roughPolygon(points,random)
    }
    private fun cutPaper(r:RectF,seed:Long):Path = roughPolygon(listOf(PointF(r.left+255,r.top),PointF(r.right,r.top),PointF(r.right,r.bottom),PointF(r.left,r.bottom),PointF(r.left,r.top+225),PointF(r.left+255,r.top+225)),Random(seed+5))
    private fun roughPolygon(points:List<PointF>,random:Random):Path = Path().apply{
        moveTo(points[0].x,points[0].y)
        points.indices.forEach{i->val a=points[i];val b=points[(i+1)%points.size];val count=(hypot(b.x-a.x,b.y-a.y)/9).toInt().coerceAtLeast(1);for(j in 1..count){val t=j.toFloat()/count;lineTo(a.x+(b.x-a.x)*t+(random.nextFloat()-.5f)*4,a.y+(b.y-a.y)*t+(random.nextFloat()-.5f)*4)}};close()
    }
    private fun blur(source:Bitmap,radius:Int):Bitmap {
        val w=source.width;val h=source.height;val input=IntArray(w*h);source.getPixels(input,0,w,0,0,w,h)
        val temp=IntArray(input.size);val output=IntArray(input.size)
        fun pass(src:IntArray,dst:IntArray,horizontal:Boolean){
            val length=if(horizontal)w else h;val lines=if(horizontal)h else w
            fun at(line:Int,p:Int)=if(horizontal)line*w+p else p*w+line
            for(line in 0 until lines){var rr=0;var gg=0;var bb=0
                for(p in -radius..radius){val c=src[at(line,p.coerceIn(0,length-1))];rr+=(c shr 16) and 255;gg+=(c shr 8) and 255;bb+=c and 255}
                val n=radius*2+1
                for(p in 0 until length){dst[at(line,p)]=0xff000000.toInt() or ((rr/n) shl 16) or ((gg/n) shl 8) or (bb/n)
                    val a=src[at(line,(p-radius).coerceIn(0,length-1))];val b=src[at(line,(p+radius+1).coerceIn(0,length-1))]
                    rr+=((b shr 16) and 255)-((a shr 16) and 255);gg+=((b shr 8) and 255)-((a shr 8) and 255);bb+=(b and 255)-(a and 255)
                }
            }
        }
        pass(input,temp,true);pass(temp,output,false);return Bitmap.createBitmap(output,w,h,Bitmap.Config.ARGB_8888)
    }
}
