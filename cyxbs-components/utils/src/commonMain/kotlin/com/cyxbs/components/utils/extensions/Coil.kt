package com.cyxbs.components.utils.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
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
  val realUrl = remember(url) {
    if (url.startsWith("http")) url else getBaseUrl() + url
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
    placeholder = painterResource(placeholder),
    error = painterResource(error),
    contentScale = contentScale
  )
}