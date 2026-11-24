package com.fuguang.localdiary.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.ui.theme.*
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.sin

private val JournalSerif = FontFamily.Serif

internal class PaperEdge(private val seed: Long) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val roughness = minOf(with(density) { 3.2.dp.toPx() },minOf(size.width,size.height)*.085f)
        val step=with(density) { 8.dp.toPx() }
        fun grain(index:Int,edge:Int):Float {
            var value=seed.toInt() xor (index*374761393) xor (edge*668265263)
            value=(value xor (value ushr 13))*1274126177
            return ((value xor (value ushr 16)) and 65535)/65535f
        }
        val path = Path().apply {
            val across=(size.width/step).toInt().coerceIn(5,120)
            val down=(size.height/step).toInt().coerceIn(4,120)
            moveTo(roughness*.4f,roughness*.4f)
            for(i in 1 until across)lineTo(size.width*i/across,roughness*(.12f+.88f*grain(i,0)))
            lineTo(size.width-roughness*.4f,roughness*.4f)
            for(i in 1 until down)lineTo(size.width-roughness*(.12f+.88f*grain(i,1)),size.height*i/down)
            lineTo(size.width-roughness*.4f,size.height-roughness*.4f)
            for(i in across-1 downTo 1)lineTo(size.width*i/across,size.height-roughness*(.12f+.88f*grain(i,2)))
            lineTo(roughness*.4f,size.height-roughness*.4f)
            for(i in down-1 downTo 1)lineTo(roughness*(.12f+.88f*grain(i,3)),size.height*i/down)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun JournalEntry(
    entry: DiaryEntity,
    appearance: JournalAppearance,
    showDate: Boolean,
    onClick: () -> Unit
) {
    val viewer = LocalPhotoViewer.current
    val readingInteraction = remember(entry.id) { MutableInteractionSource() }
    val paper = appearance == JournalAppearance.PAPER
    val nodeColor = Color(0xFFB49A59)
    Row(
        Modifier.fillMaxWidth().testTag("diary-${entry.id}")
            .drawBehind {
                val x = 72.dp.toPx()
                if (paper) {
                    val thread = Path().apply {
                        moveTo(x, 0f)
                        cubicTo(x + 5.dp.toPx(), size.height * .26f, x - 5.dp.toPx(), size.height * .74f, x, size.height)
                    }
                    drawPath(thread, Sage.copy(alpha = .62f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                } else drawLine(Line, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                drawCircle(if (paper) Sage else nodeColor, 2.8.dp.toPx(), Offset(x, 22.dp.toPx()))
            }
            .padding(bottom = 3.dp)
    ) {
        Box(Modifier.width(72.dp).padding(top = 11.dp, end = 9.dp), contentAlignment = Alignment.TopEnd) {
            if (showDate) {
                val date = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                val label = date?.format(DateTimeFormatter.ofPattern("d日")) ?: entry.date
                Box(
                    Modifier.semantics { contentDescription = entry.date }
                        .clip(if (paper) PaperEdge(entry.id) else RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp, topEnd = 12.dp, bottomEnd = 12.dp))
                        .background(SageLight).padding(horizontal = 7.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontFamily = JournalSerif, fontSize = 13.sp, color = DateInk, maxLines = 1, softWrap = false)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 18.dp, top = 9.dp, bottom = 13.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(entry.time, fontFamily = JournalSerif, color = DateInk, fontSize = 12.sp)
                if (entry.mood.isNotBlank()) {
                    Text(" · ${entry.mood}", color = InkSoft, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            val shape = PaperEdge(entry.id)
            val contentModifier = if (paper) {
                Modifier.fillMaxWidth().testTag("journal-paper-${entry.id}").shadow(1.dp, shape).glassEffect(shape)
                    .clickable(interactionSource=readingInteraction,indication=null,onClick=onClick).padding(horizontal = 12.dp, vertical = 11.dp)
            } else Modifier.fillMaxWidth().clickable(interactionSource=readingInteraction,indication=null,onClick=onClick)
            Column(contentModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(entry.body, color = BodyInk, fontFamily = JournalSerif, fontSize = 15.sp,
                    lineHeight = 24.sp, maxLines = Int.MAX_VALUE)
                if (entry.images().isNotEmpty()) {
                    val paths = entry.images()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        paths.take(2).forEachIndexed { index, path ->
                            Box(Modifier.weight(1f).clickable { viewer(paths, index) }) {
                                AsyncImage(
                                    model = File(path), contentDescription = "日记图片",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(if (paths.size == 1) 112.dp else 105.dp)
                                        .clip(RoundedCornerShape(if (paper) 2.dp else 9.dp))
                                        .then(if (paper) Modifier.border(3.dp, PaperLight) else Modifier)
                                )
                                if (index == 1 && paths.size > 2) {
                                    Box(Modifier.matchParentSize().clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = .35f)), contentAlignment = Alignment.Center) {
                                        Text("+${paths.size - 2}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                if (entry.tagList().isNotEmpty()) {
                    Text(entry.tagList().joinToString("  ") { "# $it" }, color = InkSoft, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!paper) {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(.5.dp).background(Line))
            }
        }
    }
}

@Composable
fun ThemeChooser(selected: JournalAppearance, onSelect: (JournalAppearance) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题外观", color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                JournalAppearance.entries.forEach { appearance ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("theme-${appearance.name}"),
                        onClick = { onSelect(appearance) },
                        color = if (selected == appearance) SageLight else PaperLight,
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected == appearance) Sage else Line)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected == appearance, onClick = null)
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(appearance.title, color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text(appearance.description, color = InkSoft, fontSize = 11.sp)
                                }
                            }
                            ThemeMiniature(appearance)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        containerColor = Paper
    )
}

@Composable
private fun ThemeMiniature(appearance: JournalAppearance) {
    Canvas(Modifier.fillMaxWidth().height(50.dp).padding(top = 9.dp)) {
        val x = size.width * .14f
        val spine = Path().apply {
            moveTo(x, 0f)
            if (appearance == JournalAppearance.PAPER) cubicTo(x + 7f, size.height * .3f, x - 7f, size.height * .7f, x, size.height)
            else lineTo(x, size.height)
        }
        drawPath(spine, Sage, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        for (row in 0..1) {
            val y = size.height * (.19f + row * .46f)
            drawCircle(Sage, 2.dp.toPx(), Offset(x, y))
            drawRoundRect(Sage.copy(alpha = .2f), Offset(0f, y - 4.dp.toPx()), Size(x - 8.dp.toPx(), 9.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            if (appearance == JournalAppearance.PAPER) drawRect(CardWhite, Offset(x + 9.dp.toPx(), y - 4.dp.toPx()), Size(size.width - x - 9.dp.toPx(), size.height * .35f))
            drawLine(InkSoft.copy(alpha = .5f), Offset(x + 14.dp.toPx(), y), Offset(size.width * .89f, y), 1.dp.toPx())
            drawLine(InkSoft.copy(alpha = .3f), Offset(x + 14.dp.toPx(), y + 7.dp.toPx()), Offset(size.width * .7f, y + 7.dp.toPx()), 1.dp.toPx())
        }
    }
}
