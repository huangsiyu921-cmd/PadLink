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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.min

/**
 * 自绘摇杆。返回方向是**屏幕坐标**（y 向下为正）；转成协议坐标（y 向上为正）由调用方负责。
 *
 * 手柄本体全部用 Canvas 自绘，不依赖 Material 组件——Material 只用在外围界面
 * （连接设置、布局管理）。见 docs/02 §4。
 */
@Composable
fun StickPad(
    modifier: Modifier = Modifier,
    onChange: (x: Float, y: Float) -> Unit,
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = min(size.width, size.height) / 2f
                    val trackRadius = maxRadius * 0.72f

                    fun track(position: Offset) {
                        val delta = position - center
                        val distance = delta.getDistance()
                        val clamped = if (distance > trackRadius && distance > 0f) {
                            delta * (trackRadius / distance)
                        } else {
                            delta
                        }
                        knob = clamped / trackRadius
                        pressed = true
                        onChange(knob.x, knob.y)
                    }

                    track(down.position)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
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
        val maxRadius = min(size.width, size.height) / 2f
        val trackRadius = maxRadius * 0.72f
        val knobRadius = maxRadius * 0.28f

        drawCircle(
            color = Color(0xFF3A3A3A),
            radius = trackRadius,
            center = center,
            style = Stroke(width = maxRadius * 0.06f),
        )
        drawCircle(color = Color(0xFF1E1E1E), radius = trackRadius, center = center)

        // 十字参考线
        val lineColor = Color(0xFF2E2E2E)
        drawLine(lineColor, Offset(center.x - trackRadius, center.y), Offset(center.x + trackRadius, center.y))
        drawLine(lineColor, Offset(center.x, center.y - trackRadius), Offset(center.x, center.y + trackRadius))

        drawCircle(
            color = if (pressed) Color(0xFF5AA9FF) else Color(0xFF6E6E6E),
            radius = knobRadius,
            center = center + knob * trackRadius,
        )
    }
}
