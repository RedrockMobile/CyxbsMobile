package com.cyxbs.components.base.utils

import android.app.Application
import androidx.core.content.edit
import com.cyxbs.components.config.sp.SP_PRIVACY_AGREED
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.components.init.InitialManager
import com.cyxbs.components.init.InitialService
import com.cyxbs.components.init.appCurrentProcessName
import com.cyxbs.components.config.service.allImpl

/**
 * BaseApp 不能直接实现于 InitialManager，不然会导致其他模块缺失 app_init 依赖
 *
 * @author 985892345
 * @date 2023/10/29 20:44
 */
internal class InitialManagerImpl(
  override val application: Application
) : InitialManager {

  private var hasPrivacyAgree = false

  private val initialServices by lazy {
    InitialService::class.allImpl().map {
      it.value.get()
    }
  }

  fun init() {
    // 由于android每开辟进程都会访问application的生命周期方法,所以为了保证sdk初始化无措，最好对其进行过滤。
    // 因为有些sdk的初始化不是幂等的，即多次初始化会导致进程的crash。这样就会导致一些未知的问题。
    // 所以解决方案就是对当前进程进程判断，只在main进程初始化sdk，其余进程默认不进行sdk的初始化。
    // (不排除某些sdk需要，比如友盟推送就需要在新开辟的:channel进行进行初始化)
    initialServices.forEach { it.onAllProcess(this) }
    if (isMainProcess){
      onMainProcess()
    }else {
      onOtherProcess()
    }
  }

  override val currentProcessName: String
    get() = appCurrentProcessName

  //非主进程
  private fun onOtherProcess() {
    initialServices.forEach {
      it.onOtherProcess(this)
    }
  }

  //主进程
  private fun onMainProcess() {
    //不管是否同意隐私策略都调用
    initialServices.forEach {
      it.onMainProcess(this)
    }
    //同意了隐私策略
    if (defaultSp.getBoolean(SP_PRIVACY_AGREED, false) && isMainProcess) {
      tryPrivacyAgree()
    }
  }

  // 隐私策略同意了，在登录后调用
  fun tryPrivacyAgree() {
    if (hasPrivacyAgree) return
    hasPrivacyAgree = true
    defaultSp.edit(commit = true) {
      putBoolean(SP_PRIVACY_AGREED, true)
    }
    initialServices.forEach {
      it.onPrivacyAgreed(this)
    }
  }

  // 取消同意隐私策略，用于重新登录
  fun cancelPrivacyAgree() {
    defaultSp.edit(commit = true) {
      putBoolean(SP_PRIVACY_AGREED, false)
    }
  }
}