package com.padlink.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padlink.app.LinkStatus
import com.padlink.app.PadLinkController
import com.padlink.app.TransportMode
import com.padlink.app.layout.DPadStyle
import com.padlink.app.layout.PadLayout
import com.padlink.app.layout.TriggerStyle

/**
 * 顶部条。平时管连接；进布局编辑模式后换成布局工具（样式切换、恢复默认）。
 * 两套内容不并存，免得一堆控件挤在一起。
 */
@Composable
fun ConnectionBar(
    controller: PadLinkController,
    layout: PadLayout,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onLayoutChange: (PadLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (editing) {
            LayoutTools(layout, onLayoutChange)
        } else {
            ConnectionTools(controller)
        }

        Button(onClick = { onEditingChange(!editing) }) {
            Text(if (editing) "完成" else "布局", fontSize = 13.sp)
        }
    }
}

@Composable
private fun ConnectionTools(controller: PadLinkController) {
    FilterChip(
        selected = controller.mode == TransportMode.ADB,
        onClick = { controller.mode = TransportMode.ADB },
        label = { Text("ADB", fontSize = 13.sp) },
    )
    FilterChip(
        selected = controller.mode == TransportMode.WIFI,
        onClick = { controller.mode = TransportMode.WIFI },
        label = { Text("WiFi", fontSize = 13.sp) },
    )

    if (controller.mode == TransportMode.WIFI) {
        OutlinedTextField(
            value = controller.host,
            onValueChange = { controller.host = it },
            singleLine = true,
            label = { Text("PC 地址", fontSize = 12.sp) },
            modifier = Modifier.width(180.dp),
        )
    }

    Button(onClick = { if (controller.isConnected) controller.disconnect() else controller.connect() }) {
        Text(if (controller.isConnected) "断开" else "连接", fontSize = 13.sp)
    }

    val (text, color) = statusOf(controller)
    Text(text = text, color = color, fontSize = 13.sp)
}

@Composable
private fun LayoutTools(layout: PadLayout, onLayoutChange: (PadLayout) -> Unit) {
    FilterChip(
        selected = layout.triggerStyle == TriggerStyle.SLIDE,
        onClick = { onLayoutChange(layout.copy(triggerStyle = TriggerStyle.SLIDE)) },
        label = { Text("扳机·滑动", fontSize = 13.sp) },
    )
    FilterChip(
        selected = layout.triggerStyle == TriggerStyle.BUTTON,
        onClick = { onLayoutChange(layout.copy(triggerStyle = TriggerStyle.BUTTON)) },
        label = { Text("扳机·按钮", fontSize = 13.sp) },
    )
    FilterChip(
        selected = layout.dpadStyle == DPadStyle.COMBO,
        onClick = { onLayoutChange(layout.copy(dpadStyle = DPadStyle.COMBO)) },
        label = { Text("十字·8向", fontSize = 13.sp) },
    )
    FilterChip(
        selected = layout.dpadStyle == DPadStyle.TRIANGLE,
        onClick = { onLayoutChange(layout.copy(dpadStyle = DPadStyle.TRIANGLE)) },
        label = { Text("十字·三角", fontSize = 13.sp) },
    )
    Button(onClick = { onLayoutChange(PadLayout.Default) }) {
        Text("重置", fontSize = 13.sp)
    }
    Text(text = "拖动 · 双指缩放", color = Color(0xFF8A8A8A), fontSize = 12.sp)
}

private fun statusOf(controller: PadLinkController): Pair<String, Color> = when (val status = controller.status) {
    is LinkStatus.Idle -> "未连接" to Color(0xFF9E9E9E)
    is LinkStatus.Connecting -> "连接中…" to Color(0xFFFFB74D)
    is LinkStatus.Connected -> "${status.describe} · ${controller.stats.framesSent} 帧" to Color(0xFF81C784)
    is LinkStatus.Failed -> status.message to Color(0xFFE57373)
}
