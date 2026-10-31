package com.cyxbs.components.config.init

import com.cyxbs.components.config.log.AppLogManager
import com.cyxbs.components.config.service.allImpl
import com.cyxbs.components.config.sp.SP_PRIVACY_AGREED
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.init.InitialService

/**
 * .
 *
 * @author 985892345
 * @date 2025/11/2
 */
object InitialManager {

  private var hasInvokedPrivacyAgree = false

  private val initialServices by lazy {
    InitialService::class.allImpl().map {
      it.value.get()
    }
  }

  fun init(isMainProcess: Boolean) {
    AppLogManager.initialize(mainProcess = isMainProcess)
    // 由于android每开辟进程都会访问application的生命周期方法,所以为了保证sdk初始化无措，最好对其进行过滤。
    // 因为有些sdk的初始化不是幂等的，即多次初始化会导致进程的crash。这样就会导致一些未知的问题。
    // 所以解决方案就是对当前进程进程判断，只在main进程初始化sdk，其余进程默认不进行sdk的初始化。
    // (不排除某些sdk需要，比如友盟推送就需要在新开辟的:channel进行进行初始化)
    initialServices.forEach { it.onAllProcess() }
    if (isMainProcess){
      onMainProcess()
    }else {
      onOtherProcess()
    }
  }

  //非主进程
  private fun onOtherProcess() {
    initialServices.forEach {
      it.onOtherProcess()
    }
  }

  /** 主进程启动时恢复已保存的隐私授权，并让相关 SDK 在本次进程中初始化一次。 */
  private fun onMainProcess() {
    //不管是否同意隐私策略都调用
    initialServices.forEach {
      it.onMainProcess()
    }
    //同意了隐私策略
    if (defaultSettings.getBoolean(SP_PRIVACY_AGREED, false)) {
      tryPrivacyAgree()
    }
  }

  /** 恢复历史授权或登录成功后分发隐私回调；在当前进程中最多执行一次。 */
  fun tryPrivacyAgree() {
    defaultSettings.putBoolean(SP_PRIVACY_AGREED, true)
    AppLogManager.onPrivacyAgreed()
    if (hasInvokedPrivacyAgree) return
    hasInvokedPrivacyAgree = true
    initialServices.forEach {
      it.onPrivacyAgreed()
    }
  }

  // 取消同意隐私策略，用于重新登录
  fun cancelPrivacyAgree() {
    defaultSettings.putBoolean(SP_PRIVACY_AGREED, false)
    AppLogManager.onPrivacyWithdrawn()
    // 这里不要再设置 hasInvokedPrivacyAgree，确保 onPrivacyAgreed 在整个应用生命周期内只回调一次
  }
}
