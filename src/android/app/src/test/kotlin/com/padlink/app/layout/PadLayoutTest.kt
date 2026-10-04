package com.padlink.app.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 布局序列化与摆位的回归测试，全在本地 JVM 跑，不用装到手机上。
 *
 * 加它的直接原因有两个，都是真踩过的坑：
 *  1. `Default` 曾经写成 `decode(DEFAULT_TEXT)`，而 decode 内部又读 `Default.elements` ——
 *     自己读自己，类初始化时拿到 null，App **一启动就崩**。
 *  2. ABXY 的子键 dx/dy 写得比键直径还小，四个键**叠在一起**，装到真机上才看出来。
 */
class PadLayoutTest {

    /**
     * 用用户那台机器的横屏分辨率算像素坐标。
     * 重叠与否和屏幕比例强相关，换成别的比例结论可能不同——真要支持多种比例，
     * 得把这组测试参数化，现在先盯住主力机型。
     */
    private val screenWidthPx = 2664f
    private val screenHeightPx = 1200f

    @Test
    fun `默认布局能解析出来且控件齐全`() {
        val layout = PadLayout.Default

        assertTrue("默认布局不该是空的", layout.elements.isNotEmpty())
        PadElementKind.entries.forEach { kind ->
            assertNotNull("默认布局缺少 $kind", layout.element(kind))
        }
    }

    @Test
    fun `默认布局带有 ABXY 与十字键的子键`() {
        val subKeys = PadLayout.Default.subKeys

        val abxy = subKeys[PadElementKind.ABXY_GROUP].orEmpty()
        assertEquals("ABXY 应有四个键", 4, abxy.size)
        assertEquals(setOf("A", "B", "X", "Y"), abxy.map { it.label }.toSet())

        val dpad = subKeys[PadElementKind.DPAD].orEmpty()
        assertEquals("十字键应有四个方向", 4, dpad.size)
    }

    // ------------------------------------------------------------------ 摆位

    /**
     * 只查"控件中心有没有跑到屏幕外"，**不查重叠**。
     *
     * 默认布局是用户亲手调并导出的：ABXY 故意拉得很开（方便拇指分开按）、
     * LB/RT 贴着边缘，控件之间挨着甚至压一点都是有意为之。
     * 拿"不许重叠"去卡它，等于拿我的审美否定他的选择。
     */
    @Test
    fun `默认布局的控件中心都落在屏幕内`() {
        val layout = PadLayout.Default

        layout.elements.forEach { element ->
            val box = boxOf(element, layout)
            val cx = (box.left + box.right) / 2f
            val cy = (box.top + box.bottom) / 2f

            assertTrue("${box.name} 中心跑出屏幕（$cx, $cy）", cx in -1f..(screenWidthPx + 1f))
            assertTrue("${box.name} 中心跑出屏幕（$cx, $cy）", cy in -1f..(screenHeightPx + 1f))
        }
    }

    /** 控件在屏幕上的矩形（像素）。size 相对屏宽，x 相对屏宽，y 相对屏高。 */
    private fun boxOf(element: PadElement, layout: PadLayout): Box {
        val width = element.size * screenWidthPx
        val height = width * layout.aspectOf(element.kind)
        val cx = element.x * screenWidthPx
        val cy = element.y * screenHeightPx

        return Box(element.kind.name, cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2)
    }

    private data class Box(
        val name: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    // ------------------------------------------------------------------ 序列化

    @Test
    fun `encode 与 decode 往返一致`() {
        val original = PadLayout.Default
        val restored = PadLayout.decode(original.encode())

        assertEquals(original.elements.size, restored.elements.size)
        assertEquals(original.triggerStyle, restored.triggerStyle)
        assertEquals(original.dpadStyle, restored.dpadStyle)

        original.elements.forEach { element ->
            val back = restored.element(element.kind)
            assertNotNull("往返后丢了 ${element.kind}", back)
            assertEquals(element.x, back!!.x, 0.0001f)
            assertEquals(element.y, back.y, 0.0001f)
            assertEquals(element.size, back.size, 0.0001f)
            assertEquals(element.rotation, back.rotation, 0.0001f)
            assertEquals(element.enabled, back.enabled)
        }

        assertEquals(original.subKeys[PadElementKind.ABXY_GROUP], restored.subKeys[PadElementKind.ABXY_GROUP])
    }

    @Test
    fun `旧存档缺控件时用默认值补上`() {
        val partial = PadLayout.decode("LEFT_STICK,0.2,0.8,0.25,0,true")
        val stick = partial.element(PadElementKind.LEFT_STICK)

        assertNotNull(stick)
        assertEquals(0.2f, stick!!.x, 0.0001f)
        assertNotNull("缺的控件应当被补上", partial.element(PadElementKind.ABXY_GROUP))
        assertNotNull("缺的子键也应当被补上", partial.subKeys[PadElementKind.ABXY_GROUP])
    }

    @Test
    fun `读不懂的行会被跳过而不是让整个布局报废`() {
        val layout = PadLayout.decode(
            """
            LEFT_STICK,1.0,0.5,0.2,0,true
            这行是垃圾数据
            NOT_A_KIND,0.5,0.5,0.1,0,true
            RIGHT_STICK,不是数字,0.5,0.2,0,true
            """.trimIndent()
        )

        assertNotNull(layout.element(PadElementKind.LEFT_STICK))
        assertNotNull("坏行不该影响其它控件", layout.element(PadElementKind.RIGHT_STICK))
    }

    @Test
    fun `缩放与旋转会被夹在合理范围`() {
        val tiny = PadLayout.Default.resize(PadElementKind.LEFT_STICK, 0.001f)
        assertEquals(PadLayout.MIN_SIZE, tiny.element(PadElementKind.LEFT_STICK)!!.size, 0.0001f)

        val huge = PadLayout.Default.resize(PadElementKind.LEFT_STICK, 9f)
        assertEquals(PadLayout.MAX_SIZE, huge.element(PadElementKind.LEFT_STICK)!!.size, 0.0001f)

        val spun = PadLayout.Default.rotate(PadElementKind.TRIGGER_LEFT, 270f)
        assertEquals(-90f, spun.element(PadElementKind.TRIGGER_LEFT)!!.rotation, 0.0001f)
    }
}
