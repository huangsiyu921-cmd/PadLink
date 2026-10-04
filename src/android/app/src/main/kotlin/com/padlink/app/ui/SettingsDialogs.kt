package com.padlink.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padlink.app.PadLinkController
import com.padlink.app.TransportMode
import com.padlink.app.layout.DPadStyle
import com.padlink.app.layout.PadElementKind
import com.padlink.app.layout.PadLayout
import com.padlink.app.layout.TriggerStyle

private val HintColor = Color(0xFF9E9E9E)

/** 选中控件后的设置弹窗。左下取消、右下应用——改到一半反悔不至于把布局弄乱。 */
@Composable
fun ElementSettingsDialog(
    selection: Selection,
    layout: PadLayout,
    onDismiss: () -> Unit,
    onApply: (PadLayout) -> Unit,
) {
    var draft by remember { mutableStateOf(layout) }

    val title = when (selection) {
        is Selection.Element -> selection.kind.label
        is Selection.Sub -> {
            val label = draft.subKeys[selection.kind]?.getOrNull(selection.index)?.label ?: "?"
            "${selection.kind.label} · $label"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (selection) {
                    is Selection.Element -> ElementFields(selection.kind, draft) { draft = it }
                    is Selection.Sub -> SubFields(selection.kind, selection.index, draft) { draft = it }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(draft) }) { Text("应用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ElementFields(kind: PadElementKind, layout: PadLayout, onChange: (PadLayout) -> Unit) {
    val element = layout.element(kind) ?: return

    NumberField("大小", element.size, step = 0.005f) { onChange(layout.resize(kind, it)) }
    NumberField("旋转°", element.rotation, step = 5f, decimals = 1) { onChange(layout.rotate(kind, it)) }
    NumberField("横向%", element.x * 100f, step = 1f, decimals = 2) { onChange(layout.move(kind, it / 100f, element.y)) }
    NumberField("纵向%", element.y * 100f, step = 1f, decimals = 2) { onChange(layout.move(kind, element.x, it / 100f)) }

    TextButton(onClick = { onChange(layout.toggleEnabled(kind)) }) {
        Text(if (element.enabled) "禁用这个键" else "启用这个键")
    }

    if (kind.hasSubKeys) {
        Text("里面的每个键在布局里单独点选即可调整", color = HintColor, fontSize = 12.sp)
    }
}

@Composable
private fun SubFields(kind: PadElementKind, index: Int, layout: PadLayout, onChange: (PadLayout) -> Unit) {
    val key = layout.subKeys[kind]?.getOrNull(index) ?: return

    NumberField("左右", key.dx, step = 0.01f) { newValue ->
        onChange(layout.updateSubKey(kind, index) { it.copy(dx = newValue) })
    }
    NumberField("上下", key.dy, step = 0.01f) { newValue ->
        onChange(layout.updateSubKey(kind, index) { it.copy(dy = newValue) })
    }
    NumberField("大小", key.scale, step = 0.005f) { newValue ->
        onChange(layout.updateSubKey(kind, index) { it.copy(scale = newValue) })
    }

    TextButton(onClick = { onChange(layout.updateSubKey(kind, index) { it.copy(enabled = !it.enabled) }) }) {
        Text(if (key.enabled) "禁用这个键" else "启用这个键")
    }
}

/**
 * 主设置面板（点顶部齿轮打开）。
 * 连接操作立即生效；布局相关的改动先存草稿，点「应用」才提交。
 */
@Composable
fun AppSettingsDialog(
    controller: PadLinkController,
    layout: PadLayout,
    onDismiss: () -> Unit,
    onApply: (PadLayout) -> Unit,
    onEnterLayoutEdit: (PadLayout) -> Unit,
) {
    var draft by remember { mutableStateOf(layout) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle("连接模式")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }

                if (controller.mode == TransportMode.WIFI) {
                    OutlinedTextField(
                        value = controller.host,
                        onValueChange = { controller.host = it },
                        singleLine = true,
                        label = { Text("PC 地址", fontSize = 12.sp) },
                        modifier = Modifier.width(200.dp),
                    )
                }

                TextButton(onClick = { if (controller.isConnected) controller.disconnect() else controller.connect() }) {
                    Text(if (controller.isConnected) "断开" else "连接")
                }

                SectionTitle("按钮组合")
                Text("LT / RT 的样式", color = HintColor, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.triggerStyle == TriggerStyle.SLIDE,
                        onClick = { draft = draft.copy(triggerStyle = TriggerStyle.SLIDE) },
                        label = { Text("滑动条", fontSize = 13.sp) },
                    )
                    FilterChip(
                        selected = draft.triggerStyle == TriggerStyle.BUTTON,
                        onClick = { draft = draft.copy(triggerStyle = TriggerStyle.BUTTON) },
                        label = { Text("按钮", fontSize = 13.sp) },
                    )
                }

                Text("十字键的样式", color = HintColor, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.dpadStyle == DPadStyle.COMBO,
                        onClick = { draft = draft.copy(dpadStyle = DPadStyle.COMBO) },
                        label = { Text("8 方向", fontSize = 13.sp) },
                    )
                    FilterChip(
                        selected = draft.dpadStyle == DPadStyle.TRIANGLE,
                        onClick = { draft = draft.copy(dpadStyle = DPadStyle.TRIANGLE) },
                        label = { Text("三角分键", fontSize = 13.sp) },
                    )
                }

                SectionTitle("布局")
                Button(onClick = { onEnterLayoutEdit(draft) }) { Text("调整布局", fontSize = 13.sp) }

                SectionTitle("更多设置")
                Text("主题颜色 / 动态取色 —— 还没做", color = HintColor, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onApply(draft) }) { Text("应用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFFB8D4FF),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** 导出布局：摆出文本让人复制走，拿到后固化成默认布局。 */
@Composable
fun ExportDialog(layout: PadLayout, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出布局", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text("复制这段发给我，我把它设成默认布局。", fontSize = 12.sp, color = HintColor)
                Text(
                    text = layout.encode(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("好") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text(message, fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 数字输入框 + 步进按钮。直接输数字比拖滑块准；
 * 输入过程中不打断（失焦时才应用），否则每敲一个字符就被重排。
 */
@Composable
private fun NumberField(
    label: String,
    value: Float,
    step: Float,
    decimals: Int = 3,
    onValueChange: (Float) -> Unit,
) {
    fun format(v: Float) = "%.${decimals}f".format(v)

    var text by remember { mutableStateOf(format(value)) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(value, focused) {
        if (!focused) text = format(value)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(56.dp), fontSize = 13.sp)

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier
                .width(100.dp)
                .onFocusChanged { state ->
                    val was = focused
                    focused = state.isFocused
                    if (was && !focused) text.toFloatOrNull()?.let(onValueChange)
                },
        )

        TextButton(onClick = { onValueChange(value - step) }) { Text("－", fontSize = 15.sp) }
        TextButton(onClick = { onValueChange(value + step) }) { Text("＋", fontSize = 15.sp) }
    }
}
