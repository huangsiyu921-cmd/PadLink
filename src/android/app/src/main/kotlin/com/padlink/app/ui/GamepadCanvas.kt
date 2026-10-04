package com.padlink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padlink.app.PadLinkController
import com.padlink.app.layout.DPadStyle
import com.padlink.app.layout.PadElement
import com.padlink.app.layout.PadElementKind
import com.padlink.app.layout.PadLayout
import com.padlink.app.layout.TriggerStyle
import com.padlink.core.GamepadButtons

/**
 * 按 [PadLayout] 摆放所有控件。
 *
 * [editing] 为 true 时进入布局编辑：控件只画轮廓、不响应游戏输入，
 * 单指拖动挪位置、双指捏合改大小。
 */
@Composable
fun GamepadCanvas(
    controller: PadLinkController,
    layout: PadLayout,
    editing: Boolean,
    onLayoutChange: (PadLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val density = LocalDensity.current
        val widthPx = with(density) { screenWidth.toPx() }
        val heightPx = with(density) { screenHeight.toPx() }

        val latestLayout by rememberUpdatedState(layout)
        val latestOnChange by rememberUpdatedState(onLayoutChange)

        layout.elements.forEach { element ->
            // key 保证每个控件的组合位置固定：布局一变不至于把状态串到别的控件上。
            key(element.kind) {
                val elementWidth = screenWidth * element.size
                val elementHeight = elementWidth * aspectOf(element.kind, layout)

                Box(
                    modifier = Modifier
                        .offset(
                            x = screenWidth * element.x - elementWidth / 2f,
                            y = screenHeight * element.y - elementHeight / 2f,
                        )
                        .size(elementWidth, elementHeight)
                        .then(
                            if (editing) {
                                Modifier.pointerInput(element.kind) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val base = latestLayout
                                        val self = base.elements
                                            .firstOrNull { it.kind == element.kind }
                                            ?: return@detectTransformGestures

                                        var updated = base.move(
                                            element.kind,
                                            self.x + pan.x / widthPx,
                                            self.y + pan.y / heightPx,
                                        )
                                        if (zoom != 1f) {
                                            updated = updated.resize(element.kind, self.size * zoom)
                                        }
                                        latestOnChange(updated)
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    if (editing) {
                        ElementOutline(element)
                    } else {
                        ElementContent(element, layout, controller)
                    }
                }
            }
        }
    }
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

            DPadStyle.TRIANGLE -> DPadTriangle(Modifier.fillMaxSize()) { direction ->
                input.dpad = direction
                controller.push()
            }
        }

        PadElementKind.ABXY_GROUP -> AbxyCluster { bit, pressed ->
            controller.setButton(bit, pressed)
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

/** 扳机按布局里选的样式渲染。 */
@Composable
private fun TriggerOrButton(
    layout: PadLayout,
    label: String,
    modifier: Modifier,
    onChange: (Float) -> Unit,
) {
    when (layout.triggerStyle) {
        TriggerStyle.SLIDE -> TriggerSlider(modifier, label, onChange)
        TriggerStyle.BUTTON -> PadRectButton(label, modifier) { down -> onChange(if (down) 1f else 0f) }
    }
}

/** ABXY 菱形排布。位置固定，标签按 Xbox 叫法——换 DS4 只改标签，协议不动。 */
@Composable
private fun AbxyCluster(onButton: (Int, Boolean) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        PadButton("Y", Modifier.align(Alignment.TopCenter).fillMaxSize(0.46f)) { onButton(GamepadButtons.Y, it) }
        PadButton("X", Modifier.align(Alignment.CenterStart).fillMaxSize(0.46f)) { onButton(GamepadButtons.X, it) }
        PadButton("B", Modifier.align(Alignment.CenterEnd).fillMaxSize(0.46f)) { onButton(GamepadButtons.B, it) }
        PadButton("A", Modifier.align(Alignment.BottomCenter).fillMaxSize(0.46f)) { onButton(GamepadButtons.A, it) }
    }
}

@Composable
private fun ElementOutline(element: PadElement) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x223D7BFF), shape)
            .border(1.dp, Color(0x993D7BFF), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = element.kind.label,
            color = Color(0xFFB8D4FF),
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}
