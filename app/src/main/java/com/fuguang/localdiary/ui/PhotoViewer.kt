package com.fuguang.localdiary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File

val LocalPhotoViewer = staticCompositionLocalOf<(List<String>,Int)->Unit> { { _,_ -> } }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhotoViewer(paths: List<String>, initial: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest=onClose, properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val dialogView=LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                window.statusBarColor=android.graphics.Color.TRANSPARENT
                window.navigationBarColor=android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window,dialogView).apply {
                    isAppearanceLightStatusBars=false
                    isAppearanceLightNavigationBars=false
                }
            }
        }
        val pager=rememberPagerState(initialPage=initial.coerceIn(0,paths.lastIndex)) { paths.size }
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("photo-viewer")) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                var zoom by remember { mutableFloatStateOf(1f) }
                var pan by remember { mutableStateOf(Offset.Zero) }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val width=constraints.maxWidth.toFloat(); val height=constraints.maxHeight.toFloat()
                    fun clamp(offset: Offset, scale: Float) = Offset(offset.x.coerceIn(-width*(scale-1)/2,width*(scale-1)/2),offset.y.coerceIn(-height*(scale-1)/2,height*(scale-1)/2))
                    AsyncImage(File(paths[page]),"全屏日记图片",contentScale=ContentScale.Fit,
                        modifier=Modifier.fillMaxSize()
                            .pointerInput(page) { detectTapGestures(onDoubleTap={ zoom=if(zoom>1f)1f else 2.5f;pan=Offset.Zero }) }
                            .transformable(rememberTransformableState { scale,delta,_ ->
                                zoom=(zoom*scale).coerceIn(1f,5f);pan=clamp(pan+delta,zoom)
                            }, canPan = { zoom>1f })
                            .graphicsLayer { scaleX=zoom;scaleY=zoom;translationX=pan.x;translationY=pan.y })
                }
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${pager.currentPage+1} / ${paths.size}",color=Color.White,modifier=Modifier.weight(1f))
                IconButton(onClick=onClose) { Icon(Icons.Outlined.Close,"关闭图片预览",tint=Color.White) }
            }
            Text("双击或双指缩放 · 左右切换",color=Color.White.copy(alpha=.65f),modifier=Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp))
        }
    }
}
