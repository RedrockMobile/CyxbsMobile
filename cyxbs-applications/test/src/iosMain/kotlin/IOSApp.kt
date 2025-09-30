import androidx.compose.ui.window.ComposeUIViewController
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.isIOSDebug
import com.cyxbs.components.config.navigation.MainNavHost
import platform.UIKit.UIViewController

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/23
 */

// 在 iOS 项目 AppDelegate#application 调用
fun doInitApp(isDebug: Boolean) {
  isIOSDebug = isDebug
  initProvider()
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
// 因为 KSP 只会在最底层源集生成代码，iosMain 是 iosX64、iosArm64、iosSimulatorArm64 共用共同父源集
// 所以这里需要在最底层源集初始化 KtProvider
internal expect fun initProvider()