package com.fuguang.localdiary.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.*
import com.fuguang.localdiary.ui.theme.*
import kotlin.math.roundToInt

data class GlassBackdrop(val image:ImageBitmap?,val viewport:IntSize,val imageOpacity:Float)
val LocalGlassBackdrop=staticCompositionLocalOf { GlassBackdrop(null,IntSize.Zero,0f) }

// Sample the pre-blurred wallpaper at the panel's real screen position. Text and controls
// are drawn afterwards and never blurred. One bitmap is shared by every scrolling panel.
@Composable
fun Modifier.glassEffect(shape:Shape=RoundedCornerShape(20.dp),tint:Color=CardWhite,navigation:Boolean=false,protectReading:Boolean=false):Modifier {
    val backdrop=LocalGlassBackdrop.current
    val settings=AppearanceStore.current
    val baseOpacity=if(navigation)settings.navigationOpacity else settings.cardOpacity
    val opacity=if(protectReading) 1f-(1f-baseOpacity)*(1f-settings.readingProtection) else baseOpacity
    var position by remember { mutableStateOf(Offset.Zero) }
    return this.onGloballyPositioned { position=it.positionInRoot() }.clip(shape).drawWithCache {
        val outline=shape.createOutline(size,layoutDirection,this)
        val highlight=Brush.linearGradient(listOf(Color.White.copy(alpha=.48f*(1-opacity*.5f)),Color.White.copy(alpha=.03f),Color.White.copy(alpha=.13f)),start=Offset.Zero,end=Offset(size.width,size.height))
        onDrawBehind {
            // Glass must have a stable backdrop even without a wallpaper. Otherwise a
            // translucent paper surface reveals its own elevation shadow as it scrolls.
            drawRect(Paper)
            val bitmap=backdrop.image
            if(bitmap!=null && backdrop.viewport.width>0 && backdrop.viewport.height>0) {
                val scale=maxOf(backdrop.viewport.width.toFloat()/bitmap.width,backdrop.viewport.height.toFloat()/bitmap.height)
                val w=(bitmap.width*scale).roundToInt();val h=(bitmap.height*scale).roundToInt()
                val x=(backdrop.viewport.width-w)/2-position.x.roundToInt()
                val y=(backdrop.viewport.height-h)/2-position.y.roundToInt()
                drawImage(bitmap,dstOffset=IntOffset(x,y),dstSize=IntSize(w,h),alpha=backdrop.imageOpacity,filterQuality=FilterQuality.Low)
            }
            drawRect(tint.copy(alpha=opacity))
            // Keep the face tint uniform; a full-face local gradient changes brightness as
            // a long paper card scrolls. Restrict specular light to the glass edge instead.
            drawOutline(outline,brush=highlight,style=Stroke(.7.dp.toPx()))
        }
    }
}

@Composable
fun GlassCard(modifier:Modifier=Modifier,shape:Shape=RoundedCornerShape(20.dp),colors:CardColors=CardDefaults.cardColors(containerColor=CardWhite),border:BorderStroke?=null,elevation:CardElevation=CardDefaults.cardElevation(),content:@Composable ColumnScope.()->Unit) {
    Card(modifier.glassEffect(shape,colors.containerColor),shape=shape,colors=CardDefaults.cardColors(containerColor=Color.Transparent),border=BorderStroke(.7.dp,Color.White.copy(alpha=.4f)),elevation=elevation,content=content)
}
