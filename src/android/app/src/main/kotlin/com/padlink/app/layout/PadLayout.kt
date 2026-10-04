package com.padlink.app.layout

/**
 * 手柄上可摆放的控件。位置、大小、旋转都能改。
 */
enum class PadElementKind {
    LEFT_STICK,
    RIGHT_STICK,
    DPAD,
    ABXY_GROUP,
    TRIGGER_LEFT,
    TRIGGER_RIGHT,
    SHOULDER_LEFT,
    SHOULDER_RIGHT,
    BACK,
    START,
    GUIDE,
    STICK_LEFT_BUTTON,
    STICK_RIGHT_BUTTON,
    ;

    val label: String
        get() = when (this) {
            LEFT_STICK -> "左摇杆"
            RIGHT_STICK -> "右摇杆"
            DPAD -> "十字键"
            ABXY_GROUP -> "ABXY"
            TRIGGER_LEFT -> "LT"
            TRIGGER_RIGHT -> "RT"
            SHOULDER_LEFT -> "LB"
            SHOULDER_RIGHT -> "RB"
            BACK -> "Back"
            START -> "Start"
            GUIDE -> "Guide"
            STICK_LEFT_BUTTON -> "L3"
            STICK_RIGHT_BUTTON -> "R3"
        }

    /** 这个控件是否由若干可以单独调整的子键组成。 */
    val hasSubKeys: Boolean
        get() = this == ABXY_GROUP || this == DPAD
}

/** 扳机样式：滑动条，或按住即满行程的按钮。 */
enum class TriggerStyle { SLIDE, BUTTON }

/** 十字键样式：8 方向摇杆式，或四个独立三角分键。 */
enum class DPadStyle { COMBO, TRIANGLE }

/**
 * 一个控件的位置、大小、旋转。
 *
 * 坐标是**屏幕比例**（0..1，控件中心点），[size] 是相对**屏幕宽度**的比例。
 * 用比例而不是像素：换台手机不用重排。
 */
data class PadElement(
    val kind: PadElementKind,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float = 0f,
    val enabled: Boolean = true,
)

/**
 * 子键：ABXY 或三角十字里的单个按键。
 * [dx] / [dy] 是相对**父控件**的偏移（-0.5..0.5），[scale] 是相对父控件的直径比例。
 */
data class SubKey(
    val label: String,
    val dx: Float,
    val dy: Float,
    val scale: Float,
    val enabled: Boolean = true,
)

data class PadLayout(
    val elements: List<PadElement>,
    val subKeys: Map<PadElementKind, List<SubKey>> = emptyMap(),
    val triggerStyle: TriggerStyle = TriggerStyle.SLIDE,
    val dpadStyle: DPadStyle = DPadStyle.COMBO,
) {
    fun element(kind: PadElementKind): PadElement? = elements.firstOrNull { it.kind == kind }

    /**
     * 控件的宽高比（高 / 宽）。正方形是 1。
     *
     * 放在数据模型上而不是渲染层：摆位时要靠它算占屏幕多大，测试也要用它验证有没有重叠。
     */
    fun aspectOf(kind: PadElementKind): Float = when (kind) {
        PadElementKind.TRIGGER_LEFT, PadElementKind.TRIGGER_RIGHT ->
            if (triggerStyle == TriggerStyle.SLIDE) 2.4f else 0.64f

        PadElementKind.SHOULDER_LEFT, PadElementKind.SHOULDER_RIGHT -> 0.64f
        else -> 1f
    }

    fun move(kind: PadElementKind, x: Float, y: Float): PadLayout = update(kind) {
        it.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f))
    }

    fun resize(kind: PadElementKind, size: Float): PadLayout = update(kind) {
        it.copy(size = size.coerceIn(MIN_SIZE, MAX_SIZE))
    }

    fun rotate(kind: PadElementKind, degrees: Float): PadLayout = update(kind) {
        it.copy(rotation = ((degrees + 180f).mod(360f)) - 180f)
    }

    fun toggleEnabled(kind: PadElementKind): PadLayout = update(kind) { it.copy(enabled = !it.enabled) }

    private fun update(kind: PadElementKind, transform: (PadElement) -> PadElement): PadLayout =
        copy(elements = elements.map { if (it.kind == kind) transform(it) else it })

    fun updateSubKey(kind: PadElementKind, index: Int, transform: (SubKey) -> SubKey): PadLayout {
        val keys = subKeys[kind] ?: return this
        if (index !in keys.indices) return this
        return copy(subKeys = subKeys + (kind to keys.mapIndexed { i, key -> if (i == index) transform(key) else key }))
    }

    /**
     * 导出为一段可读文本。用户在设置里点「导出」拿到它，直接贴回来就能固化成默认布局，
     * 所以格式要稳定、可整块替换。
     */
    fun encode(): String = buildString {
        appendLine("# PadLink layout")
        elements.forEach { element ->
            appendLine(
                "${element.kind.name},${element.x},${element.y},${element.size}," +
                    "${element.rotation},${element.enabled}"
            )
        }
        appendLine("trigger=${triggerStyle.name}")
        appendLine("dpad=${dpadStyle.name}")
        subKeys.forEach { (kind, keys) ->
            keys.forEach { key ->
                appendLine("sub,${kind.name},${key.label},${key.dx},${key.dy},${key.scale},${key.enabled}")
            }
        }
    }

    companion object {
        const val MIN_SIZE = 0.05f
        const val MAX_SIZE = 0.45f

        /**
         * 默认布局。刻意写成文本而不是 Kotlin 构造：用户调好导出一段文本发回来，
         * 这里整块替换即可，不用改代码结构。
         *
         * **这份是用户亲手调的**（2026-10-04 导出后固化），不是算出来的——
         * 所以别拿"控件不能重叠""必须对齐"之类的规则去改它。相邻控件留多少、
         * 哪个键偏一点，都是手感的取舍。
         */
        private const val DEFAULT_TEXT = """
# PadLink layout
TRIGGER_LEFT,0.18122892,0.050427023,0.06,10.0,true
SHOULDER_LEFT,0.33180705,4.39803E-5,0.095,0.0,true
DPAD,0.23675257,0.42976856,0.155,0.0,true
LEFT_STICK,0.2,0.82,0.15,0.0,true
TRIGGER_RIGHT,0.76028067,0.059223965,0.07,-15.0,true
SHOULDER_RIGHT,0.6323053,0.09673674,0.095,0.0,true
ABXY_GROUP,0.8770372,0.379462,0.16,0.0,true
RIGHT_STICK,0.8,0.82,0.15,0.0,true
GUIDE,0.5,0.19,0.085,0.0,true
BACK,0.43,0.56,0.08,0.0,true
START,0.57,0.56,0.08,0.0,true
STICK_LEFT_BUTTON,0.048,0.92,0.07,0.0,true
STICK_RIGHT_BUTTON,0.952,0.92,0.07,0.0,true
trigger=SLIDE
dpad=COMBO
sub,ABXY_GROUP,Y,-0.10017011,-0.21076281,0.5,true
sub,ABXY_GROUP,A,0.22078502,-0.9530263,0.665,true
sub,ABXY_GROUP,X,-0.79511386,0.19147165,0.45499995,true
sub,ABXY_GROUP,B,0.4397971,0.094813,0.44999996,true
sub,DPAD,N,0.0,-0.33,0.33,true
sub,DPAD,S,0.0,0.33,0.33,true
sub,DPAD,W,-0.33,0.0,0.33,true
sub,DPAD,E,0.33,0.0,0.33,true
"""

        /**
         * 纯解析：只认文本里的东西，**不做任何"补默认"**。
         *
         * 拆成独立函数是必须的——[Default] 就是靠它算出来的。如果解析过程里再去读 [Default]，
         * 就会撞上"类还在初始化、字段还是 null"，直接 NullPointerException（踩过：App 一启动就崩）。
         */
        private fun parse(text: String): PadLayout {
            val elements = mutableListOf<PadElement>()
            val subKeys = mutableMapOf<PadElementKind, MutableList<SubKey>>()
            var trigger = TriggerStyle.SLIDE
            var dpad = DPadStyle.COMBO

            text.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val parts = line.split(',')

                when {
                    parts[0] == "trigger" && parts.size == 2 ->
                        trigger = TriggerStyle.entries.firstOrNull { it.name == parts[1] } ?: trigger

                    parts[0] == "dpad" && parts.size == 2 ->
                        dpad = DPadStyle.entries.firstOrNull { it.name == parts[1] } ?: dpad

                    parts[0] == "sub" && parts.size >= 6 -> {
                        val kind = PadElementKind.entries.firstOrNull { it.name == parts[1] } ?: return@forEach
                        val dx = parts[3].toFloatOrNull() ?: return@forEach
                        val dy = parts[4].toFloatOrNull() ?: return@forEach
                        val scale = parts[5].toFloatOrNull() ?: return@forEach
                        val enabled = parts.getOrNull(6)?.toBooleanStrictOrNull() ?: true

                        subKeys.getOrPut(kind) { mutableListOf() } += SubKey(parts[2], dx, dy, scale, enabled)
                    }

                    parts.size >= 4 -> {
                        val kind = PadElementKind.entries.firstOrNull { it.name == parts[0] } ?: return@forEach
                        val x = parts[1].toFloatOrNull() ?: return@forEach
                        val y = parts[2].toFloatOrNull() ?: return@forEach
                        val size = parts[3].toFloatOrNull() ?: return@forEach
                        val rotation = parts.getOrNull(4)?.toFloatOrNull() ?: 0f
                        val enabled = parts.getOrNull(5)?.toBooleanStrictOrNull() ?: true

                        elements += PadElement(kind, x, y, size, rotation, enabled)
                    }
                }
            }

            return PadLayout(elements, subKeys, trigger, dpad)
        }

        val Default: PadLayout = parse(DEFAULT_TEXT)

        /**
         * 解析 [encode] 的输出，供读存档用。
         * 单行读不懂就跳过；缺的控件用 [Default] 里同名的位置补上——以后加新键时旧存档不至于少几个键。
         */
        fun decode(text: String): PadLayout {
            val parsed = parse(text)

            val missing = Default.elements.filter { def -> parsed.elements.none { it.kind == def.kind } }
            val mergedSubKeys = Default.subKeys.mapValues { (kind, defaults) ->
                parsed.subKeys[kind] ?: defaults
            } + parsed.subKeys.filterKeys { it !in Default.subKeys }

            return parsed.copy(elements = parsed.elements + missing, subKeys = mergedSubKeys)
        }
    }
}
