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
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * 十字键。转成 8 方向枚举（protocol.md §5.3）——比四个独立布尔更贴合真实十字键，
 * 也不会出现"同时按下上和下"这种物理上不可能的组合。
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

        drawCircle(color = Color(0xFF1E1E1E), radius = radius, center = center)
        drawCircle(color = Color(0xFF3A3A3A), radius = radius, center = center, style = Stroke(radius * 0.06f))

        // 十字提示：上下左右四个短条
        val armColor = Color(0xFF3A3A3A)
        val arm = radius * 0.5f
        val thickness = radius * 0.14f
        drawRect(armColor, Offset(center.x - thickness / 2f, center.y - arm),
            Size(thickness, arm - thickness))
        drawRect(armColor, Offset(center.x - thickness / 2f, center.y + thickness / 2f),
            Size(thickness, arm - thickness))
        drawRect(armColor, Offset(center.x - arm, center.y - thickness / 2f),
            Size(arm - thickness, thickness))
        drawRect(armColor, Offset(center.x + thickness / 2f, center.y - thickness / 2f),
            Size(arm - thickness, thickness))

        // 当前方向高亮：画一段圆弧指向那个方向。
        if (active != DPad.NEUTRAL) {
            val sweep = 44f
            val startAngle = degreesToStartAngle(active) - sweep / 2f
            drawArc(
                color = Color(0xFF5AA9FF),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset(center.x - radius * 0.28f, center.y - radius * 0.28f),
                size = Size(radius * 0.56f, radius * 0.56f),
            )
        }

        drawCircle(color = Color(0xFF2C2C2C), radius = radius * 0.12f, center = center)
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

/** 方向 → Compose 的 drawArc 起始角（Compose 的 0° 是三点钟方向，顺时针为正）。 */
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
