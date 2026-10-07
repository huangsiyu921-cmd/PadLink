package com.padlink.app.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import com.padlink.core.TiltSettings
import com.padlink.core.TiltSolver

/**
 * 手机重力 → 轴值。**转译就发生在这里**。
 *
 * 传感器数据在手机端就被解算成普通的轴值，发出去的仍是标准输入帧——
 * 所以协议、PC 端、虚拟手柄都不知道有重力这回事，Xbox 360 手柄一样能用。
 *
 * 只认 [Sensor.TYPE_GAME_ROTATION_VECTOR]（陀螺仪 + 加速度计融合，不受磁场干扰）。
 * 没有这个传感器就没有重力转向，不做加速度计兜底——那条路对线性加速度太敏感，
 * 写出来也只是"能跑但没法玩"。
 */
class TiltSource(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val solver = TiltSolver()

    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    /** 这台机器能不能用重力转向。不能用时界面要把它置灰，别让人点了没反应。 */
    val isAvailable: Boolean = sensor != null

    /** 每解算出一个新值回调一次。调用方负责写进输入状态。 */
    var onTilt: ((Float) -> Unit)? = null

    /** 手感参数。改完立刻生效，不用重新定零点。 */
    var settings: TiltSettings
        get() = solver.settings
        set(value) {
            solver.settings = value
        }

    private val rotationMatrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    /** 下次收到样本时先把零点定到那个姿态上——[recenter] 拿不到"当前姿态"，只能这样接力。 */
    @Volatile
    private var awaitingRecenter = false

    @Volatile
    private var listening = false

    /** 开始读。首次采样会顺手把零点定下来，用户不用再手动校准一次。 */
    fun start() {
        val target = sensor ?: return
        if (listening) return

        awaitingRecenter = true
        solver.reset()
        sensorManager.registerListener(this, target, SensorManager.SENSOR_DELAY_GAME)
        listening = true
    }

    fun stop() {
        if (!listening) return
        sensorManager.unregisterListener(this)
        listening = false
    }

    /** 以当前姿态为零点。换姿势后调一次。 */
    fun recenter() {
        awaitingRecenter = true
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return

        val roll = rollRadians(event.values) ?: return

        if (awaitingRecenter) {
            solver.recenter(roll)
            awaitingRecenter = false
        }
        onTilt?.invoke(solver.update(roll))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * 旋转向量 → **屏幕坐标系**下的 roll 角（绕"屏幕 Y 轴"）。
     *
     * 必须先按 [Surface] 旋转重映射：传感器给的是**设备物理坐标**，不随屏幕转。
     * 不换的话手机翻个面（`sensorLandscape` 会在两种横屏之间切）方向就反了。
     *
     * 横屏时重映射后的 Y 轴正是手机的**长轴**，绕它转就是"像打方向盘那样左右倾斜"，
     * 也就是赛车游戏要的那个角。
     */
    private fun rollRadians(rotationVector: FloatArray): Float? {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
        val (axisX, axisY) = screenAxes()

        if (!SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remapped)) return null

        // getOrientation 返回的是传进去的 values 本身（float[]），不是成败标志。
        SensorManager.getOrientation(remapped, orientation)
        return orientation[2]
    }

    /** 把设备坐标的两个轴，换算成"屏幕坐标系"里该当作 X / Y 的那两个轴。 */
    private fun screenAxes(): Pair<Int, Int> = when (displayRotation()) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int = windowManager.defaultDisplay.rotation
}
