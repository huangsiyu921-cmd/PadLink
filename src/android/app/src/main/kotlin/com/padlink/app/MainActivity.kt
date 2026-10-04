package com.padlink.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.padlink.app.layout.LayoutStore
import com.padlink.app.layout.PadLayout
import com.padlink.app.ui.ConnectionBar
import com.padlink.app.ui.GamepadCanvas
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 手柄界面得一直亮着，也不能被状态栏占掉空间。
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PadLinkApp()
            }
        }
    }
}

@Composable
private fun PadLinkApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { PadLinkController(scope) }
    val store = remember { LayoutStore(context) }

    var layout by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1C))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        ConnectionBar(
            controller = controller,
            layout = layout,
            editing = editing,
            onEditingChange = { next ->
                editing = next
                // 只在退出编辑时落盘：拖拽过程中每帧写一次存储没必要。
                if (!next) store.save(layout)
            },
            onLayoutChange = { layout = it },
        )

        GamepadCanvas(
            controller = controller,
            layout = layout,
            editing = editing,
            onLayoutChange = { layout = it },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // 退出界面就把连接收掉，别让发送循环在后台空转。
    DisposableEffect(Unit) {
        onDispose { controller.disconnect() }
    }

    // ADB 模式的地址是固定的 127.0.0.1，插着线打开就该能用，不必再点一下「连接」。
    LaunchedEffect(Unit) {
        if (controller.mode == TransportMode.ADB) controller.connect()
    }
}
