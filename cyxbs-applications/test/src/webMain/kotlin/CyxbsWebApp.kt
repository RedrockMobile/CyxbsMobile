import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.init.InitialManager
import com.cyxbs.components.config.navigation.MainNavHost
import com.cyxbs.components.utils.extensions.PlatformToastCompose

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
  ComposeViewport {
    AppTheme {
      MainNavHost()
      PlatformToastCompose()
    }
  }
}

expect fun initKtProvider()