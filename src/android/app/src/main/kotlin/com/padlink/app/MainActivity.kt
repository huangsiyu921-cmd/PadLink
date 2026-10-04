package com.padlink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.padlink.app.ui.StickPad
import com.padlink.core.ControllerType
import com.padlink.core.InputFrame
import com.padlink.core.ProtocolConstants
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PadLinkScreen()
            }
        }
    }
}

/**
 * P0 阶段的最小界面：验证「Canvas 自绘 + 多点触摸 + 协议编码」三件事能连起来。
 * 真正的布局编辑器、连接管理、按键组在 P2 实现（见 docs/03）。
 */
@Composable
private fun PadLinkScreen() {
    var leftStick by remember { mutableStateOf(0f to 0f) }
    var rightStick by remember { mutableStateOf(0f to 0f) }

    val frame = remember(leftStick, rightStick) {
        InputFrame(
            controller = ControllerType.XBOX360,
            player = 0,
            leftX = toProtocolAxis(leftStick.first),
            leftY = toProtocolAxis(-leftStick.second),
            rightX = toProtocolAxis(rightStick.first),
            rightY = toProtocolAxis(-rightStick.second),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            StickPad(modifier = Modifier.width(180.dp)) { x, y -> leftStick = x to y }
            StickPad(modifier = Modifier.width(180.dp)) { x, y -> rightStick = x to y }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Text(
                text = "PadLink",
                color = Color(0xFFE0E0E0),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "协议 v${ProtocolConstants.VERSION} · 未连接",
                color = Color(0xFF808080),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            text = hex(frame),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.55f)
                .padding(16.dp),
            color = Color(0xFF4C4C4C),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** 归一化值 → 协议轴值。屏幕 y 向下为正，协议 y 向上为正，调用处负责取反。 */
private fun toProtocolAxis(normalized: Float): Short =
    (normalized.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()

private fun hex(frame: InputFrame): String =
    frame.encode().joinToString("") { "%02x".format(it) }
