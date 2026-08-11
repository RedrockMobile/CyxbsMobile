package com.cyxbs.components.base.webview

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import io.github.multiweb.api.WebViewController
import io.github.multiweb.extension.OriginPolicyAwareJavaScriptExecutor
import io.github.multiweb.extension.ScriptBridgeOriginPolicy
import io.github.multiweb.extension.WebViewControllerLifecycleExtension

/** 将旧桥声明的陀螺仪、加速度计数据安全回传给当前 WebView 主文档。 */
internal class AndroidWebViewSensorExtension(
  context: Context,
) : WebViewControllerLifecycleExtension {

  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val mainHandler = Handler(Looper.getMainLooper())
  private val listeners = mutableMapOf<Int, SensorEventListener>()
  private var javaScriptExecutor: OriginPolicyAwareJavaScriptExecutor? = null

  override fun onControllerAttached(controller: WebViewController) {
    javaScriptExecutor = controller as? OriginPolicyAwareJavaScriptExecutor
  }

  override fun onControllerDisposed() {
    stopSensors()
    javaScriptExecutor = null
  }

  fun startSensor(sensorId: Int): Boolean {
    val callbackName = sensorCallbackName(sensorId) ?: return false
    val manager = sensorManager ?: return false
    if (javaScriptExecutor == null) return false
    if (listeners.containsKey(sensorId)) return true

    val sensor = manager.getDefaultSensor(sensorId) ?: return false
    val listener = object : SensorEventListener {
      override fun onSensorChanged(event: SensorEvent) {
        dispatchSensorValues(callbackName, event.values)
      }

      override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    return manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL).also { registered ->
      if (registered) {
        listeners[sensorId] = listener
      }
    }
  }

  fun stopSensors() {
    val manager = sensorManager ?: return
    listeners.values.forEach(manager::unregisterListener)
    listeners.clear()
  }

  private fun dispatchSensorValues(callbackName: String, values: FloatArray) {
    val x = values.getOrNull(0)?.toSafeJavaScriptNumber() ?: return
    val y = values.getOrNull(1)?.toSafeJavaScriptNumber() ?: return
    val z = values.getOrNull(2)?.toSafeJavaScriptNumber() ?: return
    mainHandler.post {
      javaScriptExecutor?.executeJavaScript(
        script = "if (typeof window.$callbackName === 'function') window.$callbackName($x,$y,$z);",
        originPolicy = ScriptBridgeOriginPolicy.UnsafeAnyHttpOrHttps,
      )
    }
  }

  private fun sensorCallbackName(sensorId: Int): String? {
    return when (sensorId) {
      Sensor.TYPE_GYROSCOPE -> "gyroscope"
      Sensor.TYPE_ACCELEROMETER -> "accelerometer"
      else -> null
    }
  }

  private fun Float.toSafeJavaScriptNumber(): String? {
    return takeIf(Float::isFinite)?.toString()
  }
}
