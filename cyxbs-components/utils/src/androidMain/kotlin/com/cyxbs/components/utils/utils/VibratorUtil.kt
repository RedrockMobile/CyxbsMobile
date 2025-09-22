package com.cyxbs.components.utils.utils

import android.content.Context
import android.os.*
import android.os.Vibrator
import com.cyxbs.components.init.appContext

/**
 * 用于触发震动的工具类
 *
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/2/2 10:24
 */
object VibratorUtil {
  
  /**
   * 根据不同的 Android 版本调用不同的震动方法
   */
  fun start(milliseconds: Long) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vibrator = appContext.getSystemService(VibratorManager::class.java)
      vibrator.vibrate(
        CombinedVibration.createParallel(
          VibrationEffect.createOneShot(
            milliseconds,
            VibrationEffect.DEFAULT_AMPLITUDE
          )
        )
      )
    } else {
      val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        vibrator.vibrate(
          VibrationEffect.createOneShot(
            milliseconds,
            VibrationEffect.DEFAULT_AMPLITUDE
          )
        )
      } else {
        vibrator.vibrate(milliseconds)
      }
    }
  }
}