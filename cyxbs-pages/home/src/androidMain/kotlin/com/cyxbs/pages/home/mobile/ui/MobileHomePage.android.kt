package com.cyxbs.pages.home.mobile.ui

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.base.utils.Umeng
import com.cyxbs.components.config.route.DISCOVER_EMPTY_ROOM
import com.cyxbs.components.config.route.DISCOVER_GRADES
import com.cyxbs.components.config.route.DISCOVER_SCHOOL_CAR
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.config.sp.SP_COURSE_SHOW_STATE
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.components.navigation.AppScheme
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.ClickEvent
import com.cyxbs.functions.update.api.IAppUpdateService
import com.cyxbs.pages.home.api.HomeNavArgument
import com.cyxbs.pages.home.mobile.viewmodel.BottomNavViewModel
import com.cyxbs.pages.home.mobile.viewmodel.CourseBottomSheetViewModel

// 长按桌面图标的那个东西，对应 AndroidManifest.xml 中的设置
private const val DESKTOP_SHORTCUT_COURSE = "com.mredrock.cyxbs.action.COURSE"
private const val DESKTOP_SHORTCUT_EXAM = "com.mredrock.cyxbs.action.EXAM"
private const val DESKTOP_SHORTCUT_SCHOOL_CAR = "com.mredrock.cyxbs.action.SCHOOLCAR"
private const val DESKTOP_SHORTCUT_EMPTY_ROOM = "com.mredrock.cyxbs.action.EMPTY_ROOM"
private const val ACTION_TEST_UPDATE_DIALOG = "com.mredrock.cyxbs.action.TEST_UPDATE_DIALOG"

@Composable
internal actual fun PlatformMobileHomePage(
  argument: HomeNavArgument,
  content: @Composable () -> Unit
) {
  content()
  val bottomNavViewModel = viewModel(BottomNavViewModel::class)
  val courseBottomNavViewModel = viewModel(CourseBottomSheetViewModel::class)
  val activity = LocalActivity.current as BaseActivity
  DisposableEffect(Unit) {
    // 处理 intent.action
    execIntentAction(activity.intent, false, courseBottomNavViewModel)
    val onNewIntentListener = Consumer<Intent> { intent ->
      execIntentAction(intent, true, courseBottomNavViewModel)
    }
    activity.addOnNewIntentListener(onNewIntentListener)
    onDispose {
      activity.removeOnNewIntentListener(onNewIntentListener)
    }
  }
  LaunchedEffect(Unit) {
    bottomNavViewModel.selectedItem.collect {
      when (it) {
        bottomNavViewModel.fairgroundItem -> {
          // “邮乐园” 按钮点击事件埋点
          TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_ENTRY)
        }
      }

      // Umeng 埋点统计
      Umeng.sendEvent(Umeng.Event.ClickBottomTab(bottomNavViewModel.items.indexOf(it)))
    }
  }
}

private fun execIntentAction(
  intent: Intent,
  isNewIntent: Boolean,
  courseBottomNavViewModel: CourseBottomSheetViewModel,
) {
  when (intent.action) {
    DESKTOP_SHORTCUT_COURSE -> {
      if (!IAccountService::class.impl().isTouristMode()) {
        courseBottomNavViewModel.state.value = true
      }
    }
    DESKTOP_SHORTCUT_EXAM -> {
      startActivity(DISCOVER_GRADES)
    }
    DESKTOP_SHORTCUT_SCHOOL_CAR -> {
      startActivity(DISCOVER_SCHOOL_CAR)
    }
    DESKTOP_SHORTCUT_EMPTY_ROOM -> {
      startActivity(DISCOVER_EMPTY_ROOM)
    }
    ACTION_TEST_UPDATE_DIALOG -> {
      IAppUpdateService.debug() // 测试更新弹窗是否正常
    }
    else -> {
      if (defaultSp.getBoolean(SP_COURSE_SHOW_STATE, false)) {
        // 打开应用优先显示课表的设置
        if (!IAccountService::class.impl().isTouristMode()) {
          courseBottomNavViewModel.state.value = true
        }
      }
    }
  }
  if (isNewIntent) {
    val url = intent.data
    if (url != null) {
      runCatching {
        AppScheme.jump(url.toString())
      }.onFailure {
        logg(it.stackTraceToString())
      }
    }
  }
}