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

/** 顶部连接条：选模式、填地址、连/断、看状态。文案一律短句。 */
@Composable
fun ConnectionBar(controller: PadLinkController, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
                modifier = Modifier.width(190.dp),
            )
        }

        Button(onClick = { if (controller.isConnected) controller.disconnect() else controller.connect() }) {
            Text(if (controller.isConnected) "断开" else "连接", fontSize = 13.sp)
        }

        val (text, color) = statusOf(controller)
        Text(text = text, color = color, fontSize = 13.sp)
    }
}

private fun statusOf(controller: PadLinkController): Pair<String, Color> = when (val status = controller.status) {
    is LinkStatus.Idle -> "未连接" to Color(0xFF9E9E9E)
    is LinkStatus.Connecting -> "连接中…" to Color(0xFFFFB74D)
    is LinkStatus.Connected -> "${status.describe} · ${controller.stats.framesSent} 帧" to Color(0xFF81C784)
    is LinkStatus.Failed -> status.message to Color(0xFFE57373)
}
