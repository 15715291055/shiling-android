package com.fuguang.localdiary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fuguang.localdiary.data.DiaryViewModel
import com.fuguang.localdiary.ui.FuguangApp
import com.fuguang.localdiary.ui.theme.FuguangTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        com.fuguang.localdiary.ui.theme.AppearanceStore.load(this)
        setContent {
            FuguangTheme {
                val vm: DiaryViewModel = viewModel()
                FuguangApp(viewModel = vm)
            }
        }
    }
}
