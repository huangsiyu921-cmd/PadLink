package com.padlink.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 扳机·滑动条样式。
 *
 * 填充**跟着手指走**：手指从顶端往下划，蓝色从顶部按比例盖下来。
 * （之前是从底部往上顶，方向跟手指相反，手感很别扭。）
 */
@Composable
fun TriggerSlider(
    modifier: Modifier = Modifier,
    label: String = "",
    onChange: (Float) -> Unit = {},
) {
    var value by remember { mutableFloatStateOf(0f) }

    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Canvas(
            modifier = Modifier.matchParentSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()

                    fun track(position: Offset) {
                        value = (position.y / size.height).coerceIn(0f, 1f)
                        onChange(value)
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
                    onChange(0f)
                }
            },
        ) {
            val corner = CornerRadius(size.width * 0.28f)

            drawRoundRect(color = Color(0xFF2C2C2C), cornerRadius = corner)
            // 从顶部往下盖：手指在哪，蓝色边就到哪。
            drawRoundRect(
                color = Color(0xFF4C8DFF),
                size = Size(size.width, size.height * value),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = Color(0xFF4A4A4A),
                cornerRadius = corner,
                style = Stroke(width = size.width * 0.09f),
            )
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = Color(0xFFEDEDED),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
