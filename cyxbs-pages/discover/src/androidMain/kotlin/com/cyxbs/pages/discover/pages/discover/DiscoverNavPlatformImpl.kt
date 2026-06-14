package com.cyxbs.pages.discover.pages.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.core.net.toUri
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.webView.WebViewActivity
import com.cyxbs.components.config.route.DISCOVER_CALENDAR
import com.cyxbs.components.config.route.DISCOVER_GRADES
import com.cyxbs.components.config.route.DISCOVER_NEWS
import com.cyxbs.components.config.route.DISCOVER_NEWS_ITEM
import com.cyxbs.components.config.route.DISCOVER_NO_CLASS
import com.cyxbs.components.config.route.DISCOVER_SPORT
import com.cyxbs.components.config.route.DISCOVER_TODO_MAIN
import com.cyxbs.components.config.route.MINE_CHECK_IN
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.navigation.AppScheme
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.ClickEvent
import com.cyxbs.pages.course.api.FindCourseNavArgument
import com.cyxbs.pages.discover.R
import com.cyxbs.pages.discover.home.DiscoverFunctionItem
import com.cyxbs.pages.discover.home.DiscoverNavPlatform
import com.cyxbs.pages.emptyroom.api.EmptyRoomNavArgument
import com.cyxbs.pages.map.api.MapNavArgument
import com.cyxbs.pages.notification.api.ILaunchNotificationService
import com.cyxbs.pages.schoolcar.api.SchoolCarNavArgument
import com.g985892345.provider.api.annotation.ImplProvider
import cyxbsmobile.cyxbs_pages.discover.generated.resources.Res
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_more_function_notice_text
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_bus_track
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_empty_classroom
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_map
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_more_function
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_my_exam
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_no_class
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_other_course
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_school_calendar
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_sport
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_todo
import org.jetbrains.compose.resources.stringResource

/**
 * 发现入口页平台能力的 Android 实现，供 commonMain 的 [com.cyxbs.pages.discover.home.DiscoverPage]
 * 通过 `DiscoverNavPlatform::class.implOrNull()` 调用。
 */
@ImplProvider
object DiscoverNavPlatformImpl : DiscoverNavPlatform {

  @Composable
  override fun rememberFunctions(): List<DiscoverFunctionItem> {
    // onClick lambda 不是 @Composable，需要在外面提前取出 string
    val moreFunctionToast = stringResource(Res.string.discover_more_function_notice_text)
    return listOf(
      DiscoverFunctionItem(
        id = "other_course",
        title = stringResource(Res.string.discover_title_other_course),
        painter = painterResource(R.drawable.discover_ic_other_course),
        onClick = {
          trackIfLogin(ClickEvent.CLICK_KBCX_ENTRY)
          FindCourseNavArgument().navigate()
        },
      ),
      DiscoverFunctionItem(
        id = "map",
        title = stringResource(Res.string.discover_title_map),
        painter = painterResource(R.drawable.discover_ic_map),
        onClick = {
          trackIfLogin(ClickEvent.CLICK_CYDT_ENTRY)
          MapNavArgument(null).navigate()
        },
      ),
      DiscoverFunctionItem(
        id = "no_class",
        title = stringResource(Res.string.discover_title_no_class),
        painter = painterResource(R.drawable.discover_ic_no_class),
        loginPrompt = stringResource(Res.string.discover_title_no_class),
        onClick = {
          TrackingUtils.trackClickEvent2(ClickEvent.CLICK_MKY_ENTRY)
          startActivity(DISCOVER_NO_CLASS)
        },
      ),
      DiscoverFunctionItem(
        id = "bus_track",
        title = stringResource(Res.string.discover_title_bus_track),
        painter = painterResource(R.drawable.discover_ic_bus_track),
        onClick = {
          trackIfLogin(ClickEvent.CLICK_XCGJ_ENTRY)
          SchoolCarNavArgument.navigate()
        },
      ),
      DiscoverFunctionItem(
        id = "empty_classroom",
        title = stringResource(Res.string.discover_title_empty_classroom),
        painter = painterResource(R.drawable.discover_ic_empty_classroom),
        onClick = {
          trackIfLogin(ClickEvent.CLICK_YLC_KJS_ENTRY)
          EmptyRoomNavArgument.navigate()
        },
      ),
      DiscoverFunctionItem(
        id = "school_calendar",
        title = stringResource(Res.string.discover_title_school_calendar),
        painter = painterResource(R.drawable.discover_ic_school_calendar),
        onClick = {
          trackIfLogin(ClickEvent.CLICK_YLC_XL_ENTRY)
          startActivity(DISCOVER_CALENDAR)
        },
      ),
      DiscoverFunctionItem(
        id = "todo",
        title = stringResource(Res.string.discover_title_todo),
        painter = painterResource(R.drawable.discover_ic_todo),
        onClick = {
          trackIfLogin(ClickEvent.CLICK_YZQD_ENTRY)
          startActivity(DISCOVER_TODO_MAIN)
        },
      ),
      DiscoverFunctionItem(
        id = "sport",
        title = stringResource(Res.string.discover_title_sport),
        painter = painterResource(R.drawable.discover_ic_sport),
        loginPrompt = stringResource(Res.string.discover_title_sport),
        onClick = {
          TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_TYDK_ENTRY)
          startActivity(DISCOVER_SPORT)
        },
      ),
      DiscoverFunctionItem(
        id = "my_exam",
        title = stringResource(Res.string.discover_title_my_exam),
        painter = painterResource(R.drawable.discover_ic_my_exam),
        loginPrompt = stringResource(Res.string.discover_title_my_exam),
        onClick = {
          TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_WDKS_ENTRY)
          startActivity(DISCOVER_GRADES)
        },
      ),
      DiscoverFunctionItem(
        id = "more_function",
        title = stringResource(Res.string.discover_title_more_function),
        painter = painterResource(R.drawable.discover_ic_more_function),
        onClick = {
          toast(moreFunctionToast)
        },
      ),
    )
  }

  override fun launchNotification() {
    ILaunchNotificationService::class.impl().start()
  }

  override fun jumpCheckIn() {
    startActivity(MINE_CHECK_IN)
  }

  override fun jumpJwNewsList() {
    startActivity(DISCOVER_NEWS)
  }

  override fun jumpJwNewsItem(newId: String) {
    startActivity(DISCOVER_NEWS_ITEM) {
      putExtra("newId", newId)
    }
  }

  override fun onBannerClick(pictureGotoUrl: String, keyword: String) {
    if (IAccountService::class.impl().isLogin()) {
      // banner 位的点击埋点
      TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_BANNER_ENTRY)
    }
    val finalUrl = if (pictureGotoUrl.startsWith("http")) {
      val uri = pictureGotoUrl.toUri()
      if (uri.getQueryParameter(WebViewActivity.ARG_DEFAULT_TITLE) == null) {
        // 兼容老逻辑，keyword 作为页面的兜底标题
        uri.buildUpon().appendQueryParameter(WebViewActivity.ARG_DEFAULT_TITLE, keyword).build()
          .toString()
      } else pictureGotoUrl
    } else pictureGotoUrl
    AppScheme.jump(finalUrl)
  }

  private fun trackIfLogin(clickEvent: ClickEvent) {
    if (IAccountService::class.impl().isLogin()) {
      TrackingUtils.trackClickEvent2(clickEvent)
    }
  }
}