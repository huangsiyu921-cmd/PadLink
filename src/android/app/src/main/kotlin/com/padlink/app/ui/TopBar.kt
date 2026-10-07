package com.padlink.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padlink.app.LinkStatus
import com.padlink.app.PadLinkController

/**
 * 顶部条。平时只有状态文字 + 中间一个齿轮；进编辑模式后换成布局工具。
 * 参考 EMotion 把设置按钮放中间——双手横握时两边拇指都够不着边角。
 */
@Composable
fun TopBar(
    controller: PadLinkController,
    editing: Boolean,
    selection: Selection?,
    onOpenSettings: () -> Unit,
    onOpenElementSettings: () -> Unit,
    onToggleEnabled: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editing) {
            EditTools(
                selection = selection,
                onOpenElementSettings = onOpenElementSettings,
                onToggleEnabled = onToggleEnabled,
                onReset = onReset,
                onExport = onExport,
                onDone = onDone,
            )
        } else {
            IdleBar(controller, onOpenSettings)
        }
    }
}

@Composable
private fun RowScope.IdleBar(controller: PadLinkController, onOpenSettings: () -> Unit) {
    val (text, color) = statusOf(controller)

    // 三等分：状态在左，齿轮严格居中，重力开关在右。
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        // 连不上时 message 是原始的异常文本，很长；不截断会画到右边把重力开关盖住。
        Text(text = text, color = color, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        TextButton(onClick = onOpenSettings) { Text("设置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
    // 右边留空。原来这里放重力开关，但手柄画布是画在顶栏之上的，
    // A 键正好压在这一块，按钮既看不清也点不着。开关现在只在设置里。
    Box(Modifier.weight(1f))
}

@Composable
private fun RowScope.EditTools(
    selection: Selection?,
    onOpenElementSettings: () -> Unit,
    onToggleEnabled: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onDone: () -> Unit,
) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        Text(
            text = when (selection) {
                null -> "点一个控件"
                is Selection.Element -> selection.kind.label
                is Selection.Sub -> "${selection.kind.label} · ${selection.index + 1}"
            },
            color = Color(0xFFB8D4FF),
            fontSize = 13.sp,
            maxLines = 1,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onOpenElementSettings, enabled = selection != null) { Text("设置", fontSize = 13.sp) }
        TextButton(onClick = onToggleEnabled, enabled = selection != null) { Text("禁用", fontSize = 13.sp) }
        TextButton(onClick = onReset) { Text("重置", fontSize = 13.sp) }
        TextButton(onClick = onExport) { Text("导出", fontSize = 13.sp) }
        Button(onClick = onDone) { Text("完成", fontSize = 13.sp) }
    }
}

private fun statusOf(controller: PadLinkController): Pair<String, Color> = when (val status = controller.status) {
    is LinkStatus.Idle -> "未连接" to Color(0xFF9E9E9E)
    is LinkStatus.Connecting -> "连接中…" to Color(0xFFFFB74D)
    is LinkStatus.Connected -> "${status.describe} · ${controller.stats.framesSent} 帧" to Color(0xFF81C784)
    is LinkStatus.Failed -> status.message to Color(0xFFE57373)
}
