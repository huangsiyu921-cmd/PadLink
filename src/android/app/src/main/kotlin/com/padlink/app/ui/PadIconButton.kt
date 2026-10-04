package com.padlink.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

/** Xbox 手柄上那两颗小键的标准图标。 */
enum class PadIcon {
    /** Menu（Start）：三条横线。 */
    MENU,

    /** View（Back）：两个叠在一起的方框。 */
    VIEW,
}

/**
 * 带图标的圆形按键。Xbox 原厂手柄的 Start/Back 就是这两个图案，写文字反而认不出来。
 */
@Composable
fun PadIconButton(
    icon: PadIcon,
    modifier: Modifier = Modifier,
    onPressChange: (Boolean) -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
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
                val radius = size.minDimension / 2f
                val face = if (pressed) PadColors.ButtonPressed else PadColors.Button

                drawCircle(color = face, radius = radius)
                drawCircle(color = PadColors.Hairline, radius = radius, style = Stroke(radius * 0.06f))
                drawXboxIcon(
                    icon = icon,
                    color = if (pressed) PadColors.ButtonLabelPressed else PadColors.ButtonLabel,
                    background = face,
                )
            },
    )
}

/**
 * 两个图案都画成**粗线条**：图标小的时候细线会糊成一团。
 *
 * View 的两块方框还要做出前后叠压——后面那块被前面那块挡住的地方得断开，
 * 否则两条线交叉在一起，看着就是一团乱麻。
 */
private fun DrawScope.drawXboxIcon(icon: PadIcon, color: Color, background: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val extent = size.minDimension

    when (icon) {
        PadIcon.MENU -> {
            val halfWidth = extent * 0.21f
            val thickness = extent * 0.078f
            val gap = extent * 0.112f
            val corner = CornerRadius(thickness / 2f)

            listOf(-gap, 0f, gap).forEach { dy ->
                drawRoundRect(
                    color = color,
                    topLeft = Offset(center.x - halfWidth, center.y + dy - thickness / 2f),
                    size = Size(halfWidth * 2f, thickness),
                    cornerRadius = corner,
                )
            }
        }

        PadIcon.VIEW -> {
            val box = extent * 0.325f
            val shift = extent * 0.165f
            val stroke = extent * 0.078f
            val corner = CornerRadius(box * 0.16f)

            val backTopLeft = Offset(
                center.x - (box + shift) / 2f,
                center.y - (box + shift) / 2f,
            )
            val frontTopLeft = Offset(backTopLeft.x + shift, backTopLeft.y + shift)

            // 后面那块
            drawRoundRect(color, backTopLeft, Size(box, box), corner, style = Stroke(stroke))

            // 用底面颜色把前面那块的位置整个盖住，断开后面那块穿进来的线。
            drawRoundRect(
                color = background,
                topLeft = Offset(frontTopLeft.x - stroke / 2f, frontTopLeft.y - stroke / 2f),
                size = Size(box + stroke, box + stroke),
                cornerRadius = corner,
            )

            // 前面那块
            drawRoundRect(color, frontTopLeft, Size(box, box), corner, style = Stroke(stroke))
        }
    }
}
