package com.cyxbs.pages.home.ui.main

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.ITokenService
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.base.utils.Umeng
import com.cyxbs.components.config.route.DISCOVER_EMPTY_ROOM
import com.cyxbs.components.config.route.DISCOVER_GRADES
import com.cyxbs.components.config.route.DISCOVER_SCHOOL_CAR
import com.cyxbs.components.config.sp.SP_COURSE_SHOW_STATE
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.launch
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.ClickEvent
import com.cyxbs.components.utils.service.impl
import com.cyxbs.components.utils.service.startActivity
import com.cyxbs.components.utils.utils.judge.RedrockNetwork
import com.cyxbs.functions.update.api.IAppUpdateService
import com.cyxbs.pages.home.viewmodel.BottomNavViewModel
import com.cyxbs.pages.home.viewmodel.MainViewModel
import com.cyxbs.pages.login.api.ILoginService
import kotlinx.coroutines.launch

/**
 * 注意:
 * - 新逻辑请在 [initUI] 里面 添加新的 initXXX 方法
 * - 不建议重写其他生命周期方法，应该使用 lifecycle.addObserver() 写在你的 initXXX 方法里
 * -
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/9/14 20:49
 */
class MainActivity : BaseActivity() {
  
  private val mViewModel by viewModels<MainViewModel>()
  private val mBottomNavViewModel by viewModels<BottomNavViewModel>()
  
  private val mAccountService = IAccountService::class.impl()

  private var mIsLogin = false
  
  override fun onCreate(savedInstanceState: Bundle?) {
    // 还原主题，因为 MainActivity 最开始在 AndroidManifest.xml 设置了闪屏页背景，所以这里需要还原
    setTheme(com.cyxbs.components.config.R.style.ConfigAppTheme)
    super.onCreate(savedInstanceState)
    val isLogin = checkIsLogin()
    if (isLogin != null) {
      mIsLogin = isLogin
      initUI()
      if (mIsLogin) {
        launch {
          RedrockNetwork.tryPingNetWork()?.onFailure {
            toast("后端服务暂不可用")
            logg("tryPingNetWork: ${it.stackTraceToString()}")
          }
        }
      }
    }
  }

  private fun checkIsLogin(): Boolean? {
    if (!mAccountService.isTouristMode()) {
      // 不是游客模式
      if (!mAccountService.isLogin() || ITokenService::class.impl().isRefreshTokenExpired()) {
        // 未登录 和 refreshToken 过期时 需要跳转到登录界面
        ILoginService::class.impl().jumpToLoginPage()
        finish()
        return null
      }
      return true
    }
    return false
  }

  /**
   * 在没有跳转到登录界面时调用，用于初始化 UI
   *
   * 以后初始化的内容写在这里
   */
  private fun initUI() {
    initAction()
    initBottomNav()
    initNotification()
    initUpdate()
    setContent {
      MainPage()
    }
  }

  /**
   * 打开应用就要触发的「大型UI」事件，比如直接跳转到某页面
   */
  private fun initAction() {
    if (mIsActivityRebuilt) return // 如果 activity 被重建了，就不应该执行
    when (intent.action) {
      DESKTOP_SHORTCUT_COURSE -> {
        if (mIsLogin) {
          mViewModel.courseBottomSheetExpand.value = true
        }
      }
      // 因政策问题暂时关闭
      DESKTOP_SHORTCUT_EXAM -> {
        if (mIsLogin) {
          startActivity(DISCOVER_GRADES)
        }
      }
      DESKTOP_SHORTCUT_SCHOOL_CAR -> {
        startActivity(DISCOVER_SCHOOL_CAR)
      }
      DESKTOP_SHORTCUT_EMPTY_ROOM -> {
        startActivity(DISCOVER_EMPTY_ROOM)
      }
      else -> {
        if (mIsLogin && defaultSp.getBoolean(SP_COURSE_SHOW_STATE, false)) {
          // 打开应用优先显示课表的设置
          mViewModel.courseBottomSheetExpand.value = true
        }
      }
    }
  }
  
  private fun initBottomNav() {
    mViewModel.courseBottomSheetOffset.observe {
      // 底部按钮跟随课表展开而变化
      mBottomNavViewModel.offsetYRadio.floatValue = it
      mBottomNavViewModel.alpha.floatValue = 1 - it
    }
    mBottomNavViewModel.selectedItem.collectLaunch {
      when (it) {
        mBottomNavViewModel.discoverItem, mBottomNavViewModel.mineItem -> {
          if (mViewModel.courseBottomSheetExpand.value == null) {
            mViewModel.courseBottomSheetExpand.value = false
          }
        }
        mBottomNavViewModel.fairgroundItem -> {
          mViewModel.courseBottomSheetExpand.value = null
          if (mIsLogin) {
            // “邮乐园” 按钮点击事件埋点
            appCoroutineScope.launch {
              TrackingUtils.trackClickEvent(ClickEvent.CLICK_YLC_ENTRY)
            }
          }
        }
      }
      
      // Umeng 埋点统计
      Umeng.sendEvent(Umeng.Event.ClickBottomTab(mBottomNavViewModel.items.indexOf(it)))
    }
  }

  private fun initNotification() {
    mViewModel.hasUnReadNotification.observe {
      mBottomNavViewModel.mineItem.setRedDot(it)
    }
    lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onResume(owner: LifecycleOwner) {
        if (mIsLogin){
          // 获取（远端消息数据可能发生更新后）最新的未读消息数量，一般认为在从其他Activity返回后调用
          mViewModel.getNotificationUnReadStatus()
        }
      }
    })
  }

  private fun initUpdate() {
    IAppUpdateService::class.impl().tryNoticeUpdate(this)
  }
  
  companion object {
    // 长按桌面图标的那个东西，对应 AndroidManifest.xml 中的设置
    const val DESKTOP_SHORTCUT_COURSE = "com.mredrock.cyxbs.action.COURSE"
    const val DESKTOP_SHORTCUT_EXAM = "android.intent.action.EXAM"
    const val DESKTOP_SHORTCUT_SCHOOL_CAR = "android.intent.action.SCHOOLCAR"
    const val DESKTOP_SHORTCUT_EMPTY_ROOM = "android.intent.action.EMPTY_ROOM"
  }
}