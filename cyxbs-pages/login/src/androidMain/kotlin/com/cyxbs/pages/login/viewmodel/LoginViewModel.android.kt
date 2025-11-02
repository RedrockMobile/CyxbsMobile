package com.cyxbs.pages.login.viewmodel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.snapshotFlow
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.base.BaseApp
import com.cyxbs.components.config.route.MINE_FORGET_PASSWORD
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.components.utils.utils.judge.NetworkUtil
import com.cyxbs.pages.login.api.ILegalNoticeService
import com.cyxbs.pages.login.api.LoginNavArgument
import com.cyxbs.pages.login.bean.DeviceInfoParams
import com.cyxbs.pages.login.bean.LoginBean
import com.cyxbs.pages.login.network.LoginApiService

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/31
 */
actual class LoginViewModel actual constructor(argument: LoginNavArgument) :
  CommonLoginViewModel(argument) {

  init {
    BaseApp.baseApp.cancelPrivacyAgree() // 重新登录时取消之前已保存的隐私政策同意状态
    snapshotFlow { isLoginAnim.value }.collectLaunch {
      if (it) {
        hideSoftInput()
      }
    }
  }

  override suspend fun onLoginSuccess(username: String, bean: LoginBean) {
    super.onLoginSuccess(username, bean)
    BaseApp.baseApp.tryPrivacyAgree()
    postDeviceInfo()
  }

  override fun clickForgetPassword() {
    startActivity(MINE_FORGET_PASSWORD)
  }

  override fun clickUserAgreement() {
    ILegalNoticeService::class.impl().startUserAgreementActivity()
  }

  override fun clickPrivacyPolicy() {
    ILegalNoticeService::class.impl().startPrivacyPolicyActivity()
  }

  override fun enterTouristMode() {
    BaseApp.baseApp.tryPrivacyAgree()
    IAccountEditService::class.impl().onTouristMode()
    super.enterTouristMode()
  }

  override fun clickDisagreeUserAgreement() {
    if (!MainNavController.navigateUp()) {
      // 没有上一级时就退出 activity
      var context: Context? = MainNavController.context
      while (context is ContextWrapper) {
        if (context is Activity) {
          context.finish()
          return
        }
        context = context.baseContext
      }
    }
  }

  private fun postDeviceInfo() {
    /**
     * 登录后向后端发送一次登录时的设备信息以及wifi的ip，用于在校园网登录时能进行定位，防止有人乱登录搞出事故
     * 如果连接方式为流量或者无法获取到wifi的ip，则直接上传 null 即可
     */
    var ipAddress: String? = null
    //检测网络的连接方式
    NetworkUtil.checkCurrentNetworkType()?.let {
      //如果是通过wifi连接，则尝试获取wifi的ip
      if (!it) {
        ipAddress = NetworkUtil.getWifiIPAddress()
      }
    }
    launch {
      //上传设备以及ip信息
      LoginApiService.INSTANCE.recordDeviceInfo(
        DeviceInfoParams(
          BaseApp.getAndroidID(),
          BaseApp.getDeviceModel(),
          ipAddress
        )
      )
    }
  }

  // 放下键盘
  private fun hideSoftInput() {
    val inputMethodManager =
      appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    if (inputMethodManager.isActive) {
      inputMethodManager.hideSoftInputFromWindow(appTopActivity.get()?.currentFocus?.windowToken, 0)
    }
  }
}