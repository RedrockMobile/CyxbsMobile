package com.cyxbs.components.utils.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.utils.network.getBaseUrl
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * @Desc : 使用Coil实现网络图片加载
 * @Author : zzx
 * @Date : 2025/11/30 21:27
 */

@Composable
fun ImageFromUrlCompose(
  url: String,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
  placeholder: DrawableResource = ConfigRes.configIcPlaceHolder(),
  error: DrawableResource = ConfigRes.configIcPlaceHolder(),
  contentScale: ContentScale = ContentScale.Crop,
  block: (ImageRequest.Builder.() -> Unit)? = null
) {
  ImageFromUrlCompose(
    url = url,
    placeholder = painterResource(placeholder),
    error = painterResource(error),
    modifier = modifier,
    contentDescription = contentDescription,
    contentScale = contentScale,
    block = block,
  )
}

/**
 * 接受任意 [Painter] 作为 placeholder / error 的重载，便于把纯 Compose 绘制
 * （如 `rememberCyxbsV6BannerPainter()`）当作兜底图，不再依赖 [DrawableResource]。
 */
@Composable
fun ImageFromUrlCompose(
  url: String,
  placeholder: Painter,
  error: Painter,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
  contentScale: ContentScale = ContentScale.Crop,
  block: (ImageRequest.Builder.() -> Unit)? = null
) {
  val realUrl = remember(url) {
    if (url.startsWith("http")) url.toHttpsCdnUrl() else getBaseUrl() + url
  }
  AsyncImage(
    modifier = modifier,
    model = ImageRequest.Builder(LocalPlatformContext.current)
      .data(realUrl)
      .apply {
        // 调用block时需要导入coil的包，因为这个变成了扩展函数而不是成员函数
        block?.invoke(this)
      }
      .build(),
    contentDescription = contentDescription,
    placeholder = placeholder,
    error = error,
    contentScale = contentScale
  )
}

// 头像加载
@Composable
fun ImageAvatarCompose(
  url: String,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Crop,
  block: (ImageRequest.Builder.() -> Unit)? = null
) {
  ImageFromUrlCompose(
    url = url,
    modifier = modifier,
    contentDescription = "头像",
    placeholder = ConfigRes.configIcDefaultAvatar(),
    error = ConfigRes.configIcDefaultAvatar(),
    contentScale = contentScale,
    block = block,
  )
}

/**
 * 后端返回的为http开头，这里转成https的
 * 因为苹果的ATS不允许http，这里暂时转换一下
 */
private fun String.toHttpsCdnUrl(): String {
  return if (startsWith("http://")) {
    replaceFirst("http://", "https://")
  } else {
    this
  }
}
