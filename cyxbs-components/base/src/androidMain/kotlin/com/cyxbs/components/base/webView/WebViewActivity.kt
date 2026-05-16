package com.cyxbs.components.base.webView

import android.app.Activity
import android.content.ContextWrapper
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.scheme.WebViewFactory
import com.cyxbs.components.config.service.startActivity
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 通用的 webView activity
 *
 * @author 985892345
 * @date 2025/10/20
 */
class WebViewActivity : BaseActivity() {

  @ImplProvider
  companion object : WebViewFactory {
    fun start(
      url: String,
      hideTitle: Boolean = false, // 是否隐藏标题栏
      title: String? = null, // 如果为 null 则优先使用网页标签页名字
      defaultTitle: String = "网页" // 如果网页标签页名字为空则使用这个
    ) {
      startActivity(WebViewActivity::class) {
        putExtra("hideTitle", hideTitle)
        putExtra("url", url)
        putExtra("title", title)
        putExtra("defaultTitle", defaultTitle)
      }
    }

    override fun startWebView(url: String) {
      start(url)
    }
  }

  private val viewModel by viewModels<WebViewViewModel>()
  private val mHideTitle by lazy { intent.getBooleanExtra("hideTitle", false) }
  private val mUrl by lazy { intent.getStringExtra("url") }
  private val mTitle by lazy { intent.getStringExtra("title") }

  private val mDefaultTitle by lazy { intent.getStringExtra("defaultTitle") ?: "网页" }
  
  // 返回按钮回调 - 根据 WebView 是否有历史记录来控制行为
  private val onBackPressedCallback = object : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
      val webView = viewModel.webViewStateFlow.value
      if (webView != null && webView.canGoBack()) {
        // 如果 WebView 有历史记录，则后退
        webView.goBack()
      } else {
        // 临时禁用当前回调
        isEnabled = false
        // 递归再次触发系统默认返回行为（会调用 Activity.finish()）
        onBackPressedDispatcher.onBackPressed()
        // 重新启用回调，以便下次返回键按下时仍能处理
        isEnabled = true
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // 注册返回按钮回调
    onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    
    setContent {
      AppTheme {
        WebViewCompose(
          hideTitle = mHideTitle,
          url = mUrl,
          title = mTitle,
          defaultTitle = mDefaultTitle
        )
      }
    }
  }
}

class WebViewViewModel : BaseViewModel() {
  // webView 对象，用于 activity 处理一些操作
  // 声明周期由 Compose 内部主动置为 null 保证
  val webViewStateFlow = MutableStateFlow<LiteJsWebView?>(null)
}

@Composable
private fun WebViewCompose(
  hideTitle: Boolean,
  url: String?,
  title: String?,
  defaultTitle: String,
) {
  val webViewTitle = remember { mutableStateOf(title) }
  val context = LocalContext.current
  Column(modifier = Modifier.systemBarsPadding()) {
    if (!hideTitle) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Image(
          painter = painterResource(com.cyxbs.components.config.R.drawable.config_ic_back),
          contentDescription = null,
          modifier = Modifier
            .padding(start = 24.dp)
            .size(12.dp)
            .clickable {
              var wrapper = context
              while (wrapper is ContextWrapper && wrapper !is Activity) {
                wrapper = wrapper.baseContext
              }
              if (wrapper is Activity) {
                wrapper.finishAfterTransition()
              }
            }
        )
        Text(
          text = webViewTitle.value ?: defaultTitle,
          color = LocalAppColors.current.tvLv2,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 12.dp)
        )
      }
    }
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1F),
      contentAlignment = Alignment.Center,
    ) {
      if (url != null) {
        WebViewCompose(
          webViewTitle = webViewTitle,
          url = url,
        )
      } else {
        Text(text = "无网页链接")
      }
    }
  }
}

@Composable
private fun WebViewCompose(
  webViewTitle: MutableState<String?>,
  url: String,
) {
  val viewModel = viewModel<WebViewViewModel>()
  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = {
      LiteJsWebView(it).apply {
        init()
        webChromeClient = object : WebChromeClient() {
          // 加载的时候会拿到网页的标签页名字
          override fun onReceivedTitle(view: WebView?, title: String) {
            super.onReceivedTitle(view, title)
            if (webViewTitle.value == null && title.isNotEmpty()) {
              webViewTitle.value = title
            }
          }
        }
        loadUrl(url)
        viewModel.webViewStateFlow.value = this
      }
    },
    onRelease = {
      viewModel.webViewStateFlow.value = null
      it.destroy()
    }
  )
}