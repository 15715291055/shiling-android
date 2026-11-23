package com.fuguang.localdiary.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

val Paper get() = Color(AppearanceStore.current.background)
val PaperLight get() = lerp(Paper, Color.White, if(Paper.luminance()<.3f) .025f else .45f)
val CardWhite get() = lerp(Paper, Color.White, if(Paper.luminance()<.3f) .04f else .72f)
val Ink get() = readable(Color(AppearanceStore.current.section), CardWhite)
val TitleInk get() = readable(Color(AppearanceStore.current.title), Paper)
val BodyInk get() = readable(Color(AppearanceStore.current.body), Paper)
val DateInk get() = readable(Color(AppearanceStore.current.date), Paper)
val InkSoft get() = readable(Color(AppearanceStore.current.auxiliary), Paper)
val Sage get() = readable(Color(AppearanceStore.current.accent), Paper, 4.5)
val SageLight get() = lerp(Paper, Color(AppearanceStore.current.accent), .14f)
val OnSage get() = readable(Color.White, Sage)
val Clay = Color(0xFFB77F68)
val Line get() = lerp(Paper, Color(AppearanceStore.current.accent), .28f)
val Mist get() = Paper
