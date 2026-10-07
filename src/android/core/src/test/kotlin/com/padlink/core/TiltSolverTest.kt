package com.padlink.core

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TiltSolverTest {

    /** 去掉平滑，单测解算本身；平滑单独测。 */
    private fun solver() = TiltSolver(smoothing = 1f)

    private fun deg(value: Double): Float = (value * PI / 180.0).toFloat()

    /** 角度来回转弧度有浮点误差，满舵附近别用精确比较。 */
    private fun assertAxis(expected: Float, actual: Float) = assertEquals(expected, actual, 1e-4f)

    @Test
    fun `零点姿态输出零`() {
        val tilt = solver()
        tilt.recenter(deg(30.0))

        assertEquals(0f, tilt.update(deg(30.0)))
    }

    @Test
    fun `死区内不动`() {
        val tilt = solver()
        tilt.recenter(0f)

        assertEquals(0f, tilt.update(deg(1.0)))
        assertEquals(0f, tilt.update(deg(-2.4)))
    }

    @Test
    fun `出死区立刻离开零但很小`() {
        val tilt = solver()
        tilt.recenter(0f)

        val value = tilt.update(deg(3.0))
        assertTrue(value > 0f, "应该往外走，实际 $value")
        assertTrue(value < 0.05f, "刚出死区不该跳一大截，实际 $value")
    }

    @Test
    fun `推到满角度是正一`() {
        val tilt = solver()
        tilt.recenter(0f)

        assertAxis(1f, tilt.update(deg(25.0)))
    }

    @Test
    fun `反方向是负一`() {
        val tilt = solver()
        tilt.recenter(0f)

        assertAxis(-1f, tilt.update(deg(-25.0)))
    }

    @Test
    fun `超过满角度饱和`() {
        val tilt = solver()
        tilt.recenter(0f)

        assertEquals(1f, tilt.update(deg(80.0)))
        assertEquals(-1f, tilt.update(deg(-80.0)))
    }

    @Test
    fun `输出是相对零点算的`() {
        val tilt = solver()
        tilt.recenter(deg(30.0))

        assertAxis(1f, tilt.update(deg(55.0)))
        assertAxis(-1f, tilt.update(deg(5.0)))
    }

    @Test
    fun `跨过正负一百八十不断裂`() {
        val tilt = solver()
        tilt.recenter(deg(178.0))

        // 相对零点只差 +5°，虽然绝对值跨了 180°。跨过头会让这一下直接翻成满舵。
        val value = tilt.update(deg(-177.0))
        assertTrue(value > 0f, "应该继续往正方向，实际 $value")
        assertTrue(value < 0.2f, "跨 180° 不该算成满舵，实际 $value")
    }

    @Test
    fun `中间位置线性`() {
        val tilt = solver()
        tilt.recenter(0f)

        // 死区 2.5°，满舵 25°，可用 22.5°。推到 13.75° 正好是可用行程的一半。
        val value = tilt.update(deg(13.75))
        assertAxis(0.5f, value)
    }

    @Test
    fun `平滑会让第一步只走一部分`() {
        val tilt = TiltSolver(smoothing = 0.5f)
        tilt.recenter(0f)

        assertAxis(0.5f, tilt.update(deg(25.0)))
        assertAxis(0.75f, tilt.update(deg(25.0)))
        assertAxis(0.875f, tilt.update(deg(25.0)))
    }

    @Test
    fun `reset 之后零点也清掉`() {
        val tilt = solver()
        tilt.recenter(deg(30.0))
        assertAxis(1f, tilt.update(deg(55.0)))

        tilt.reset()

        // 零点回到 0°，所以 30° 不再是中立，而是推满。
        assertEquals(0f, tilt.update(0f))
        assertAxis(1f, tilt.update(deg(30.0)))
    }
}
