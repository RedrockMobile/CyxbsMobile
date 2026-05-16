import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.cyxbs.components.config.ConfigApplicationInfo
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.init.InitialManager
import com.cyxbs.components.config.navigation.MainNavHost
import com.cyxbs.components.utils.extensions.PlatformToastCompose
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/29
 */

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  initKtProvider()
  InitialManager.init(isMainProcess = true)
  ComposeViewport(viewportContainerId = "composeContainer") {
    AppTheme {
      MainNavHost()
      PlatformToastCompose()
    }
  }
}

expect fun initKtProvider()

@ImplProvider
object WebConfigApplicationInfo : ConfigApplicationInfo {
  override fun isDebug(): Boolean {
    return true
  }
}