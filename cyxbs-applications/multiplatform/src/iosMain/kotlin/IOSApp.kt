import androidx.compose.ui.window.ComposeUIViewController
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.account.provider.TokenProvider
import com.cyxbs.components.config.ConfigApplicationInfo
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.init.InitialManager
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.navigation.AppNavDisplay
import com.cyxbs.components.utils.extensions.IOSToast
import com.cyxbs.components.utils.extensions.PlatformToastCompose
import com.cyxbs.pages.discover.home.DiscoverIosPlatform
import com.cyxbs.pages.discover.home.functions.DiscoverFunctionsIosPlatform
import com.cyxbs.pages.home.mobile.ui.IOSHomeViewPager
import com.cyxbs.pages.sport.service.SportIosPlatform
import com.cyxbs.pages.todo.service.TodoIosPlatform
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/23
 */

// 在 iOS 项目 AppDelegate#application 调用
fun doInitApp(impl: IOSKmpInterface) {
  IOSKmpInterfaceLink.impl = impl
  initProvider()
  InitialManager.init(isMainProcess = true)

  // 监听 token
  appCoroutineScope.launch(Dispatchers.Main) {
    TokenProvider.stateFlow.collect {
      if (it != null) {
        impl.setToken(it.token)
      }
    }
  }
}

fun MainViewController(): UIViewController {
  return ComposeUIViewController {
    AppTheme {
      AppNavDisplay()
      if (!IOSKmpInterfaceLink.enableUsePlatformToast()) {
        PlatformToastCompose()
      }
    }
  }
}

fun onLogout() {
  IAccountEditService::class.impl().onLogout()
}

// 初始化 KtProvider
// 因为 KSP 只会在最底层源集生成代码，iosMain 是 iosArm64、iosSimulatorArm64 共用共同父源集
// 所以这里需要在最底层源集初始化 KtProvider
internal expect fun initProvider()

interface IOSKmpInterface {
  fun isDebug(): Boolean
  fun setToken(token: String)
  fun getDefaultExpandCourse(): Boolean
  fun enableUsePlatformToast(): Boolean
  fun toast(s: String, isLong: Boolean)

  /** push 体育打卡详情页（iOS 原生 SportAttendanceViewController） */
  fun jumpSportDetail()

  /** push 邮子清单主页（iOS 原生 ToDoVC） */
  fun jumpTodoMain()

  /** push 没课约（iOS 原生 WeDateVC） */
  fun jumpWeDate()

  /** push 校历（iOS 原生 CalendarViewController） */
  fun jumpSchoolCalendar()

  /** push 我的考试（iOS 原生 TestArrangeViewController） */
  fun jumpTestArrange()

  /** push 消息中心（iOS 原生 MineMessageVC） */
  fun launchNotification()

  /** present 签到页（iOS 原生 CheckInViewController，全屏 modal） */
  fun jumpCheckIn()

  /** 跳转教务在线新闻列表（iOS 原版功能已停服，toast 兜底） */
  fun jumpJwNewsList()

  /** 跳转教务在线某条新闻详情（iOS 原版功能已停服，toast 兜底） */
  fun jumpJwNewsItem(newId: String)

  /** Banner 点击：UIApplication.shared.open(url) 交给系统 Safari */
  fun onBannerClick(pictureGotoUrl: String, keyword: String)
}

// SportIosPlatform / TodoIosPlatform / DiscoverFunctionsIosPlatform 之间存在同名同签名
// 方法（jumpSportDetail / jumpTodoMain），同一个 override 一次性满足多个接口，是 Kotlin
// 多接口合并的标准行为，不需要 super<X> 仲裁。
@ImplProvider(IOSHomeViewPager::class)
@ImplProvider(IOSToast::class)
@ImplProvider(ConfigApplicationInfo::class)
@ImplProvider(SportIosPlatform::class)
@ImplProvider(TodoIosPlatform::class)
@ImplProvider(DiscoverFunctionsIosPlatform::class)
@ImplProvider(DiscoverIosPlatform::class)
internal object IOSKmpInterfaceLink :
  IOSHomeViewPager,
  IOSToast,
  ConfigApplicationInfo,
  SportIosPlatform,
  TodoIosPlatform,
  DiscoverFunctionsIosPlatform,
  DiscoverIosPlatform {

  lateinit var impl: IOSKmpInterface

  override fun isDebug(): Boolean {
    return impl.isDebug()
  }

  override fun getDefaultExpandCourse(): Boolean {
    return impl.getDefaultExpandCourse()
  }

  override fun enableUsePlatformToast(): Boolean {
    return impl.enableUsePlatformToast()
  }

  override fun toast(s: String, isLong: Boolean) {
    impl.toast(s, isLong)
  }

  override fun jumpSportDetail() {
    impl.jumpSportDetail()
  }

  override fun jumpTodoMain() {
    impl.jumpTodoMain()
  }

  override fun jumpWeDate() {
    impl.jumpWeDate()
  }

  override fun jumpSchoolCalendar() {
    impl.jumpSchoolCalendar()
  }

  override fun jumpTestArrange() {
    impl.jumpTestArrange()
  }

  override fun launchNotification() {
    impl.launchNotification()
  }

  override fun jumpCheckIn() {
    impl.jumpCheckIn()
  }

  override fun jumpJwNewsList() {
    impl.jumpJwNewsList()
  }

  override fun jumpJwNewsItem(newId: String) {
    impl.jumpJwNewsItem(newId)
  }

  override fun onBannerClick(pictureGotoUrl: String, keyword: String) {
    impl.onBannerClick(pictureGotoUrl, keyword)
  }
}