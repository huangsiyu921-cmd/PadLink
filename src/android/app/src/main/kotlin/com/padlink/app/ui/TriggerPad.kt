package com.padlink.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 扳机：竖直长条，按下的位置决定行程（顶端 0、底端 1）。
 * 触屏上这样比"按压力度"可靠得多——手指位置至少是可复现的。
 */
@Composable
fun TriggerPad(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF2C2C2C),
    onValueChange: (Float) -> Unit = {},
) {
    var value by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()

                fun track(position: Offset) {
                    value = (position.y / size.height).coerceIn(0f, 1f)
                    onValueChange(value)
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

                value = 0f
                onValueChange(0f)
            }
        },
    ) {
        val corner = CornerRadius(size.width * 0.25f)

        drawRoundRect(color = tint, cornerRadius = corner)
        drawRoundRect(
            color = Color(0xFF5AA9FF),
            topLeft = Offset(0f, size.height * (1f - value)),
            size = Size(size.width, size.height * value),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = Color(0xFF454545),
            cornerRadius = corner,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.08f),
        )
    }
}
