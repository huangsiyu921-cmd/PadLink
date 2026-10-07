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
import com.padlink.app.input.TiltSource
import com.padlink.app.layout.LayoutStore
import com.padlink.app.layout.PadLayout
import com.padlink.app.ui.AppSettingsDialog
import com.padlink.app.ui.ConfirmDialog
import com.padlink.app.ui.ElementSettingsDialog
import com.padlink.app.ui.ExportDialog
import com.padlink.app.ui.GamepadCanvas
import com.padlink.app.ui.Selection
import com.padlink.app.ui.TopBar
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
    val controller = remember { PadLinkController(scope, TiltSource(context)) }
    val store = remember { LayoutStore(context) }

    var layout by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Selection?>(null) }

    var showAppSettings by remember { mutableStateOf(false) }
    var elementSettings by remember { mutableStateOf<Selection?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1C))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        TopBar(
            controller = controller,
            editing = editing,
            selection = selection,
            onOpenSettings = { showAppSettings = true },
            onOpenElementSettings = { selection?.let { elementSettings = it } },
            onToggleEnabled = {
                layout = when (val current = selection) {
                    is Selection.Element -> layout.toggleEnabled(current.kind)
                    is Selection.Sub -> layout.updateSubKey(current.kind, current.index) {
                        it.copy(enabled = !it.enabled)
                    }

                    null -> layout
                }
            },
            onReset = { showResetConfirm = true },
            onExport = { showExport = true },
            onDone = {
                editing = false
                selection = null
                store.save(layout)
            },
        )

        GamepadCanvas(
            controller = controller,
            layout = layout,
            editing = editing,
            selection = selection,
            onSelectionChange = { selection = it },
            onLayoutChange = { layout = it },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showAppSettings) {
        AppSettingsDialog(
            controller = controller,
            layout = layout,
            onDismiss = { showAppSettings = false },
            onApply = {
                layout = it
                store.save(it)
                showAppSettings = false
            },
            onEnterLayoutEdit = { draft ->
                layout = draft
                selection = null
                editing = true
                showAppSettings = false
            },
        )
    }

    elementSettings?.let { target ->
        ElementSettingsDialog(
            selection = target,
            layout = layout,
            onDismiss = { elementSettings = null },
            onApply = {
                layout = it
                store.save(it)
                elementSettings = null
            },
        )
    }

    if (showExport) {
        ExportDialog(
            layout = layout,
            onDismiss = { showExport = false },
            onImport = { imported ->
                layout = imported
                store.save(imported)
                showExport = false
            },
        )
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = "重置布局",
            message = "所有控件的位置、大小、角度都恢复默认。",
            confirmText = "重置",
            onConfirm = {
                layout = PadLayout.Default
                selection = null
                store.save(PadLayout.Default)
            },
            onDismiss = { showResetConfirm = false },
        )
    }

    // 退出界面就把连接和传感器都收掉，别让发送循环和传感器在后台空转。
    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    // ADB 模式的地址是固定的 127.0.0.1，插着线打开就该能用，不必再点一下「连接」。
    LaunchedEffect(Unit) {
        if (controller.mode == TransportMode.ADB) controller.connect()
    }
}
