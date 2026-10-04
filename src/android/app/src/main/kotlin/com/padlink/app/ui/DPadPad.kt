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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.padlink.core.DPad
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * 十字键·8 方向样式。转成 8 方向枚举（protocol.md §5.3），
 * 比四个独立布尔更贴合真实十字键，也不会出现"同时按下上和下"这种组合。
 *
 * 按下反馈是一片从圆心扇出去的 45° 扇形，加中心点变色——之前那圈小弧太不显眼了。
 */
@Composable
fun DPadPad(
    modifier: Modifier = Modifier,
    onDirection: (DPad) -> Unit = {},
) {
    var active by remember { mutableStateOf(DPad.NEUTRAL) }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val deadZone = min(size.width, size.height) * 0.16f

                    fun track(position: Offset) {
                        val dx = position.x - center.x
                        val dy = position.y - center.y
                        val direction = if (hypot(dx, dy) < deadZone) {
                            DPad.NEUTRAL
                        } else {
                            // 屏幕 y 向下为正，取负换成"上为正"再算角度。
                            directionFromDegrees(Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())))
                        }
                        active = direction
                        onDirection(direction)
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

                    active = DPad.NEUTRAL
                    onDirection(DPad.NEUTRAL)
                }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f

        drawCircle(color = PadColors.StickBase, radius = radius, center = center)

        // 按下方向的扇形，垫在内圈下面，看起来像外环亮了一格。
        if (active != DPad.NEUTRAL) {
            drawArc(
                color = Color(0xFF3D7BFF),
                startAngle = degreesToStartAngle(active) - 22.5f,
                sweepAngle = 45f,
                useCenter = true,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
            )
        }

        drawCircle(color = Color(0xFF262626), radius = radius * 0.62f, center = center)

        // 四个方向的臂，提示这是个十字键。
        val arm = radius * 0.5f
        val thickness = radius * 0.16f
        val armColor = Color(0xFF3A3A3A)
        drawRect(armColor, Offset(center.x - thickness / 2f, center.y - arm), Size(thickness, arm - thickness))
        drawRect(armColor, Offset(center.x - thickness / 2f, center.y + thickness / 2f), Size(thickness, arm - thickness))
        drawRect(armColor, Offset(center.x - arm, center.y - thickness / 2f), Size(arm - thickness, thickness))
        drawRect(armColor, Offset(center.x + thickness / 2f, center.y - thickness / 2f), Size(arm - thickness, thickness))

        drawCircle(
            color = if (active != DPad.NEUTRAL) Color(0xFF8FC0FF) else Color(0xFF3F3F3F),
            radius = radius * 0.15f,
            center = center,
        )
        drawCircle(color = PadColors.Hairline, radius = radius, center = center, style = Stroke(radius * 0.05f))
    }
}

/** 角度（0=东，逆时针为正）→ 8 方向。 */
internal fun directionFromDegrees(degrees: Double): DPad {
    val normalized = ((degrees + 22.5) % 360.0 + 360.0) % 360.0
    return when ((normalized / 45.0).toInt() % 8) {
        0 -> DPad.EAST
        1 -> DPad.NORTH_EAST
        2 -> DPad.NORTH
        3 -> DPad.NORTH_WEST
        4 -> DPad.WEST
        5 -> DPad.SOUTH_WEST
        6 -> DPad.SOUTH
        else -> DPad.SOUTH_EAST
    }
}

/** 方向 → drawArc 的起始角（Compose 的 0° 是三点钟方向，顺时针为正）。 */
private fun degreesToStartAngle(direction: DPad): Float = when (direction) {
    DPad.NORTH -> -90f
    DPad.NORTH_EAST -> -45f
    DPad.EAST -> 0f
    DPad.SOUTH_EAST -> 45f
    DPad.SOUTH -> 90f
    DPad.SOUTH_WEST -> 135f
    DPad.WEST -> 180f
    DPad.NORTH_WEST -> -135f
    DPad.NEUTRAL -> 0f
}
