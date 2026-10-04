package com.padlink.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 圆形按键。按住期间一直保持按下状态，即使手指滑出按钮范围——
 * 手柄上按住了就是按住了，跟真实手柄的手感一致。
 */
@Composable
fun PadButton(
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF2C2C2C),
    onPressChange: (Boolean) -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    onPressChange(true)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                    }

                    pressed = false
                    onPressChange(false)
                }
            }
            .drawBehind {
                drawCircle(color = if (pressed) Color(0xFF5AA9FF) else tint)
                drawCircle(
                    color = if (pressed) Color(0xFF9CCBFF) else Color(0xFF454545),
                    radius = size.minDimension / 2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.05f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (pressed) Color(0xFF10233A) else Color(0xFFD8D8D8),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
