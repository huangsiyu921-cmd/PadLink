package com.padlink.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 圆角矩形按钮。肩键（LB/RB）和按钮式扳机（LT/RT）都用它——
 * 它们本来就是同一种东西，区别只在语义。
 */
@Composable
fun PadRectButton(
    label: String,
    modifier: Modifier = Modifier,
    labelSize: TextUnit = 15.sp,
    onPressChange: (Boolean) -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    pressed = true
                    onPressChange(true)
                    first.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                    }

                    pressed = false
                    onPressChange(false)
                }
            }
            .drawBehind {
                val corner = CornerRadius(size.height * 0.26f)
                drawRoundRect(
                    color = if (pressed) PadColors.ButtonPressed else PadColors.Button,
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = PadColors.Hairline,
                    cornerRadius = corner,
                    style = Stroke(width = size.height * 0.05f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (pressed) PadColors.ButtonLabelPressed else PadColors.ButtonLabel,
            fontSize = labelSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}
