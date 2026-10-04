package com.padlink.app.layout

/**
 * 手柄上可摆放的控件。
 * 每种控件的位置和大小都能改，样式能切换（见 [PadLayout]）。
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
}

/** 扳机样式：滑动条，或按住即满行程的按钮（EMotion 那种）。 */
enum class TriggerStyle {
    SLIDE,
    BUTTON,
}

/** 十字键样式：8 方向摇杆式，或四个独立三角分键。 */
enum class DPadStyle {
    COMBO,
    TRIANGLE,
}

/**
 * 一个控件的位置与大小。
 *
 * 坐标是**屏幕比例**（0..1，控件中心点），[size] 是相对**屏幕宽度**的比例。
 * 用比例而不是像素：换台手机不用重排，横竖屏切换也不会错位。
 */
data class PadElement(
    val kind: PadElementKind,
    val x: Float,
    val y: Float,
    val size: Float,
)

data class PadLayout(
    val elements: List<PadElement>,
    val triggerStyle: TriggerStyle = TriggerStyle.SLIDE,
    val dpadStyle: DPadStyle = DPadStyle.COMBO,
) {
    fun move(kind: PadElementKind, x: Float, y: Float): PadLayout = copy(
        elements = elements.map {
            if (it.kind == kind) it.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)) else it
        }
    )

    fun resize(kind: PadElementKind, size: Float): PadLayout = copy(
        elements = elements.map {
            if (it.kind == kind) it.copy(size = size.coerceIn(MIN_SIZE, MAX_SIZE)) else it
        }
    )

    fun encode(): String = buildString {
        appendLine("trigger=${triggerStyle.name}")
        appendLine("dpad=${dpadStyle.name}")
        elements.forEach { appendLine("${it.kind.name},${it.x},${it.y},${it.size}") }
    }

    companion object {
        const val MIN_SIZE = 0.05f
        const val MAX_SIZE = 0.45f

        /**
         * 默认布局：照着真实手柄的手感排——两个摇杆在两侧偏下（拇指自然落点），
         * 十字键和 ABXY 在两侧偏上，肩键/扳机贴左右上角，中间只放 Back/Start/Guide。
         */
        val Default = PadLayout(
            elements = listOf(
                PadElement(PadElementKind.TRIGGER_LEFT, 0.062f, 0.150f, 0.105f),
                PadElement(PadElementKind.SHOULDER_LEFT, 0.152f, 0.180f, 0.105f),
                PadElement(PadElementKind.DPAD, 0.118f, 0.430f, 0.195f),
                PadElement(PadElementKind.LEFT_STICK, 0.165f, 0.765f, 0.235f),

                PadElement(PadElementKind.TRIGGER_RIGHT, 0.938f, 0.150f, 0.105f),
                PadElement(PadElementKind.SHOULDER_RIGHT, 0.848f, 0.180f, 0.105f),
                PadElement(PadElementKind.ABXY_GROUP, 0.882f, 0.430f, 0.225f),
                PadElement(PadElementKind.RIGHT_STICK, 0.835f, 0.765f, 0.235f),

                PadElement(PadElementKind.GUIDE, 0.500f, 0.135f, 0.100f),
                PadElement(PadElementKind.BACK, 0.430f, 0.560f, 0.090f),
                PadElement(PadElementKind.START, 0.570f, 0.560f, 0.090f),

                PadElement(PadElementKind.STICK_LEFT_BUTTON, 0.048f, 0.915f, 0.080f),
                PadElement(PadElementKind.STICK_RIGHT_BUTTON, 0.952f, 0.915f, 0.080f),
            ),
        )

        /** 从 [encode] 的文本还原；有任何一行读不懂就整体退回 [Default]。 */
        fun decode(text: String): PadLayout {
            val elements = mutableListOf<PadElement>()
            var trigger = TriggerStyle.SLIDE
            var dpad = DPadStyle.COMBO

            text.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach

                val parts = line.split(',')
                when (parts[0]) {
                    "trigger" -> trigger = if (parts.size == 2) {
                        TriggerStyle.entries.firstOrNull { it.name == parts[1] } ?: TriggerStyle.SLIDE
                    } else {
                        TriggerStyle.SLIDE
                    }

                    "dpad" -> dpad = if (parts.size == 2) {
                        DPadStyle.entries.firstOrNull { it.name == parts[1] } ?: DPadStyle.COMBO
                    } else {
                        DPadStyle.COMBO
                    }

                    else -> {
                        if (parts.size != 4) return@forEach
                        val kind = PadElementKind.entries.firstOrNull { it.name == parts[0] } ?: return@forEach
                        val x = parts[1].toFloatOrNull() ?: return@forEach
                        val y = parts[2].toFloatOrNull() ?: return@forEach
                        val size = parts[3].toFloatOrNull() ?: return@forEach
                        elements += PadElement(kind, x, y, size)
                    }
                }
            }

            // 缺控件就补默认位置，免得升级后旧存档少几个键。
            val missing = Default.elements.filter { default ->
                elements.none { it.kind == default.kind }
            }

            return PadLayout(elements + missing, trigger, dpad)
        }
    }
}
