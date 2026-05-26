package com.cyxbs.components.base.webView

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.R
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.dir.DIR_PHOTO
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.navigation.WebViewFactory
import com.cyxbs.components.utils.extensions.doPermissionAction
import com.cyxbs.components.utils.extensions.loadBitmap
import com.cyxbs.components.utils.extensions.saveImage
import com.cyxbs.components.utils.extensions.toast
import com.g985892345.provider.api.annotation.ImplProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 通用的 webView activity
 *
 * @author 985892345
 * @date 2025/10/20
 */
class WebViewActivity : BaseActivity() {

  @ImplProvider
  companion object : WebViewFactory {

    const val ARG_URL = "url"
    const val ARG_HIDE_TITLE = "hideTitle" // 是否隐藏标题栏
    const val ARG_TITLE = "title" // 如果为 null 则优先使用网页标签页名字
    const val ARG_DEFAULT_TITLE = "defaultTitle" // 如果网页标签页名字为空则使用这个为标题



    const val DEFAULT_TITLE = "网页"

    fun start(
      url: String,
      hideTitle: Boolean = false,
      title: String? = null,
      defaultTitle: String = DEFAULT_TITLE
    ) {
      startActivity(WebViewActivity::class) {
        putExtra(ARG_URL, url)
        putExtra(ARG_HIDE_TITLE, hideTitle)
        putExtra(ARG_TITLE, title)
        putExtra(ARG_DEFAULT_TITLE, defaultTitle)
      }
    }

    override fun startWebView(url: String) {
      val uri = url.toUri()
      start(
        url = url,
        hideTitle = uri.getQueryParameter(ARG_HIDE_TITLE)?.toBooleanStrictOrNull() ?: false,
        title = uri.getQueryParameter(ARG_TITLE),
        defaultTitle = uri.getQueryParameter(ARG_DEFAULT_TITLE) ?: DEFAULT_TITLE,
      )
    }
  }

  private val mUrl by lazy { intent.getStringExtra(ARG_URL) }
  private val mHideTitle by lazy { intent.getBooleanExtra(ARG_HIDE_TITLE, false) }
  private val mTitle by lazy { intent.getStringExtra(ARG_TITLE) }
  private val mDefaultTitle by lazy { intent.getStringExtra(ARG_DEFAULT_TITLE) ?: DEFAULT_TITLE }

  // 前端通过 JS 桥控制的全屏状态
  private var isFullscreen by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      AppTheme {
        WebViewCompose(
          hideTitle = mHideTitle || isFullscreen,
          url = mUrl,
          title = mTitle,
          defaultTitle = mDefaultTitle,
          onSavePic = ::savePic,
          onSetFullscreen = ::applyFullscreen,
        )
      }
    }
  }

  // JS 调用 setFullscreen(b) 时触发：切换标题栏和 systemBars 显隐
  private fun applyFullscreen(fullscreen: Boolean) {
    runOnUiThread {
      isFullscreen = fullscreen
      val controller = WindowInsetsControllerCompat(window, window.decorView)
      if (fullscreen) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
      } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
      }
    }
  }

  // JS 调用 savePic(url) 时触发：弹确认对话框 -> 申请权限 -> 下载并保存到相册
  private fun savePic(url: String) {
    doPermissionAction(Manifest.permission.WRITE_EXTERNAL_STORAGE) {
      doAfterGranted {
        MaterialAlertDialogBuilder(this@WebViewActivity)
          .setTitle("是否保存")
          .setMessage("这张图片将保存到手机")
          .setPositiveButton("确定") { dialog, _ ->
            val name = "${System.currentTimeMillis()}${url.split('/').lastIndex}"
            this@WebViewActivity.loadBitmap(url) { bitmap ->
              this@WebViewActivity.saveImage(bitmap, name)
              MediaScannerConnection.scanFile(
                this@WebViewActivity,
                arrayOf("${Environment.getExternalStorageDirectory()}$DIR_PHOTO"),
                arrayOf("image/jpeg"),
                null,
              )
              runOnUiThread {
                toast("图片保存于${Environment.DIRECTORY_PICTURES}${DIR_PHOTO}文件夹下哦")
                dialog.dismiss()
              }
            }
          }
          .setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss()
          }
          .show()
      }
    }
  }
}

@Composable
private fun WebViewCompose(
  hideTitle: Boolean,
  url: String?,
  title: String?,
  defaultTitle: String,
  onSavePic: (String) -> Unit,
  onSetFullscreen: (Boolean) -> Unit,
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
          painter = painterResource(R.drawable.config_ic_back),
          contentDescription = null,
          modifier = Modifier
            .padding(start = 24.dp)
            .size(12.dp)
            .clickable {
              var wrapper: Context = context
              while (wrapper is ContextWrapper && wrapper !is Activity) {
                wrapper = wrapper.baseContext
              }
              (wrapper as? Activity)?.finishAfterTransition()
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
          onSavePic = onSavePic,
          onSetFullscreen = onSetFullscreen,
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
  onSavePic: (String) -> Unit,
  onSetFullscreen: (Boolean) -> Unit,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher!!
  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      LiteJsWebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        init(
          lifecycleOwner = lifecycleOwner,
          url = url,
          onBackPressedDispatcher = backDispatcher,
          onSavePic = onSavePic,
          onSetFullscreen = onSetFullscreen,
          onReceivedTitle = { t ->
            if (webViewTitle.value == null && t.isNotEmpty()) {
              webViewTitle.value = t
            }
          },
        )
      }
    },
  )
}
