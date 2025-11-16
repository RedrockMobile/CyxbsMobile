package com.cyxbs.components.base

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.CallSuper
import com.cyxbs.components.base.crash.CrashMonitor
import com.cyxbs.components.config.init.InitialManager
import com.cyxbs.components.init.appActivities
import com.cyxbs.components.init.appApplication
import com.cyxbs.components.init.appCurrentProcessName
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.components.utils.utils.impl.ActivityLifecycleCallbacksImpl
import java.lang.ref.WeakReference

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/26 14:01
 */
abstract class BaseApp : Application() {
  companion object {
    @SuppressLint("StaticFieldLeak")
    lateinit var baseApp: BaseApp
      private set

    /**
     * 使用 androidId 来代替设备 id
     *
     * android id 会在设备重置后还原，并且不具有唯一性
     *
     * 但都重置系统了，可以不用管这么多
     */
    @SuppressLint("HardwareIds")
    fun getAndroidID(): String {
      return Settings.Secure.getString(baseApp.contentResolver, Settings.Secure.ANDROID_ID)
    }

    /**
     * 获取设备型号
     */
    @SuppressLint("PrivateApi")
    fun getDeviceModel(): String {
      return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
  }

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    baseApp = this
    appApplication = this
    CrashMonitor.install()
    initProvider()
  }
  
  @CallSuper
  override fun onCreate() {
    super.onCreate()
    InitialManager.init(isMainProcess = appCurrentProcessName == packageName)
    initActivityManger()
  }

  /**
   * 初始化服务提供框架
   */
  abstract fun initProvider()
  
  private fun initActivityManger() {
    registerActivityLifecycleCallbacks(
      object : ActivityLifecycleCallbacksImpl {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
          appTopActivity = WeakReference(activity)
          appActivities[activity] = Unit
        }
        override fun onActivityResumed(activity: Activity) {
          if (activity !== appTopActivity.get()) {
            appTopActivity = WeakReference(activity)
          }
        }
        override fun onActivityDestroyed(activity: Activity) {
          appActivities[activity] = Unit
        }
      }
    )
  }
}