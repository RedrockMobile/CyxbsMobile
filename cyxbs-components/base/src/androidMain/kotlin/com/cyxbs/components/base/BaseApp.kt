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
import com.cyxbs.components.base.utils.InitialManagerImpl
import com.cyxbs.components.init.appActivities
import com.cyxbs.components.init.appApplication
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.components.utils.coroutine.appCoroutineScope
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.NewClickEvent
import com.cyxbs.components.utils.utils.impl.ActivityLifecycleCallbacksImpl
import kotlinx.coroutines.launch
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
  
  private lateinit var mInitialManager: InitialManagerImpl

  private var enterTime:Long=0

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    baseApp = this
    appApplication = this
    CrashMonitor.install()
    initProvider()
    enterTime=System.currentTimeMillis()
  }
  
  @CallSuper
  override fun onCreate() {
    super.onCreate()
    initInitialService()
    initActivityManger()
  }

  /**
   * 初始化服务提供框架
   */
  abstract fun initProvider()
  
  private fun initInitialService() {
    mInitialManager = InitialManagerImpl(this)
    mInitialManager.init()
  }
  
  // 隐私策略同意，在登录后调用
  fun tryPrivacyAgree() {
    mInitialManager.tryPrivacyAgree()
  }

  // 取消同意隐私策略，用于重新登录
  fun cancelPrivacyAgree() {
    mInitialManager.cancelPrivacyAgree()
  }

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
          appActivities.remove(activity)
          appActivities.takeIf { it.isEmpty() }?.let {
            val exitTime=System.currentTimeMillis()
            val duration=exitTime-enterTime
            appCoroutineScope.launch {
              TrackingUtils.trackStayEvent(NewClickEvent.TIME_MOBILE_ZSCY,duration,enterTime)
            }
          }
        }
      }
    )
  }
}