package com.cyxbs.pages.discover.pages.discover

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
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
import com.cyxbs.pages.discover.home.DiscoverFeedItem
import com.cyxbs.pages.discover.home.DiscoverFunctionItem
import com.cyxbs.pages.discover.home.DiscoverNavPlatform
import com.cyxbs.pages.electricity.api.IElectricityService
import com.cyxbs.pages.emptyroom.api.EmptyRoomNavArgument
import com.cyxbs.pages.map.api.MapNavArgument
import com.cyxbs.pages.notification.api.ILaunchNotificationService
import com.cyxbs.pages.schoolcar.api.SchoolCarNavArgument
import com.cyxbs.pages.sport.api.ISportService
import com.cyxbs.pages.todo.api.ITodoService
import com.g985892345.provider.api.annotation.ImplProvider
import androidx.core.net.toUri

/**
 * 发现入口页平台能力的 Android 实现，供 commonMain 的 [com.cyxbs.pages.discover.home.DiscoverPage]
 * 通过 `DiscoverNavPlatform::class.implOrNull()` 调用。
 *
 * 按钮的图标 / 文字 / 点击行为完全还原原 [com.cyxbs.pages.discover.utils.MoreFunctionProvider.functions]。
 */
@ImplProvider
object DiscoverNavPlatformImpl : DiscoverNavPlatform {

  @Composable
  override fun rememberFunctions(): List<DiscoverFunctionItem> = listOf(
    DiscoverFunctionItem(
      id = "other_course",
      title = "课表查询",
      painter = painterResource(R.drawable.discover_ic_other_course),
      onClick = {
        trackIfLogin(ClickEvent.CLICK_KBCX_ENTRY)
        FindCourseNavArgument().navigate()
      },
    ),
    DiscoverFunctionItem(
      id = "map",
      title = "重邮地图",
      painter = painterResource(R.drawable.discover_ic_map),
      onClick = {
        trackIfLogin(ClickEvent.CLICK_CYDT_ENTRY)
        MapNavArgument(null).navigate()
      },
    ),
    DiscoverFunctionItem(
      id = "no_class",
      title = "没课约",
      painter = painterResource(R.drawable.discover_ic_no_class),
      loginPrompt = "没课约",
      onClick = {
        TrackingUtils.trackClickEvent2(ClickEvent.CLICK_MKY_ENTRY)
        startActivity(DISCOVER_NO_CLASS)
      },
    ),
    DiscoverFunctionItem(
      id = "bus_track",
      title = "校车轨迹",
      painter = painterResource(R.drawable.discover_ic_bus_track),
      onClick = {
        trackIfLogin(ClickEvent.CLICK_XCGJ_ENTRY)
        SchoolCarNavArgument.navigate()
      },
    ),
    DiscoverFunctionItem(
      id = "empty_classroom",
      title = "空教室",
      painter = painterResource(R.drawable.discover_ic_empty_classroom),
      onClick = {
        trackIfLogin(ClickEvent.CLICK_YLC_KJS_ENTRY)
        EmptyRoomNavArgument.navigate()
      },
    ),
    DiscoverFunctionItem(
      id = "school_calendar",
      title = "校历",
      painter = painterResource(R.drawable.discover_ic_school_calendar),
      onClick = {
        trackIfLogin(ClickEvent.CLICK_YLC_XL_ENTRY)
        startActivity(DISCOVER_CALENDAR)
      },
    ),
    DiscoverFunctionItem(
      id = "todo",
      title = "邮子清单",
      painter = painterResource(R.drawable.discover_ic_todo),
      onClick = {
        trackIfLogin(ClickEvent.CLICK_YZQD_ENTRY)
        startActivity(DISCOVER_TODO_MAIN)
      },
    ),
    DiscoverFunctionItem(
      id = "sport",
      title = "体育打卡",
      painter = painterResource(R.drawable.discover_ic_sport),
      loginPrompt = "体育打卡",
      onClick = {
        TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_TYDK_ENTRY)
        startActivity(DISCOVER_SPORT)
      },
    ),
    DiscoverFunctionItem(
      id = "my_exam",
      title = "我的考试",
      painter = painterResource(R.drawable.discover_ic_my_exam),
      loginPrompt = "我的考试",
      onClick = {
        TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_WDKS_ENTRY)
        startActivity(DISCOVER_GRADES)
      },
    ),
    DiscoverFunctionItem(
      id = "more_function",
      title = "更多功能",
      painter = painterResource(R.drawable.discover_ic_more_function),
      onClick = {
        toast(R.string.discover_more_function_notice_text)
      },
    ),
  )

  @Composable
  override fun rememberFeeds(): List<DiscoverFeedItem> {
    return remember {
      listOf(
        makeFeed("sport") { ISportService::class.impl().getSportFeed() },
        makeFeed("todo") { ITodoService::class.impl().getTodoFeed() },
        makeFeed("electricity") { IElectricityService::class.impl().getElectricityFeed() },
      )
    }
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
        uri.buildUpon().appendQueryParameter(WebViewActivity.ARG_DEFAULT_TITLE, keyword).build().toString()
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

/**
 * 通用 helper：把一个产 Fragment 的 lambda 包装成 [DiscoverFeedItem]。
 */
private fun makeFeed(
  id: String,
  fragmentFactory: () -> Fragment,
): DiscoverFeedItem {
  return DiscoverFeedItem(
    content = { modifier -> FeedFragmentHost(id, modifier, fragmentFactory) },
  )
}

@Composable
private fun FeedFragmentHost(
  id: String,
  modifier: Modifier,
  fragmentFactory: () -> Fragment,
) {
  // 让 viewId 与 feedId 绑定且稳定，避免重组时 id 变化导致 Fragment 重复 add
  val viewId = remember(id) { stableViewIdFor(id) }
  val context = LocalContext.current
  // 优先用最近的宿主 Fragment（兼容老路径：DiscoverPage 被 Fragment 包裹）；
  // 没有宿主 Fragment 时（DiscoverPage 直接挂在 NavDisplay 下）兜底到 Activity 的 supportFragmentManager。
  val fm = remember(context) { context.findOwningFragmentManager() }
  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      FragmentContainerView(ctx).also { container ->
        container.id = viewId
        container.post {
          fm ?: return@post
          if (fm.findFragmentById(viewId) == null) {
            fm.beginTransaction()
              .add(viewId, fragmentFactory())
              .commitNowAllowingStateLoss()
          }
        }
      }
    },
  )
  // FeedFragmentHost 退出组合时把 Fragment 也从 FM 中摘下，避免使用 Activity 级 FM 时
  // 跨页面累积。原方案靠 DiscoverHomeFragment.childFragmentManager 销毁自动清理，这里手动对齐。
  DisposableEffect(fm, viewId) {
    onDispose {
      fm ?: return@onDispose
      if (fm.isStateSaved || fm.isDestroyed) return@onDispose
      val f = fm.findFragmentById(viewId) ?: return@onDispose
      fm.beginTransaction()
        .remove(f)
        .commitNowAllowingStateLoss()
    }
  }
}

private fun stableViewIdFor(id: String): Int {
  // 高 8 位置 1，避免与系统 / R 文件 id 冲突
  return (id.hashCode() and 0x00ffffff) or 0x7f000000
}

/**
 * 找到一个能挂 Fragment 的 FragmentManager：
 * 1. 当前 Composable 所在 View 链中最近的 Fragment 的 childFragmentManager；
 * 2. 兜底到宿主 FragmentActivity 的 supportFragmentManager。
 */
private fun Context.findOwningFragmentManager(): FragmentManager? {
  var c: Context? = this
  while (c is ContextWrapper) {
    if (c is FragmentActivity) return c.supportFragmentManager
    c = c.baseContext
  }
  return null
}