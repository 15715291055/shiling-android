package com.fuguang.localdiary.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fuguang.localdiary.ui.theme.*

@Composable
internal fun ShareStylePicker(selected:ShareTemplate,classic:Boolean,enabled:Boolean,onSelect:(ShareTemplate)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        ShareTemplate.entries.filter { it.editorial||classic||it==selected }.forEach { style ->
            Card(
                modifier=Modifier.width(132.dp).testTag("share-style-${style.name}")
                    .selectable(selected==style,enabled=enabled,role=Role.RadioButton,onClick={onSelect(style)}),
                shape=RoundedCornerShape(14.dp),
                border=BorderStroke(if(selected==style)2.dp else 1.dp,if(selected==style)Sage else Line),
                colors=CardDefaults.cardColors(containerColor=PaperLight)
            ) {
                if(style.editorial) AsyncImage("file:///android_asset/share-previews/${style.name}.jpg","${style.title}模板示例",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().height(154.dp))
                else ClassicStyleThumbnail(style)
                Text((if(selected==style)"✓ " else "")+style.title,color=Ink,fontSize=13.sp,modifier=Modifier.padding(horizontal=10.dp,vertical=9.dp))
            }
        }
    }
}

@Composable
private fun ClassicStyleThumbnail(style:ShareTemplate) {
    Canvas(Modifier.fillMaxWidth().height(154.dp).clip(RoundedCornerShape(topStart=12.dp,topEnd=12.dp))) {
        val dark=style==ShareTemplate.FILM
        val bg=when(style){ShareTemplate.FILM->Color(0xFF242722);ShareTemplate.NOTE->Color(0xFFEEEAF5);ShareTemplate.MOOD->Color(0xFFFCF0E8);ShareTemplate.MINIMAL,ShareTemplate.MAGAZINE->Color.White;else->Color(0xFFF7F1E2)}
        val ink=if(dark)Color(0xFFF2ECDC) else Color(0xFF687655)
        drawRect(bg)
        val w=size.width;val h=size.height;val margin=w*.12f
        if(style==ShareTemplate.NOTE)drawRect(Color.White,Offset(w*.07f,h*.06f),Size(w*.86f,h*.88f))
        drawRect(ink,Offset(margin,h*.09f),Size(w*(if(style==ShareTemplate.LITERARY).28f else .17f),h*.08f))
        if(style==ShareTemplate.MOOD)drawCircle(Color(0xFFE4B18F),w*.075f,Offset(w*.8f,h*.13f))
        val photoFirst=style==ShareTemplate.MAGAZINE||style==ShareTemplate.POSTCARD||dark
        val photoY=if(photoFirst)h*.25f else h*.56f
        if(dark){
            drawRect(Color(0xFF91A89A),Offset(margin,photoY),Size(w*.35f,h*.28f))
            drawRect(Color(0xFFD3C4A5),Offset(w*.53f,photoY+h*.035f),Size(w*.35f,h*.28f))
        }else drawRect(Color(0xFF91A89A),Offset(margin,photoY),Size(w*.76f,h*.28f))
        val textY=if(photoFirst)h*.63f else h*.25f
        for(i in 0..3)drawRect(ink.copy(alpha=.65f),Offset(margin,textY+i*h*.055f),Size(w*(if(i==3).48f else .76f),h*.012f))
        drawLine(ink.copy(alpha=.4f),Offset(margin,h*.91f),Offset(w-margin,h*.91f),1.dp.toPx())
    }
}
