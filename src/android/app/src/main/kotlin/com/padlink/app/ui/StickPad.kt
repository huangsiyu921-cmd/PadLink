package com.padlink.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.min

/**
 * 自绘摇杆。回调给的是**屏幕坐标**（y 向下为正）。
 *
 * 转成协议语义（y 向上为正）由 `PadInputState` 统一负责——全项目只在那一个地方取反。
 */
@Composable
fun StickPad(
    modifier: Modifier = Modifier,
    onChange: (x: Float, y: Float) -> Unit = { _, _ -> },
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxTravel = min(size.width, size.height) / 2f * 0.62f

                    fun track(position: Offset) {
                        val delta = position - center
                        val distance = delta.getDistance()
                        val clamped = if (distance > maxTravel && distance > 0f) {
                            delta * (maxTravel / distance)
                        } else {
                            delta
                        }
                        knob = clamped / maxTravel
                        pressed = true
                        onChange(knob.x, knob.y)
                    }

                    track(first.position)
                    first.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!change.pressed) break
                        track(change.position)
                        change.consume()
                    }

                    knob = Offset.Zero
                    pressed = false
                    onChange(0f, 0f)
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f
        val maxTravel = radius * 0.62f
        val knobRadius = radius * 0.40f

        // 底盘 → 内凹 → 摇杆头，三层就够，不加十字线（容易显得乱）。
        drawCircle(color = PadColors.StickBase, radius = radius, center = center)
        drawCircle(color = PadColors.StickWell, radius = radius * 0.88f, center = center)
        drawCircle(
            color = PadColors.Hairline,
            radius = radius,
            center = center,
            style = Stroke(radius * 0.045f),
        )
        drawCircle(
            color = if (pressed) PadColors.ButtonPressed else PadColors.StickKnob,
            radius = knobRadius,
            center = center + knob * maxTravel,
        )
    }
}
