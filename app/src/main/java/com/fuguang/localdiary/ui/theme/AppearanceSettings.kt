package com.fuguang.localdiary.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AppearanceSettings(
    val background: Long = 0xFFF7F5EF, val image: String = "", val opacity: Float = .24f, val blur: Float = 0f,
    val accent: Long = 0xFF71815A, val title: Long = 0xFF263429, val section: Long = 0xFF263429,
    val auxiliary: Long = 0xFF737B6B, val body: Long = 0xFF263429, val date: Long = 0xFF71815A,
    val cardOpacity: Float = .55f, val navigationOpacity: Float = .65f, val glassBlur: Float = 18f
)

object AppearanceStore {
    var current by mutableStateOf(AppearanceSettings())
        private set
    fun load(context: Context) {
        current = runCatching {
            val j = JSONObject(context.getSharedPreferences("shiling_appearance", 0).getString("settings", "{}")!!)
            val d = AppearanceSettings()
            AppearanceSettings(j.optLong("background", d.background), j.optString("image"), j.optDouble("opacity", .24).toFloat().coerceIn(0f,1f), j.optDouble("blur",0.0).toFloat().coerceIn(0f,25f), j.optLong("accent",d.accent), j.optLong("title",d.title), j.optLong("section",d.section), j.optLong("auxiliary",d.auxiliary), j.optLong("body",d.body),j.optLong("date",d.date),j.optDouble("cardOpacity",.55).toFloat().coerceIn(0f,1f),j.optDouble("navigationOpacity",.65).toFloat().coerceIn(0f,1f),j.optDouble("glassBlur",18.0).toFloat().coerceIn(0f,30f))
        }.getOrDefault(AppearanceSettings())
    }
    fun update(context: Context, settings: AppearanceSettings) {
        current = settings
        val j = JSONObject().put("background",settings.background).put("image",settings.image).put("opacity",settings.opacity.toDouble()).put("blur",settings.blur.toDouble()).put("accent",settings.accent).put("title",settings.title).put("section",settings.section).put("auxiliary",settings.auxiliary).put("body",settings.body).put("date",settings.date).put("cardOpacity",settings.cardOpacity.toDouble()).put("navigationOpacity",settings.navigationOpacity.toDouble()).put("glassBlur",settings.glassBlur.toDouble())
        context.getSharedPreferences("shiling_appearance",0).edit().putString("settings",j.toString()).apply()
    }
}

fun readable(foreground: Color, background: Color, minimum: Double = 4.5): Color {
    fun luminance(c: Color): Double {
        fun channel(v: Float): Double = if (v <= .04045f) v / 12.92 else Math.pow((v + .055) / 1.055, 2.4)
        return .2126 * channel(c.red) + .7152 * channel(c.green) + .0722 * channel(c.blue)
    }
    fun ratio(a: Color): Double { val x=luminance(a); val y=luminance(background); return (maxOf(x,y)+.05)/(minOf(x,y)+.05) }
    if (ratio(foreground) >= minimum) return foreground
    val destination = if (ratio(Color.Black) > ratio(Color.White)) Color.Black else Color.White
    for (i in 1..100) { val c=lerp(foreground,destination,i/100f); if (ratio(c)>=minimum) return c }
    return destination
}

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val s = AppearanceStore.current
    var bitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var glass by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var viewport by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val context=LocalContext.current
    val view=LocalView.current
    val density=LocalDensity.current
    val topInset=WindowInsets.statusBars.getTop(density)
    LaunchedEffect(s.image, s.blur,s.glassBlur) {
        if (s.image.isBlank()) { bitmap=null;glass=null; return@LaunchedEffect }
        delay(160)
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeFile(s.image,bounds)
                var sample=1
                val maxEdge=if(s.blur>0) 480 else 1600
                while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxEdge) sample*=2
                val decoded=BitmapFactory.decodeFile(s.image,BitmapFactory.Options().apply { inSampleSize=sample }) ?: return@runCatching null
                val background=if(s.blur>0) boxBlur(decoded, (s.blur*.75f).toInt().coerceAtLeast(1)) else decoded
                val factor=480f/maxOf(background.width,background.height)
                val small=if(factor<1f)Bitmap.createScaledBitmap(background,(background.width*factor).toInt().coerceAtLeast(1),(background.height*factor).toInt().coerceAtLeast(1),true) else background
                val frosted=if(s.glassBlur>0)boxBlur(small,s.glassBlur.toInt().coerceAtLeast(1)) else small
                background.asImageBitmap() to frosted.asImageBitmap()
            }.getOrNull()
        }
        bitmap=result?.first;glass=result?.second
    }
    val statusColor=remember(bitmap,viewport,s.background,s.opacity) {
        val image=bitmap
        if(image==null || viewport.width==0 || viewport.height==0) Color(s.background) else {
            // Sample the same cropped top row that is actually behind the system icons.
            val scale=maxOf(viewport.width.toFloat()/image.width,viewport.height.toFloat()/image.height)
            val left=((image.width-viewport.width/scale)/2).toInt().coerceIn(0,image.width-1)
            val row=((image.height-viewport.height/scale)/2).toInt().coerceIn(0,image.height-1)
            val count=(viewport.width/scale).toInt().coerceIn(1,image.width-left)
            val pixels=IntArray(count)
            image.readPixels(pixels,startX=left,startY=row,width=count,height=1,stride=count)
            val r=pixels.sumOf{(it shr 16)and 255}/count
            val g=pixels.sumOf{(it shr 8)and 255}/count
            val b=pixels.sumOf{it and 255}/count
            lerp(Color(s.background),Color(r,g,b),s.opacity)
        }
    }
    val darkIcons=statusColor.luminance()>.38f
    SideEffect {
        var host:Context=context
        while(host is android.content.ContextWrapper && host !is android.app.Activity)host=host.baseContext
        (host as? android.app.Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window,view).isAppearanceLightStatusBars=darkIcons
        }
    }
    Box(Modifier.fillMaxSize().background(Color(s.background)).onSizeChanged { viewport=it }) {
        bitmap?.let { Image(it,null,Modifier.matchParentSize(),contentScale=ContentScale.Crop,alpha=s.opacity) }
        if(topInset>0 && bitmap!=null) {
            // Feather the image into the status area; never draw a flat opaque white strip.
            val scrim=if(darkIcons)Color.White.copy(alpha=.10f) else Color.Black.copy(alpha=.10f)
            Box(Modifier.fillMaxWidth().height(with(density){topInset.toDp()}+20.dp)
                .background(Brush.verticalGradient(listOf(scrim,Color.Transparent))))
        }
        CompositionLocalProvider(com.fuguang.localdiary.ui.LocalGlassBackdrop provides com.fuguang.localdiary.ui.GlassBackdrop(glass,viewport,s.opacity)) { content() }
    }
}

// Two separable passes, computed on settings changes, never during scrolling.
private fun boxBlur(source: Bitmap, radius: Int): Bitmap {
    val w=source.width; val h=source.height
    val pixels=IntArray(w*h); source.getPixels(pixels,0,w,0,0,w,h)
    val temp=IntArray(pixels.size); val result=IntArray(pixels.size)
    fun pass(input:IntArray,output:IntArray,horizontal:Boolean) {
        val lines=if(horizontal) h else w; val length=if(horizontal) w else h
        fun index(line:Int, p:Int)=if(horizontal) line*w+p else p*w+line
        for(line in 0 until lines) {
            var r=0; var g=0; var b=0
            for(p in -radius..radius) { val c=input[index(line,p.coerceIn(0,length-1))]; r+=(c shr 16) and 255;g+=(c shr 8) and 255;b+=c and 255 }
            val count=radius*2+1
            for(p in 0 until length) {
                output[index(line,p)]=0xff000000.toInt() or ((r/count) shl 16) or ((g/count) shl 8) or (b/count)
                val a=input[index(line,(p-radius).coerceIn(0,length-1))]; val z=input[index(line,(p+radius+1).coerceIn(0,length-1))]
                r+=((z shr 16) and 255)-((a shr 16) and 255);g+=((z shr 8) and 255)-((a shr 8) and 255);b+=(z and 255)-(a and 255)
            }
        }
    }
    pass(pixels,temp,true); pass(temp,result,false)
    return Bitmap.createBitmap(result,w,h,Bitmap.Config.ARGB_8888)
}
