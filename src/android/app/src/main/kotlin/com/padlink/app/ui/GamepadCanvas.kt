package com.padlink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padlink.app.PadLinkController
import com.padlink.app.layout.DPadStyle
import com.padlink.app.layout.PadElement
import com.padlink.app.layout.PadElementKind
import com.padlink.app.layout.PadLayout
import com.padlink.app.layout.SubKey
import com.padlink.app.layout.TriggerStyle
import com.padlink.core.DPad
import com.padlink.core.GamepadButtons

/** 编辑模式下选中的东西：整个控件，或某个控件里的一个子键。 */
sealed interface Selection {
    data class Element(val kind: PadElementKind) : Selection
    data class Sub(val kind: PadElementKind, val index: Int) : Selection
}

private val SelectedBorder = Color(0xFF3D7BFF)
private val SelectedFill = Color(0x443D7BFF)
private val DisabledBorder = Color(0x99787878)

/** 拖动判定阈值：小于这个位移就当是"点了一下"，不是拖。 */
private const val DRAG_SLOP_PX = 12f

/**
 * 按 [PadLayout] 摆放所有控件。
 *
 * [editing] 为 true 时进入布局编辑：控件只画轮廓、不响应游戏输入。
 * 点一下选中，拖一下挪位置；带子键的控件（ABXY / 三角十字）连里面的每个键都能单独选。
 */
@Composable
fun GamepadCanvas(
    controller: PadLinkController,
    layout: PadLayout,
    editing: Boolean,
    selection: Selection?,
    onSelectionChange: (Selection?) -> Unit,
    onLayoutChange: (PadLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val screenWidthPx = with(LocalDensity.current) { screenWidth.toPx() }
        val screenHeightPx = with(LocalDensity.current) { screenHeight.toPx() }

        val latestLayout by rememberUpdatedState(layout)
        val latestSelect by rememberUpdatedState(onSelectionChange)
        val latestChange by rememberUpdatedState(onLayoutChange)

        layout.elements.forEach { element ->
            // key 保证每个控件的组合位置固定：布局一变不至于把状态串到别的控件上。
            key(element.kind) {
                val elementWidth = screenWidth * element.size
                val elementHeight = elementWidth * aspectOf(element.kind, layout)
                val isSelected = selection is Selection.Element && selection.kind == element.kind

                Box(
                    modifier = Modifier
                        .offset(
                            x = screenWidth * element.x - elementWidth / 2f,
                            y = screenHeight * element.y - elementHeight / 2f,
                        )
                        .size(elementWidth, elementHeight)
                        .rotate(element.rotation)
                        .then(if (isSelected) Modifier.selectionPaint() else Modifier)
                        .then(
                            if (editing) {
                                Modifier.pointerInput(element.kind) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        var moved = Offset.Zero
                                        var dragged = false
                                        latestSelect(Selection.Element(element.kind))
                                        down.consume()

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break

                                            val delta = change.positionChange()
                                            moved += delta
                                            if (moved.getDistance() > DRAG_SLOP_PX) {
                                                dragged = true
                                                val base = latestLayout
                                                val self = base.element(element.kind)
                                                if (self != null) {
                                                    latestChange(
                                                        base.move(
                                                            element.kind,
                                                            self.x + delta.x / screenWidthPx,
                                                            self.y + delta.y / screenHeightPx,
                                                        )
                                                    )
                                                }
                                            }
                                            change.consume()
                                        }

                                        if (!dragged) latestSelect(Selection.Element(element.kind))
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    when {
                        editing && element.kind.hasSubKeys ->
                            SubKeyEditor(element.kind, layout, selection, onSelectionChange, onLayoutChange)

                        editing -> ElementOutline(element, isSelected)
                        element.enabled -> ElementContent(element, layout, controller)
                    }
                }
            }
        }
    }
}

/**
 * 编辑带子键的控件：每个小键单独点选、单独拖。
 * 拖动改的是子键相对父控件的偏移，所以整体挪位置时它们会跟着走。
 */
@Composable
private fun SubKeyEditor(
    kind: PadElementKind,
    layout: PadLayout,
    selection: Selection?,
    onSelectionChange: (Selection?) -> Unit,
    onLayoutChange: (PadLayout) -> Unit,
) {
    val keys = layout.subKeys[kind].orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        val widthPx = with(LocalDensity.current) { width.toPx() }
        val heightPx = with(LocalDensity.current) { height.toPx() }

        val latestLayout by rememberUpdatedState(layout)
        val latestSelect by rememberUpdatedState(onSelectionChange)
        val latestChange by rememberUpdatedState(onLayoutChange)

        keys.forEachIndexed { index, key ->
            val keySize = width * key.scale
            val selected = selection is Selection.Sub && selection.kind == kind && selection.index == index

            Box(
                Modifier
                    .offset(x = width * (0.5f + key.dx) - keySize / 2, y = height * (0.5f + key.dy) - keySize / 2)
                    .size(keySize)
                    .then(if (selected) Modifier.selectionPaint() else Modifier)
                    .pointerInput(kind, index) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var moved = Offset.Zero
                            latestSelect(Selection.Sub(kind, index))
                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break

                                val delta = change.positionChange()
                                moved += delta
                                if (moved.getDistance() > DRAG_SLOP_PX) {
                                    latestChange(
                                        latestLayout.updateSubKey(kind, index) {
                                            it.copy(dx = it.dx + delta.x / widthPx, dy = it.dy + delta.y / heightPx)
                                        }
                                    )
                                }
                                change.consume()
                            }

                            if (moved.getDistance() <= DRAG_SLOP_PX) latestSelect(Selection.Sub(kind, index))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                SubKeyOutline(key, selected)
            }
        }
    }
}

// ------------------------------------------------------------------ 正常模式的渲染

private fun Modifier.selectionPaint(): Modifier = drawBehind {
    val corner = CornerRadius(14f, 14f)
    drawRoundRect(color = SelectedFill, cornerRadius = corner)
    drawRoundRect(color = SelectedBorder, cornerRadius = corner, style = Stroke(width = 6f))
}

/** 每个控件的宽高比（高 / 宽）。正方形是 1。 */
private fun aspectOf(kind: PadElementKind, layout: PadLayout): Float = when (kind) {
    PadElementKind.TRIGGER_LEFT, PadElementKind.TRIGGER_RIGHT ->
        if (layout.triggerStyle == TriggerStyle.SLIDE) 2.4f else 0.64f

    PadElementKind.SHOULDER_LEFT, PadElementKind.SHOULDER_RIGHT -> 0.64f
    else -> 1f
}

@Composable
private fun ElementContent(element: PadElement, layout: PadLayout, controller: PadLinkController) {
    val input = controller.input

    when (element.kind) {
        PadElementKind.LEFT_STICK -> StickPad(Modifier.fillMaxSize()) { x, y ->
            input.leftX = x
            input.leftY = y
            controller.push()
        }

        PadElementKind.RIGHT_STICK -> StickPad(Modifier.fillMaxSize()) { x, y ->
            input.rightX = x
            input.rightY = y
            controller.push()
        }

        PadElementKind.DPAD -> when (layout.dpadStyle) {
            DPadStyle.COMBO -> DPadPad(Modifier.fillMaxSize()) { direction ->
                input.dpad = direction
                controller.push()
            }

            DPadStyle.TRIANGLE -> TriangleDpad(layout.subKeys[PadElementKind.DPAD].orEmpty()) { direction ->
                input.dpad = direction
                controller.push()
            }
        }

        PadElementKind.ABXY_GROUP -> AbxyCluster(layout.subKeys[PadElementKind.ABXY_GROUP].orEmpty()) { bit, down ->
            controller.setButton(bit, down)
        }

        PadElementKind.TRIGGER_LEFT -> TriggerOrButton(layout, "LT", Modifier.fillMaxSize()) { value ->
            input.leftTrigger = value
            controller.push()
        }

        PadElementKind.TRIGGER_RIGHT -> TriggerOrButton(layout, "RT", Modifier.fillMaxSize()) { value ->
            input.rightTrigger = value
            controller.push()
        }

        PadElementKind.SHOULDER_LEFT -> PadRectButton("LB", Modifier.fillMaxSize()) { down ->
            controller.setButton(GamepadButtons.LEFT_SHOULDER, down)
        }

        PadElementKind.SHOULDER_RIGHT -> PadRectButton("RB", Modifier.fillMaxSize()) { down ->
            controller.setButton(GamepadButtons.RIGHT_SHOULDER, down)
        }

        PadElementKind.BACK -> PadButton("Back", Modifier.fillMaxSize(), labelSize = 14.sp) { down ->
            controller.setButton(GamepadButtons.BACK, down)
        }

        PadElementKind.START -> PadButton("Start", Modifier.fillMaxSize(), labelSize = 14.sp) { down ->
            controller.setButton(GamepadButtons.START, down)
        }

        PadElementKind.GUIDE -> PadButton("Guide", Modifier.fillMaxSize(), labelSize = 12.sp) { down ->
            controller.setButton(GamepadButtons.GUIDE, down)
        }

        PadElementKind.STICK_LEFT_BUTTON -> PadButton("L3", Modifier.fillMaxSize(), labelSize = 13.sp) { down ->
            controller.setButton(GamepadButtons.LEFT_THUMB, down)
        }

        PadElementKind.STICK_RIGHT_BUTTON -> PadButton("R3", Modifier.fillMaxSize(), labelSize = 13.sp) { down ->
            controller.setButton(GamepadButtons.RIGHT_THUMB, down)
        }
    }
}

private val AbxyBits = mapOf(
    "A" to GamepadButtons.A,
    "B" to GamepadButtons.B,
    "X" to GamepadButtons.X,
    "Y" to GamepadButtons.Y,
)

/** ABXY：位置和大小全部来自布局里的子键，所以每个键都能单独挪。 */
@Composable
private fun AbxyCluster(keys: List<SubKey>, onButton: (Int, Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight

        keys.forEach { key ->
            val bit = AbxyBits[key.label] ?: return@forEach
            if (!key.enabled) return@forEach
            val keySize = width * key.scale

            Box(
                Modifier
                    .offset(x = width * (0.5f + key.dx) - keySize / 2, y = height * (0.5f + key.dy) - keySize / 2)
                    .size(keySize),
            ) {
                PadButton(key.label, Modifier.fillMaxSize()) { down -> onButton(bit, down) }
            }
        }
    }
}

private val TriangleDirections = mapOf(
    "N" to DPad.NORTH,
    "S" to DPad.SOUTH,
    "W" to DPad.WEST,
    "E" to DPad.EAST,
)

/** 三角分键：同按两个能拼出斜向，所以不比 8 方向那套差。 */
@Composable
private fun TriangleDpad(keys: List<SubKey>, onDirection: (DPad) -> Unit) {
    val pressed = remember { mutableStateListOf<DPad>() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight

        keys.forEach { key ->
            val direction = TriangleDirections[key.label] ?: return@forEach
            if (!key.enabled) return@forEach
            val keySize = width * key.scale

            Box(
                Modifier
                    .offset(x = width * (0.5f + key.dx) - keySize / 2, y = height * (0.5f + key.dy) - keySize / 2)
                    .size(keySize),
            ) {
                TriangleKey(direction, Modifier.fillMaxSize()) { down ->
                    if (down) pressed.add(direction) else pressed.remove(direction)
                    onDirection(combine(pressed))
                }
            }
        }
    }
}

@Composable
private fun TriangleKey(
    direction: DPad,
    modifier: Modifier,
    onPressChange: (Boolean) -> Unit,
) {
    var down by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    down = true
                    onPressChange(true)
                    first.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                    }

                    down = false
                    onPressChange(false)
                }
            }
            .drawBehind {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(color = if (down) PadColors.ButtonPressed else PadColors.Button, radius = radius, center = center)
                drawTriangle(
                    direction = direction,
                    color = if (down) PadColors.ButtonLabelPressed else PadColors.ButtonLabel,
                    center = center,
                    radius = radius * 0.46f,
                )
            },
    )
}

/** 四个方向 → 8 方向枚举。 */
internal fun combine(pressed: List<DPad>): DPad {
    val north = DPad.NORTH in pressed
    val south = DPad.SOUTH in pressed
    val west = DPad.WEST in pressed
    val east = DPad.EAST in pressed

    return when {
        north && east -> DPad.NORTH_EAST
        north && west -> DPad.NORTH_WEST
        south && east -> DPad.SOUTH_EAST
        south && west -> DPad.SOUTH_WEST
        north -> DPad.NORTH
        south -> DPad.SOUTH
        west -> DPad.WEST
        east -> DPad.EAST
        else -> DPad.NEUTRAL
    }
}

private fun DrawScope.drawTriangle(direction: DPad, color: Color, center: Offset, radius: Float) {
    val path = Path()
    val half = radius * 0.9f

    when (direction) {
        DPad.NORTH -> {
            path.moveTo(center.x, center.y - radius)
            path.lineTo(center.x - half, center.y + radius * 0.7f)
            path.lineTo(center.x + half, center.y + radius * 0.7f)
        }

        DPad.SOUTH -> {
            path.moveTo(center.x, center.y + radius)
            path.lineTo(center.x - half, center.y - radius * 0.7f)
            path.lineTo(center.x + half, center.y - radius * 0.7f)
        }

        DPad.WEST -> {
            path.moveTo(center.x - radius, center.y)
            path.lineTo(center.x + radius * 0.7f, center.y - half)
            path.lineTo(center.x + radius * 0.7f, center.y + half)
        }

        else -> {
            path.moveTo(center.x + radius, center.y)
            path.lineTo(center.x - radius * 0.7f, center.y - half)
            path.lineTo(center.x - radius * 0.7f, center.y + half)
        }
    }

    path.close()
    drawPath(path, color)
}

// ------------------------------------------------------------------ 编辑模式的轮廓

@Composable
private fun ElementOutline(element: PadElement, selected: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    val accent = if (element.enabled) Color(0x993D7BFF) else DisabledBorder

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (element.enabled) Color(0x223D7BFF) else Color(0x22787878), shape)
            .border(if (selected) 2.dp else 1.dp, accent, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = element.kind.label,
            color = if (element.enabled) Color(0xFFB8D4FF) else Color(0xFFAAAAAA),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SubKeyOutline(key: SubKey, selected: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val accent = if (key.enabled) Color(0xCC3D7BFF) else DisabledBorder

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (key.enabled) Color(0x333D7BFF) else Color(0x33787878), shape)
            .border(if (selected) 2.dp else 1.dp, accent, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.label,
            color = if (key.enabled) Color(0xFFE0E8FF) else Color(0xFFAAAAAA),
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

/** 扳机按布局里选的样式渲染。 */
@Composable
private fun TriggerOrButton(layout: PadLayout, label: String, modifier: Modifier, onChange: (Float) -> Unit) {
    when (layout.triggerStyle) {
        TriggerStyle.SLIDE -> TriggerSlider(modifier, label, onChange)
        TriggerStyle.BUTTON -> PadRectButton(label, modifier) { down -> onChange(if (down) 1f else 0f) }
    }
}
