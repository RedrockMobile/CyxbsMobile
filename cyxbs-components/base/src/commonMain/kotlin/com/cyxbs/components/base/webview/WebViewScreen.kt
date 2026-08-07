package com.cyxbs.components.base.webview

import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import io.github.multiweb.api.WebRequest
import io.github.multiweb.api.WebViewController
import io.github.multiweb.api.WebViewStateObservable
import io.github.multiweb.compose.WebView
import io.github.multiweb.compose.WebViewHostCallbacks
import io.github.multiweb.compose.rememberWebViewController
import org.jetbrains.compose.resources.painterResource

/** WebView 页面 UI；布局和标题行为与旧 WebViewActivity 保持一致。 */
@Composable
internal fun WebViewScreen(argument: WebViewNavArgument) {
  var isFullscreen by remember { mutableStateOf(false) }
  val platformHost = rememberWebViewPlatformHost { enabled ->
    isFullscreen = enabled
  }
  val controller = rememberWebViewController(
    initialization = platformHost.initialization,
    hostCallbacks = WebViewHostCallbacks(
      onExternalNavigation = platformHost::onExternalNavigation,
    ),
  )

  LaunchedEffect(controller, argument.url) {
    if (argument.url.isNotBlank()) {
      controller.load(WebRequest(argument.url))
    }
  }

  WebViewContent(
    argument = argument,
    controller = controller,
    isFullscreen = isFullscreen,
  )
}

@Composable
private fun WebViewContent(
  argument: WebViewNavArgument,
  controller: WebViewController,
  isFullscreen: Boolean,
) {
  val stateFlow = (controller as? WebViewStateObservable)?.stateFlow
  val observedState = stateFlow?.collectAsState()
  val webViewState = observedState?.value ?: controller.state
  val title = argument.title
    ?: webViewState.title?.takeIf { it.isNotBlank() }
    ?: argument.defaultTitle

  val onBack: () -> Unit = argument::popBackStack
  val backEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
  NavigationBackHandler(
    state = backEventState,
    isBackEnabled = true,
    onBackCompleted = onBack,
  )

  Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
    if (!argument.hideTitle && !isFullscreen) {
      WebViewTopBar(
        title = title,
        onBack = onBack,
      )
    }
    Box(
      modifier = Modifier.fillMaxWidth().weight(1f),
      contentAlignment = Alignment.Center,
    ) {
      if (argument.url.isBlank()) {
        Text(text = "无网页链接")
      } else {
        WebView(controller = controller, modifier = Modifier.fillMaxSize())
      }
    }
  }
}

@Composable
private fun WebViewTopBar(
  title: String,
  onBack: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().height(56.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Image(
      painter = painterResource(ConfigRes.configIcBack()),
      contentDescription = null,
      modifier = Modifier
        .padding(start = 24.dp)
        .size(12.dp)
        .clickable(onClick = onBack),
    )
    Text(
      text = title,
      color = LocalAppColors.current.tvLv2,
      fontSize = 20.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Clip,
      modifier = Modifier
        .weight(1f)
        .padding(start = 12.dp, end = 24.dp)
        .basicMarquee(iterations = Int.MAX_VALUE),
    )
  }
}
