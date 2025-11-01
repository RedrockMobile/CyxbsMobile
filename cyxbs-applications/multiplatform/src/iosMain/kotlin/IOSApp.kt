import androidx.compose.ui.window.ComposeUIViewController
import com.cyxbs.components.config.ConfigApplicationInfo
import com.cyxbs.components.config.IOSDebug
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.navigation.MainNavHost
import com.cyxbs.components.utils.extensions.IOSToast
import com.g985892345.provider.api.annotation.ImplProvider
import platform.UIKit.UIViewController
import com.cyxbs.components.config.init.InitialManager
import com.cyxbs.components.navigation.AppNavDisplay

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/23
 */

// 在 iOS 项目 AppDelegate#application 调用
fun doInitApp(impl: IOSKmpInterface) {
  IOSKmpInterfaceLink.impl = impl
  IOSConfigApplicationInfo.isDebug = impl.isDebug
  initProvider()
  InitialManager.init(isMainProcess = true)
}

fun MainViewController(): UIViewController {
  return ComposeUIViewController {
    AppTheme {
      MainNavHost()
//      PlatformToastCompose()
    }
  }
}

// 初始化 KtProvider
// 因为 KSP 只会在最底层源集生成代码，iosMain 是 iosArm64、iosSimulatorArm64 共用共同父源集
// 所以这里需要在最底层源集初始化 KtProvider
internal expect fun initProvider()

interface IOSKmpInterface {
  fun isDebug(): Boolean
}

@ImplProvider(IOSDebug::class)
internal object IOSKmpInterfaceLink : IOSDebug {

  lateinit var impl: IOSKmpInterface

  override fun isDebug(): Boolean {
    return impl.isDebug()
  }
}

@ImplProvider(IOSToast::class)
internal object IOSToast : IOSToast {
  override fun toast(s: String, isLong: Boolean) {
    // todo
  }
}

@ImplProvider
object IOSConfigApplicationInfo : ConfigApplicationInfo {

  var isDebug = false

  override fun isDebug(): Boolean {
    return isDebug
  }
}