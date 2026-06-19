import androidx.compose.ui.window.ComposeUIViewController
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.account.api.IAccountService
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
import com.cyxbs.pages.home.api.HomeNavArgument
import com.cyxbs.pages.home.mobile.ui.IOSHomeViewPager
import com.cyxbs.pages.login.api.LoginNavArgument
import com.cyxbs.pages.login.service.LoginIosPlatform
import com.cyxbs.pages.mine.home.MineIosPlatform
import com.cyxbs.pages.sport.service.SportIosPlatform
import com.cyxbs.pages.todo.service.TodoIosPlatform
import com.cyxbs.pages.ufield.fairground.FairgroundIosPlatform
import com.cyxbs.pages.course.service.CourseIosPlatform
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
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

  // 监听 token 变化，同步到 iOS 原生侧
  appCoroutineScope.launch(Dispatchers.Main) {
    TokenProvider.stateFlow.collect {
      impl.setToken(it?.token ?: "")
    }
  }

  // 监听账户状态变化，登录成功后同步 iOS 原生数据（待办、用户信息、邮箱绑定等）。
  // drop(1) 跳过初始值：app 启动时有缓存 token 则 state 已经是 Login，
  // 不跳过会误触发 onLoginSuccess 导致重复同步。
  val accountService = IAccountService::class.impl()
  appCoroutineScope.launch(Dispatchers.Main) {
    accountService.state
      .drop(1)
      .filterIsInstance<AccountState.Login>()
      .collect { login ->
        impl.onLoginSuccess(login.stuNum)
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
  // 退出登录后跳转到 CMP 登录页，登录成功后回到主页
  LoginNavArgument.navigate(HomeNavArgument(), clearStack = true)
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

  /** push 答疑广场（iOS 原生 QAMainVC） */
  fun jumpQaEntry()

  /** push 活动布告栏（iOS 原生 ActivityMainViewController） */
  fun jumpUfieldMainEntry()

  /** push 邮票中心（iOS 原生 StampCenterVC） */
  fun jumpStore()

  /** push 反馈中心（iOS 原生 FeedBackMainPageViewController） */
  fun jumpFeedbackCenter()

  /** push 我的页签到（iOS 原生 CheckInViewController，与 jumpCheckIn 的 present 不同） */
  fun jumpSign()

  /** push 设置页（iOS 原生 MineSettingViewController） */
  fun jumpSetting()

  /** push 活动中心（iOS 原生 ActivityCenterVC） */
  fun jumpActivityCenter()

  /**
   * 课表数据更新成功回调。
   * Swift 端把 [stuLessonBeanJson] 解析为旧 ScheduleModel / CurriculumModel，
   * 写入 App Group 共享缓存供 CyxbsWidgetExtension 课表小组件读取。
   */
  fun onLessonUpdated(stuNum: String, nowWeek: Int, stuLessonBeanJson: String)

  /**
   * 登录成功后同步 iOS 原生数据。
   * Swift 端执行：同步待办、获取用户信息、更新 Person 模型、检查邮箱绑定、记录登录时间。
   */
  fun onLoginSuccess(stuNum: String)

  /**
   * 跳转找回密码页面。
   * [stuNum] 当前输入的学号（可能为空），Swift 端用于预填充和查询绑定状态。
   */
  fun jumpForgotPassword(stuNum: String)

  /**
   * 打开用户协议页面
   */
  fun jumpUserAgreement()

  /**
   * 打开隐私政策页面
   */
  fun jumpPrivacyPolicy()

  /**
   * 退出应用（用户不同意用户协议时调用）
   */
  fun exitApp()
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
@ImplProvider(FairgroundIosPlatform::class)
@ImplProvider(MineIosPlatform::class)
@ImplProvider(CourseIosPlatform::class)
@ImplProvider(LoginIosPlatform::class)
internal object IOSKmpInterfaceLink :
  IOSHomeViewPager,
  IOSToast,
  ConfigApplicationInfo,
  SportIosPlatform,
  TodoIosPlatform,
  DiscoverFunctionsIosPlatform,
  DiscoverIosPlatform,
  FairgroundIosPlatform,
  MineIosPlatform,
  CourseIosPlatform,
  LoginIosPlatform {

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

  override fun jumpQaEntry() {
    impl.jumpQaEntry()
  }

  override fun jumpUfieldMainEntry() {
    impl.jumpUfieldMainEntry()
  }

  override fun jumpStore() {
    impl.jumpStore()
  }

  override fun jumpFeedbackCenter() {
    impl.jumpFeedbackCenter()
  }

  override fun jumpSign() {
    impl.jumpSign()
  }

  override fun jumpSetting() {
    impl.jumpSetting()
  }

  override fun jumpActivityCenter() {
    impl.jumpActivityCenter()
  }

  override fun onLessonUpdated(stuNum: String, nowWeek: Int, stuLessonBeanJson: String) {
    impl.onLessonUpdated(stuNum, nowWeek, stuLessonBeanJson)
  }

  override fun jumpForgotPassword(stuNum: String) {
    impl.jumpForgotPassword(stuNum)
  }

  override fun jumpUserAgreement() {
    impl.jumpUserAgreement()
  }

  override fun jumpPrivacyPolicy() {
    impl.jumpPrivacyPolicy()
  }

  override fun exitApp() {
    impl.exitApp()
  }
}