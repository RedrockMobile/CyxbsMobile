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
import com.cyxbs.pages.home.mobile.ui.IOSHomeViewPager
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.UIKit.UITabBarController
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
  fun createTabBarController(): UITabBarController
  fun getDefaultExpandCourse(): Boolean
  fun enableUsePlatformToast(): Boolean
  fun toast(s: String, isLong: Boolean)
}

@ImplProvider(IOSHomeViewPager::class)
@ImplProvider(IOSToast::class)
@ImplProvider(ConfigApplicationInfo::class)
internal object IOSKmpInterfaceLink : IOSHomeViewPager, IOSToast, ConfigApplicationInfo {

  lateinit var impl: IOSKmpInterface

  override fun isDebug(): Boolean {
    return impl.isDebug()
  }

  override fun createTabBarController(): UITabBarController {
    return impl.createTabBarController()
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
}