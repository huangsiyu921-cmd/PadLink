package com.padlink.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 协议向量一致性测试。向量文件由 `tools/gen_testvectors.py` 生成，
 * C# 端读的是同一份文件——两端必须对同一字节序列得出同一结论。
 */
class ProtocolVectorTest {

    private val file: VectorFile = loadVectorFile()

    @Test
    fun constantsMatchVectorFile() {
        assertEquals(file.ver, ProtocolConstants.VERSION.toInt())
        assertEquals(file.frameSize, ProtocolConstants.FRAME_SIZE)
        assertEquals(file.rumbleSize, ProtocolConstants.RUMBLE_SIZE)
        assertEquals("504c", file.magic)
    }

    @Test
    fun buttonBitsMatchVectorFile() {
        val bits = file.buttonBits
        assertEquals(GamepadButtons.A, bits.getValue("a"))
        assertEquals(GamepadButtons.B, bits.getValue("b"))
        assertEquals(GamepadButtons.X, bits.getValue("x"))
        assertEquals(GamepadButtons.Y, bits.getValue("y"))
        assertEquals(GamepadButtons.LEFT_SHOULDER, bits.getValue("lb"))
        assertEquals(GamepadButtons.RIGHT_SHOULDER, bits.getValue("rb"))
        assertEquals(GamepadButtons.BACK, bits.getValue("back"))
        assertEquals(GamepadButtons.START, bits.getValue("start"))
        assertEquals(GamepadButtons.LEFT_THUMB, bits.getValue("l3"))
        assertEquals(GamepadButtons.RIGHT_THUMB, bits.getValue("r3"))
        assertEquals(GamepadButtons.GUIDE, bits.getValue("guide"))
        assertEquals(GamepadButtons.TOUCHPAD, bits.getValue("touchpad"))
    }

    @Test
    fun controllerIdsMatchVectorFile() {
        val ids = file.controllers
        assertEquals(ControllerType.XBOX360.id, ids.getValue("xbox360"))
        assertEquals(ControllerType.DS4.id, ids.getValue("ds4"))
        assertEquals(ControllerType.DUAL_SENSE.id, ids.getValue("dualsense"))
        assertEquals(ControllerType.SWITCH_PRO.id, ids.getValue("switchpro"))
    }

    @Test
    fun decodeReconstructsFields() {
        for (vector in file.vectors) {
            val frame = InputFrame.decodeOrNull(hexToBytes(vector.bytes))
            assertNotNull(frame, "${vector.name}: 解码失败")

            val expected = vector.fields
            val where = vector.name
            assertEquals(expected.player, frame.player, "$where: player")
            assertEquals(expected.controller, frame.controller.wireName, "$where: controller")
            assertEquals(expected.rumbleAck, frame.rumbleAck, "$where: rumbleAck")
            assertEquals(expected.seq, frame.sequence, "$where: seq")
            assertEquals(expected.ts, frame.timestampMs, "$where: ts")
            assertEquals(expected.buttons, frame.buttons, "$where: buttons")
            assertEquals(expected.dpad, frame.dpad.value, "$where: dpad")
            assertEquals(expected.lx.toShort(), frame.leftX, "$where: lx")
            assertEquals(expected.ly.toShort(), frame.leftY, "$where: ly")
            assertEquals(expected.rx.toShort(), frame.rightX, "$where: rx")
            assertEquals(expected.ry.toShort(), frame.rightY, "$where: ry")
            assertEquals(expected.lt, frame.leftTrigger, "$where: lt")
            assertEquals(expected.rt, frame.rightTrigger, "$where: rt")
        }
    }

    @Test
    fun encodeReproducesVectorBytes() {
        for (vector in file.vectors) {
            val expected = hexToBytes(vector.bytes)
            val frame = InputFrame.decodeOrNull(expected)
            assertNotNull(frame, "${vector.name}: 解码失败")
            assertEquals(vector.bytes, bytesToHex(frame.encode()), "${vector.name}: 编码结果与向量不一致")
        }
    }

    @Test
    fun encodeIntoReusedBufferMatchesAllocatingEncoder() {
        for (vector in file.vectors) {
            val frame = InputFrame.decodeOrNull(hexToBytes(vector.bytes))
            assertNotNull(frame, "${vector.name}: 解码失败")

            val reused = ByteArray(ProtocolConstants.FRAME_SIZE)
            frame.encodeInto(reused)
            assertTrue(reused.contentEquals(frame.encode()), "${vector.name}: encodeInto 与 encode 不一致")
        }
    }

    @Test
    fun invalidFramesAreRejected() {
        for (vector in file.invalid) {
            val bytes = hexToBytes(vector.bytes)
            assertNull(InputFrame.decodeOrNull(bytes), "${vector.name}: 本应被拒绝")
        }
    }

    @Test
    fun invalidFramesReportExpectedError() {
        val expected = mapOf(
            "bad_magic" to ProtocolError.BAD_MAGIC,
            "short_frame" to ProtocolError.BAD_SIZE,
            "long_frame" to ProtocolError.BAD_SIZE,
            "bad_reserved" to ProtocolError.RESERVED_NOT_ZERO,
            "bad_ver" to ProtocolError.UNSUPPORTED_VERSION,
            "bad_controller" to ProtocolError.UNKNOWN_CONTROLLER,
        )
        for (vector in file.invalid) {
            val error = runCatching { InputFrame.decode(hexToBytes(vector.bytes)) }
                .exceptionOrNull()
                ?.let { (it as? ProtocolException)?.error }
            assertEquals(expected.getValue(vector.name), error, "${vector.name}: 错误分类不一致")
        }
    }

    @Test
    fun roundTripPreservesEveryField() {
        val random = kotlin.random.Random(20261004)
        repeat(2000) {
            val frame = InputFrame(
                controller = ControllerType.entries[random.nextInt(ControllerType.entries.size)],
                player = random.nextInt(4),
                rumbleAck = random.nextBoolean(),
                sequence = random.nextLong(0x1_0000_0000L),
                timestampMs = random.nextLong(0x1_0000_0000L),
                buttons = random.nextInt(0, 1 shl 16),
                dpad = DPad.entries[random.nextInt(DPad.entries.size)],
                leftX = random.nextInt(-32768, 32768).toShort(),
                leftY = random.nextInt(-32768, 32768).toShort(),
                rightX = random.nextInt(-32768, 32768).toShort(),
                rightY = random.nextInt(-32768, 32768).toShort(),
                leftTrigger = random.nextInt(0, 32768),
                rightTrigger = random.nextInt(0, 32768),
            )
            assertEquals(frame, InputFrame.decode(frame.encode()), "往返后字段不一致")
        }
    }

    @Test
    fun rumbleFrameMatchesVector() {
        val expected = hexToBytes(file.rumble.bytes)
        val frame = RumbleFrame(player = file.rumble.player, left = file.rumble.left, right = file.rumble.right)

        assertEquals(file.rumble.bytes, bytesToHex(frame.encode()))
        assertEquals(frame, RumbleFrame.decodeOrNull(expected))
    }

    @Test
    fun rumbleFrameRejectsInputFrameBytes() {
        val inputFrame = InputFrame.neutral().encode()
        assertNull(RumbleFrame.decodeOrNull(inputFrame), "输入帧不应被当成震动帧接受")
    }
}

// ------------------------------------------------------------------ 向量文件模型

@Serializable
data class VectorFile(
    val protocol: String,
    val ver: Int,
    @SerialName("frame_size") val frameSize: Int,
    @SerialName("rumble_size") val rumbleSize: Int,
    val magic: String,
    @SerialName("button_bits") val buttonBits: Map<String, Int>,
    val controllers: Map<String, Int>,
    val vectors: List<Vector>,
    val invalid: List<InvalidVector>,
    val rumble: RumbleVector,
)

@Serializable
data class Vector(
    val name: String,
    val desc: String,
    val bytes: String,
    val fields: Fields,
)

@Serializable
data class Fields(
    val player: Int,
    val controller: String,
    @SerialName("rumble_ack") val rumbleAck: Boolean,
    val seq: Long,
    val ts: Long,
    val buttons: Int,
    val dpad: Int,
    val lx: Int,
    val ly: Int,
    val rx: Int,
    val ry: Int,
    val lt: Int,
    val rt: Int,
)

@Serializable
data class InvalidVector(val name: String, val desc: String, val bytes: String)

@Serializable
data class RumbleVector(
    val desc: String,
    val bytes: String,
    val player: Int,
    val left: Int,
    val right: Int,
)

private val json = Json { ignoreUnknownKeys = true }

private fun loadVectorFile(): VectorFile {
    val stream = ProtocolVectorTest::class.java.getResourceAsStream("/testvectors/frames.json")
        ?: error("找不到 /testvectors/frames.json——processTestResources 应把它从 protocol/testvectors 复制进来")
    return json.decodeFromString(stream.readBytes().decodeToString())
}

private fun hexToBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

private fun bytesToHex(bytes: ByteArray): String =
    buildString(bytes.size * 2) { bytes.forEach { append("%02x".format(it)) } }
