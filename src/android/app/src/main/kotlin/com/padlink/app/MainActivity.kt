package com.padlink.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.padlink.app.ui.ConnectionBar
import com.padlink.app.ui.DPadPad
import com.padlink.app.ui.PadButton
import com.padlink.app.ui.StickPad
import com.padlink.app.ui.TriggerPad
import com.padlink.core.GamepadButtons

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
    val scope = rememberCoroutineScope()
    val controller = remember { PadLinkController(scope) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        ConnectionBar(controller)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LeftCluster(controller, Modifier.weight(1f).fillMaxHeight())
            CenterCluster(controller, Modifier.weight(0.72f).fillMaxHeight())
            RightCluster(controller, Modifier.weight(1f).fillMaxHeight())
        }
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

@Composable
private fun LeftCluster(controller: PadLinkController, modifier: Modifier = Modifier) {
    val input = controller.input

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TriggerPad(Modifier.size(width = 30.dp, height = 76.dp)) {
                input.leftTrigger = it
                controller.push()
            }
            PadButton("LB", Modifier.size(50.dp)) {
                controller.setButton(GamepadButtons.LEFT_SHOULDER, it)
            }
        }

        DPadPad(Modifier.size(118.dp)) { direction ->
            input.dpad = direction
            controller.push()
        }

        StickPad(Modifier.size(128.dp)) { x, y ->
            input.leftX = x
            input.leftY = y
            controller.push()
        }
    }
}

@Composable
private fun CenterCluster(controller: PadLinkController, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        PadButton("Guide", Modifier.size(46.dp)) {
            controller.setButton(GamepadButtons.GUIDE, it)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadButton("Back", Modifier.size(46.dp)) {
                controller.setButton(GamepadButtons.BACK, it)
            }
            PadButton("Start", Modifier.size(46.dp)) {
                controller.setButton(GamepadButtons.START, it)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadButton("L3", Modifier.size(46.dp)) {
                controller.setButton(GamepadButtons.LEFT_THUMB, it)
            }
            PadButton("R3", Modifier.size(46.dp)) {
                controller.setButton(GamepadButtons.RIGHT_THUMB, it)
            }
        }
    }
}

@Composable
private fun RightCluster(controller: PadLinkController, modifier: Modifier = Modifier) {
    val input = controller.input

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PadButton("RB", Modifier.size(50.dp)) {
                controller.setButton(GamepadButtons.RIGHT_SHOULDER, it)
            }
            TriggerPad(Modifier.size(width = 30.dp, height = 76.dp)) {
                input.rightTrigger = it
                controller.push()
            }
        }

        ButtonCluster { bit, pressed -> controller.setButton(bit, pressed) }

        StickPad(Modifier.size(128.dp)) { x, y ->
            input.rightX = x
            input.rightY = y
            controller.push()
        }
    }
}

/** ABXY 菱形排布。位置固定，标签按 Xbox 叫法——切换成 DS4 时改标签即可，协议不用动。 */
@Composable
private fun ButtonCluster(onButton: (Int, Boolean) -> Unit) {
    Box(Modifier.size(148.dp)) {
        PadButton("Y", Modifier.align(Alignment.TopCenter).size(50.dp)) { onButton(GamepadButtons.Y, it) }
        PadButton("X", Modifier.align(Alignment.CenterStart).size(50.dp)) { onButton(GamepadButtons.X, it) }
        PadButton("B", Modifier.align(Alignment.CenterEnd).size(50.dp)) { onButton(GamepadButtons.B, it) }
        PadButton("A", Modifier.align(Alignment.BottomCenter).size(50.dp)) { onButton(GamepadButtons.A, it) }
    }
}
