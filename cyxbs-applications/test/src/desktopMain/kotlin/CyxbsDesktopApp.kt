import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.launchApplication
import androidx.compose.ui.window.rememberWindowState
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.view.ui.PlatformToastCompose
import com.cyxbs.components.utils.coroutine.runApp
import com.cyxbs.pages.login.ui.LoginPage
import com.g985892345.provider.cyxbsmobile.cyxbsapplications.test.TestKtProviderInitializer

/**
 * .
 *
 * @author 985892345
 * @date 2024/12/29
 */

fun main() = runApp {
  TestKtProviderInitializer.tryInitKtProvider()
  launchApplication {
    val width = 800
    val height = 600
    Window(
      onCloseRequest = ::exitApplication,
      title = "桌上重邮",
      state = rememberWindowState(width = width.dp, height = height.dp),
//      resizable = false,
    ) {
      remember {
        this.window.minimumSize = java.awt.Dimension(360, 600)
      }
      AppTheme {
        LoginPage()
        PlatformToastCompose()
      }
    }
  }
}