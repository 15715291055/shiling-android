package com.fuguang.localdiary.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.*
import com.fuguang.localdiary.data.DiaryEntity
import com.fuguang.localdiary.ui.theme.*
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

internal fun dampedDistance(raw: Float, limit: Float): Float = sign(raw)*limit*(1f-exp(-abs(raw)/limit))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DateBoundary(
    date: String, diaries: List<DiaryEntity>, enabled: Boolean=true, skipEmpty: Boolean=false,
    onDate: (String)->Unit, modifier: Modifier=Modifier, newestFirst:Boolean=false, content: @Composable ()->Unit
) {
    val density=LocalDensity.current
    val threshold=with(density) { 92.dp.toPx() }
    val limit=with(density) { 230.dp.toPx() }
    var raw by remember { mutableFloatStateOf(0f) }
    var animated by remember { mutableStateOf<Float?>(null) }
    var settling by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var height by remember { mutableFloatStateOf(1f) }
    val haptics=LocalHapticFeedback.current
    val latestDate by rememberUpdatedState(date)
    val latestEntries by rememberUpdatedState(diaries)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestSkip by rememberUpdatedState(skipEmpty)
    val latestOnDate by rememberUpdatedState(onDate)
    fun adjacent(direction: Float): String {
        val current=LocalDate.parse(latestDate)
        val previous=direction>0
        if(latestSkip) {
            val dates=latestEntries.map { it.date }.distinct().sorted()
            val target=if(previous) dates.lastOrNull { it<latestDate } else dates.firstOrNull { it>latestDate }
            if(target!=null) return target
        }
        return (if(previous) current.minusDays(1) else current.plusDays(1)).toString()
    }
    fun drag(delta:Float): Offset {
        val old=raw
        raw+=delta
        if(old!=0f && sign(old)!=sign(raw)) raw=0f
        val now=abs(dampedDistance(raw,limit))>=threshold
        if(now && !armed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        armed=now
        return Offset(0f,if(raw==0f) -old else delta)
    }
    val connection=remember(limit,threshold) { object:NestedScrollConnection {
        override fun onPreScroll(available:Offset,source:NestedScrollSource):Offset {
            if(settling) return Offset(0f,available.y)
            if(!latestEnabled || source!=NestedScrollSource.UserInput) return Offset.Zero
            return if(raw!=0f) drag(available.y) else Offset.Zero
        }
        override fun onPostScroll(consumed:Offset,available:Offset,source:NestedScrollSource):Offset {
            if(!latestEnabled || settling || source!=NestedScrollSource.UserInput || available.y==0f) return Offset.Zero
            return drag(available.y)
        }
        override suspend fun onPreFling(available:Velocity):Velocity {
            if(raw==0f || settling) return Velocity.Zero
            settling=true
            val start=dampedDistance(raw,limit)
            try {
                val animation=Animatable(start)
                if(abs(start)>=threshold && latestEnabled) {
                    val direction=sign(start)
                    val next=adjacent(direction)
                    animation.animateTo(direction*height,tween(220)) { animated=value }
                    latestOnDate(next)
                    raw=0f;armed=false
                    animated=-direction*height
                    animation.snapTo(-direction*height)
                    animation.animateTo(0f,tween(250)) { animated=value }
                } else animation.animateTo(0f,spring(dampingRatio=1f,stiffness=380f)) { animated=value }
            } finally { raw=0f;animated=null;armed=false;settling=false }
            return available
        }
    } }
    val offset=animated ?: dampedDistance(raw,limit)
    val neighbor=adjacent(if(offset>=0) 1f else -1f)
    val preview=remember(diaries,neighbor,newestFirst) { diaries.filter { it.date==neighbor }.sortedWith(if(newestFirst)compareByDescending<DiaryEntity>{it.time}.thenByDescending{it.id} else compareBy<DiaryEntity>{it.time}.thenBy{it.id}) }
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().nestedScroll(connection).testTag("date-boundary").semantics { stateDescription=if(armed) "松开切换日期" else "正常阅读" }) {
        SideEffect { height=constraints.maxHeight.toFloat().coerceAtLeast(1f) }
        if(abs(offset)>1f) {
            Box(Modifier.fillMaxWidth().height(with(density){abs(offset).coerceAtMost(height).toDp()})
                .align(if(offset>0)Alignment.TopCenter else Alignment.BottomCenter).clipToBounds()) {
            Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=6.dp)
                .glassEffect(RoundedCornerShape(22.dp)).testTag("date-neighbor-preview")
                .padding(start=18.dp,end=if(offset<0)132.dp else 18.dp,top=12.dp,bottom=12.dp),verticalArrangement=Arrangement.Center) {
                Text(if(armed) "松开，查看 $neighbor" else "继续${if(offset>0)"下拉" else "上拉"}，查看 $neighbor",color=DateInk,fontSize=12.sp)
                Spacer(Modifier.height(8.dp))
                Text(preview.firstOrNull()?.body?.take(130) ?: "这一天还没有记录",color=InkSoft,fontSize=12.sp,maxLines=2)
            }
            }
        }
        Box(Modifier.fillMaxSize().offset { IntOffset(0,offset.roundToInt()) }) {
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) { content() }
        }
    }
}
