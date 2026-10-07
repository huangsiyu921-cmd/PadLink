package com.padlink.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padlink.app.PadLinkController
import com.padlink.app.TransportMode
import com.padlink.app.layout.DPadStyle
import com.padlink.app.layout.PadElementKind
import com.padlink.app.layout.PadLayout
import com.padlink.app.layout.TriggerStyle

private val HintColor = Color(0xFF9E9E9E)

/**
 * 弹窗里滚动区的最大高度。
 *
 * 必须给上限：`verticalScroll` 只在内容**溢出**时才滚得动，
 * 而弹窗的 text 槽高度是自适应的——不给上限，内容超出只会被裁掉，手指怎么滑都没反应。
 */
private val DialogScrollMax = 250.dp

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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = DialogScrollMax)
                    .verticalScroll(rememberScrollState()),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = DialogScrollMax)
                    .verticalScroll(rememberScrollState()),
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

                SectionTitle("重力转向")
                if (controller.tiltAvailable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = controller.tiltEnabled,
                            onClick = { controller.enableTilt(true) },
                            label = { Text("开", fontSize = 13.sp) },
                        )
                        FilterChip(
                            selected = !controller.tiltEnabled,
                            onClick = { controller.enableTilt(false) },
                            label = { Text("关", fontSize = 13.sp) },
                        )
                    }
                    TextButton(
                        onClick = { controller.recenterTilt() },
                        enabled = controller.tiltEnabled,
                    ) { Text("重定中心", fontSize = 13.sp) }

                    // 手感四件套。拖的时候实时生效，松手才落盘。
                    val tilt = controller.tiltSettings

                    TiltSlider(
                        label = "死区",
                        display = "%.1f°".format(tilt.deadZoneDeg),
                        value = tilt.deadZoneDeg,
                        range = 0f..6f,
                        onDrag = { controller.applyTiltSettings(tilt.copy(deadZoneDeg = it), persist = false) },
                        onDrop = { controller.applyTiltSettings(controller.tiltSettings, persist = true) },
                    )

                    TiltSlider(
                        label = "满舵角",
                        display = "%.0f°".format(tilt.maxAngleDeg),
                        value = tilt.maxAngleDeg,
                        range = 15f..60f,
                        onDrag = { controller.applyTiltSettings(tilt.copy(maxAngleDeg = it), persist = false) },
                        onDrop = { controller.applyTiltSettings(controller.tiltSettings, persist = true) },
                    )

                    TiltSlider(
                        label = "曲线",
                        display = curveLabel(tilt.curve),
                        value = tilt.curve,
                        range = 0.2f..3f,
                        onDrag = { controller.applyTiltSettings(tilt.copy(curve = it), persist = false) },
                        onDrop = { controller.applyTiltSettings(controller.tiltSettings, persist = true) },
                    )

                    TiltSlider(
                        label = "平滑",
                        display = "%.2f".format(tilt.smoothing),
                        value = tilt.smoothing,
                        range = 0.05f..0.8f,
                        onDrag = { controller.applyTiltSettings(tilt.copy(smoothing = it), persist = false) },
                        onDrop = { controller.applyTiltSettings(controller.tiltSettings, persist = true) },
                    )
                } else {
                    Text("这台机器没有陀螺仪", color = HintColor, fontSize = 12.sp)
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

/**
 * 布局文件：存成文件带走，或者从文件读回来。
 *
 * 走系统的 Storage Access Framework，不需要任何存储权限，存哪儿由用户决定。
 */
@Composable
fun ExportDialog(
    layout: PadLayout,
    onDismiss: () -> Unit,
    onImport: (PadLayout) -> Unit,
) {
    val context = LocalContext.current

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(layout.encode().toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val text = uri?.let { picked ->
            runCatching {
                context.contentResolver.openInputStream(picked)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
        }

        if (text != null) onImport(PadLayout.decode(text))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("布局文件", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = DialogScrollMax)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("也可以长按选中下面这段复制发我。", fontSize = 12.sp, color = HintColor)
                SelectionContainer {
                    Text(
                        text = layout.encode(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { openLauncher.launch(arrayOf("text/plain", "*/*")) }) {
                    Text("从文件导入")
                }
                TextButton(onClick = { saveLauncher.launch("padlink-layout.txt") }) {
                    Text("保存到文件")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("好") } },
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
    val focusManager = LocalFocusManager.current

    LaunchedEffect(value, focused) {
        if (!focused) text = format(value)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(56.dp), fontSize = 13.sp)

        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                // 边打边生效。之前要等失焦（点别处）才应用，用户敲完发现没反应，很别扭。
                input.toFloatOrNull()?.let(onValueChange)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .width(110.dp)
                .onFocusChanged { state ->
                    val was = focused
                    focused = state.isFocused
                    // 失焦时也应用一次，兜住"敲了但没打全"这种情况。
                    if (was && !focused) text.toFloatOrNull()?.let(onValueChange)
                },
        )

        TextButton(onClick = { onValueChange(value - step) }) { Text("－", fontSize = 15.sp) }
        TextButton(onClick = { onValueChange(value + step) }) { Text("＋", fontSize = 15.sp) }
    }
}

/**
 * 重力转向的一个手感参数。拖的时候实时生效，松手才落盘
 * （见 [PadLinkController.applyTiltSettings]）。
 */
@Composable
private fun TiltSlider(
    label: String,
    display: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onDrag: (Float) -> Unit,
    onDrop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = HintColor, fontSize = 12.sp)
            Text(display, color = HintColor, fontSize = 12.sp)
        }

        Slider(
            value = value,
            onValueChange = onDrag,
            onValueChangeFinished = onDrop,
            valueRange = range,
        )
    }
}

/** 曲线指数光看数字没概念，直接说它是"哪一头快"。 */
private fun curveLabel(curve: Float): String = when {
    curve < 0.8f -> "初段快 · %.2f".format(curve)
    curve > 1.25f -> "初段慢 · %.2f".format(curve)
    else -> "线性 · %.2f".format(curve)
}
