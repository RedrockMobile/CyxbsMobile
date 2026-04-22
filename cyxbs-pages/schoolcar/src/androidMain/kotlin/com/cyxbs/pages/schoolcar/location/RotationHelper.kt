package com.cyxbs.pages.schoolcar.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.cyxbs.components.init.appContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**  
 * description ： 角度信息的获取由系统的旋转传感器提供
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/25 20:59
 */
class RotationHelper : SensorEventListener {
	private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
	private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

	private val _rotationFlow = MutableStateFlow(0f)
	val rotationFlow: StateFlow<Float> = _rotationFlow.asStateFlow()

	private val rotationMatrix = FloatArray(9)
	private val orientationAngles = FloatArray(3)
	private var lastValue = 0f

	fun start() {
		rotationSensor?.let {
			sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
		}
	}

	fun stop() {
		sensorManager.unregisterListener(this)
	}

	override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
	}

	override fun onSensorChanged(event: SensorEvent?) {
		if (event == null) return
		if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
			SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
			SensorManager.getOrientation(rotationMatrix, orientationAngles)
			val azimuthRadians = orientationAngles[0]
			var azimuthDegrees = Math.toDegrees(azimuthRadians.toDouble()).toFloat()
			if (azimuthDegrees < 0.0f) {
				azimuthDegrees += 360.0f
			}
			if (abs(azimuthDegrees - lastValue) > 1.5f) {
				lastValue = azimuthDegrees
				_rotationFlow.value = azimuthDegrees
			}
		}
	}
}