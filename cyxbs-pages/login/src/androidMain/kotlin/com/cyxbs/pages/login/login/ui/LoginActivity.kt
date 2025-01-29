package com.cyxbs.pages.login.login.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.base.BaseApp
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.route.MINE_FORGET_PASSWORD
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.service.impl
import com.cyxbs.components.utils.service.startActivity
import com.cyxbs.functions.update.api.IAppUpdateService
import com.cyxbs.pages.login.api.ILegalNoticeService
import com.cyxbs.pages.login.ui.LoginPage
import com.cyxbs.pages.login.viewmodel.LoginViewModel

/**
 * 登录界面
 *
 * 目前登录界面使用了多平台的 compose
 * 数据流方向: LoginCompose -> LoginViewModel -> LoginActivity
 * 由于 CommonLoginViewModel 抽了一层多平台通用逻辑后，LoginActivity 更多的逻辑是处理跳转，所以感觉代码有点少
 * 但如果后续是全界面上 Compose 的话，那个时候跳转就放到 Compose 层，目前因为跳转需要强耦合 activity，所以变成了这样
 *
 */
class LoginActivity : BaseActivity() {
  companion object {
    // 登录成功后直接返回上一个 Activity 界面
    fun start(intent: (Intent.() -> Unit)? = null) {
      startInternal(false, null, intent)
    }

    // 登录成功后打开指定的 Activity
    fun start(
      successActivity: Class<out Activity>,
      intent: (Intent.() -> Unit)? = null
    ) {
      startInternal(false, successActivity, intent)
    }

    // 登录成功后重新打开应用，会清空之前的所有 Activity
    fun startReboot(intent: (Intent.() -> Unit)?) {
      startInternal(true, null, intent)
    }

    private fun startInternal(
      isReboot: Boolean,
      successActivity: Class<out Activity>?,
      intent: (Intent.() -> Unit)?
    ) {
      com.cyxbs.components.init.appContext.startActivity(
        Intent(com.cyxbs.components.init.appContext, LoginActivity::class.java)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 因为使用 appContext，所以需要加
          .putExtra(LoginActivity::mIsReboot.name, isReboot)
          .apply {
            if (successActivity != null) {
              putExtra(
                LoginActivity::mSuccessIntent.name,
                Intent(com.cyxbs.components.init.appContext, successActivity)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 因为使用 appContext，所以需要加
              )
            }
            if (isReboot) {
              addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK) // 清空 Activity 栈
            }
            intent?.invoke(this)
          }
      )
    }
  }

  override val isPortraitScreen: Boolean
    get() = false

  private val mIsReboot by intent<Boolean>()
  private val mSuccessIntent by intentNullable<Intent?>()

  private val mViewModel by viewModels<LoginViewModel>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { AppTheme { LoginPage() } }
    initObserveEvent()
    initUpdate()
    initPrivacyAgree()
  }

  private fun initObserveEvent() {
    mViewModel.event.collectLaunch {
      when (it) {
        LoginViewModel.Event.ClickForgetPassword -> onClickForgetPasswordEvent()
        LoginViewModel.Event.ClickPrivacyPolicy -> onClickPrivacyPolicy()
        LoginViewModel.Event.ClickUserAgreement -> onClickUserAgreement()
        is LoginViewModel.Event.Login -> onLoginEvent(it)
        LoginViewModel.Event.HideSoftInput -> onHideSoftInput()
        LoginViewModel.Event.ClickDisagreeUserAgreement -> onDisagreeUserAgreement()
      }
    }
  }

  private fun onClickForgetPasswordEvent() {
    startActivity(MINE_FORGET_PASSWORD)
  }

  private fun onClickPrivacyPolicy() {
    ILegalNoticeService::class.impl().startPrivacyPolicyActivity(this)
  }

  private fun onClickUserAgreement() {
    ILegalNoticeService::class.impl().startUserAgreementActivity(this)
  }

  private fun onLoginEvent(event: LoginViewModel.Event.Login) {
    when (event.result) {
      null -> { // 游客模式
        IAccountEditService::class.impl().onTouristMode()
        rebootApp()
        BaseApp.baseApp.tryPrivacyAgree()
      }
      true -> { // 登录成功
        if (mIsReboot) {
          rebootApp()
        } else {
          if (mSuccessIntent != null) {
            startActivity(mSuccessIntent)
          }
          finish()
        }
        BaseApp.baseApp.tryPrivacyAgree()
      }
      false -> { // 登录失败

      }
    }
  }


  private fun onHideSoftInput() {
    //放下键盘
    val inputMethodManager =
      com.cyxbs.components.init.appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    if (inputMethodManager.isActive) {
      inputMethodManager.hideSoftInputFromWindow(this.currentFocus?.windowToken, 0)
    }
  }

  private fun onDisagreeUserAgreement() {
    finishAndRemoveTask()
  }

  private fun rebootApp() {
    val rebootIntent = com.cyxbs.components.init.appContext.packageManager
      .getLaunchIntentForPackage(com.cyxbs.components.init.appContext.packageName)!!
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
    startActivity(rebootIntent)
    finishAndRemoveTask()
  }

  private fun initUpdate() {
    IAppUpdateService::class.impl().tryNoticeUpdate(this, true)
  }

  private fun initPrivacyAgree() {
    BaseApp.baseApp.cancelPrivacyAgree() // 重新登录时取消之前已保存的隐私政策同意状态
  }
}