package com.padlink.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import com.padlink.core.DPad

/**
 * 十字键·三角分键样式：四个独立的圆钮，各带一个三角。
 *
 * 同时按住两个（例如上 + 右）也能拼出斜向——所以这个样式并不比 8 方向那套差，
 * 只是视觉上更接近实体手柄的分体式十字键。
 */
@Composable
fun DPadTriangle(
    modifier: Modifier = Modifier,
    onDirection: (DPad) -> Unit = {},
) {
    val pressed = remember { mutableStateListOf<DPad>() }

    fun publish() = onDirection(combine(pressed))

    Box(modifier) {
        val third = 1f / 3f

        TriangleKey(DPad.NORTH, Modifier.align(Alignment.TopCenter).fillMaxSize(third)) { down ->
            if (down) pressed.add(DPad.NORTH) else pressed.remove(DPad.NORTH)
            publish()
        }
        TriangleKey(DPad.SOUTH, Modifier.align(Alignment.BottomCenter).fillMaxSize(third)) { down ->
            if (down) pressed.add(DPad.SOUTH) else pressed.remove(DPad.SOUTH)
            publish()
        }
        TriangleKey(DPad.WEST, Modifier.align(Alignment.CenterStart).fillMaxSize(third)) { down ->
            if (down) pressed.add(DPad.WEST) else pressed.remove(DPad.WEST)
            publish()
        }
        TriangleKey(DPad.EAST, Modifier.align(Alignment.CenterEnd).fillMaxSize(third)) { down ->
            if (down) pressed.add(DPad.EAST) else pressed.remove(DPad.EAST)
            publish()
        }
    }
}

@Composable
private fun TriangleKey(
    direction: DPad,
    modifier: Modifier,
    onPressChange: (Boolean) -> Unit,
) {
    var down by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    down = true
                    onPressChange(true)
                    first.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                    }

                    down = false
                    onPressChange(false)
                }
            }
            .drawBehind {
                val radius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(color = if (down) Color(0xFF4C8DFF) else Color(0xFF4A4A4A), radius = radius, center = center)
                drawTriangle(
                    direction = direction,
                    color = if (down) Color(0xFF10192B) else Color(0xFFEDEDED),
                    center = center,
                    radius = radius * 0.46f,
                )
            },
    )
}

/** 四个方向的组合 → 8 方向枚举。 */
internal fun combine(pressed: List<DPad>): DPad {
    val north = DPad.NORTH in pressed
    val south = DPad.SOUTH in pressed
    val west = DPad.WEST in pressed
    val east = DPad.EAST in pressed

    return when {
        north && east -> DPad.NORTH_EAST
        north && west -> DPad.NORTH_WEST
        south && east -> DPad.SOUTH_EAST
        south && west -> DPad.SOUTH_WEST
        north -> DPad.NORTH
        south -> DPad.SOUTH
        west -> DPad.WEST
        east -> DPad.EAST
        else -> DPad.NEUTRAL
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTriangle(
    direction: DPad,
    color: Color,
    center: Offset,
    radius: Float,
) {
    val path = Path()
    val half = radius * 0.9f

    when (direction) {
        DPad.NORTH -> {
            path.moveTo(center.x, center.y - radius)
            path.lineTo(center.x - half, center.y + radius * 0.7f)
            path.lineTo(center.x + half, center.y + radius * 0.7f)
        }

        DPad.SOUTH -> {
            path.moveTo(center.x, center.y + radius)
            path.lineTo(center.x - half, center.y - radius * 0.7f)
            path.lineTo(center.x + half, center.y - radius * 0.7f)
        }

        DPad.WEST -> {
            path.moveTo(center.x - radius, center.y)
            path.lineTo(center.x + radius * 0.7f, center.y - half)
            path.lineTo(center.x + radius * 0.7f, center.y + half)
        }

        else -> {
            path.moveTo(center.x + radius, center.y)
            path.lineTo(center.x - radius * 0.7f, center.y - half)
            path.lineTo(center.x - radius * 0.7f, center.y + half)
        }
    }

    path.close()
    drawPath(path, color)
}
