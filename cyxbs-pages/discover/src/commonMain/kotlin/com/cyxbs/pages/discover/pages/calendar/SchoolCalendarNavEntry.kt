package com.cyxbs.pages.discover.pages.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.compose.theme.LocalAppDark
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SCHOOL_CALENDAR
import com.cyxbs.components.utils.extensions.ImageFromUrlCompose
import com.cyxbs.pages.discover.home.bean.SchoolCalendarCdnBean
import com.cyxbs.pages.discover.home.bean.imageCacheKey
import com.cyxbs.pages.discover.home.bean.versionedImageUrl
import kotlinx.serialization.Serializable

/**
 * 校历页面导航参数。
 *
 * 页面不需要业务入参，使用 object 保证同一入口拥有稳定的导航身份。
 */
@Serializable
object SchoolCalendarNavArgument : AppNavArgument

/**
 * 所有已启用 CMP 平台共用的校历导航页面。
 */
@AppNav(route = NAV_SCHOOL_CALENDAR)
class SchoolCalendarNavEntry : AppNavEntry<SchoolCalendarNavArgument>() {

  /** 校历是公开内容，不要求登录。 */
  override fun isNeedLogin(argument: SchoolCalendarNavArgument): Boolean = false

  /** 创建页面级 ViewModel 并渲染跨平台校历。 */
  @Composable
  override fun Content(argument: SchoolCalendarNavArgument) {
    val viewModel = viewModel { SchoolCalendarViewModel() }
    SchoolCalendarScreen(
      argument = argument,
      viewModel = viewModel,
    )
  }
}

/**
 * 展示校历图片以及元数据加载状态。
 *
 * 缓存键同时使用 URL 与版本号：任意一个变化都会立即下载新图；两者不变时持续复用缓存。
 * 接口失败时仍可继续展示上一次保存的元数据对应的缓存。
 */
@Composable
private fun SchoolCalendarScreen(
  argument: SchoolCalendarNavArgument,
  viewModel: SchoolCalendarViewModel,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(LocalAppColors.current.bottomBg)
      .statusBarsPadding(),
  ) {
    SchoolCalendarTopBar(onBack = argument::popBackStack)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .navigationBarsPadding(),
    ) {
      when (val state = uiState) {
        SchoolCalendarUiState.Loading -> {
          CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        SchoolCalendarUiState.Error -> {
          SchoolCalendarError(
            onRetry = viewModel::refresh,
            modifier = Modifier.align(Alignment.Center),
          )
        }

        is SchoolCalendarUiState.Content -> {
          SchoolCalendarImage(calendar = state.calendar)
          if (state.isRefreshing) {
            CircularProgressIndicator(
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(20.dp),
              strokeWidth = 2.dp,
            )
          }
        }
      }
    }
  }
}

/**
 * 校历页面顶部返回栏。
 */
@Composable
private fun SchoolCalendarTopBar(onBack: () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp),
  ) {
    IconButton(
      modifier = Modifier.align(Alignment.CenterStart),
      onClick = onBack,
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "返回",
        tint = LocalAppColors.current.tvLv1,
      )
    }
    Text(
      text = "校历",
      modifier = Modifier
        .align(Alignment.Center)
        .padding(vertical = 14.dp),
      color = LocalAppColors.current.tvLv1,
      fontSize = 20.sp,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

/**
 * 加载并纵向滚动展示完整校历图片。
 *
 * 暗色矩阵复用旧 Android 页面的反色与灰度组合，并在 CMP 后作用于所有支持该绘制能力的平台。
 */
@Composable
private fun SchoolCalendarImage(
  calendar: SchoolCalendarCdnBean,
) {
  val imageUrl = remember(calendar) { calendar.versionedImageUrl() }
  val cacheKey = remember(calendar) { calendar.imageCacheKey() }
  val darkColorFilter = if (LocalAppDark.current) {
    remember { createSchoolCalendarDarkColorFilter() }
  } else {
    null
  }
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
  ) {
    ImageFromUrlCompose(
      url = imageUrl,
      modifier = Modifier.fillMaxWidth(),
      contentDescription = "重庆邮电大学校历",
      contentScale = ContentScale.FillWidth,
      colorFilter = darkColorFilter,
    ) {
      // URL 或版本任一变化都会生成新键，同时绕过内存与磁盘中的旧图片。
      memoryCacheKey(cacheKey)
      diskCacheKey(cacheKey)
    }
  }
}

/**
 * 展示无缓存且元数据请求失败时的重试入口。
 */
@Composable
private fun SchoolCalendarError(
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = "校历服务暂时不可用",
      color = LocalAppColors.current.tvLv2,
      fontSize = 15.sp,
    )
    TextButton(onClick = onRetry) {
      Text(text = "重新加载")
    }
  }
}

/**
 * 创建旧 Android 校历页使用的暗色 ColorMatrix。
 *
 * 先反色，再叠加旧实现的灰度权重矩阵，避免纯白校历在暗色模式下过亮。
 */
private fun createSchoolCalendarDarkColorFilter(): ColorFilter {
  val matrix = ColorMatrix(
    floatArrayOf(
      -1f, 0f, 0f, 0f, 255f,
      0f, -1f, 0f, 0f, 255f,
      0f, 0f, -1f, 0f, 255f,
      0f, 0f, 0f, 1f, 0f,
    ),
  )
  matrix *= ColorMatrix(
    floatArrayOf(
      0.6333f, 0.0333f, 0.3333f, 0f, 0f,
      0.6333f, 0.0333f, 0.3333f, 0f, 0f,
      0.6333f, 0.0333f, 0.3333f, 0f, 0f,
      0f, 0f, 0f, 1f, 0f,
    ),
  )
  return ColorFilter.colorMatrix(matrix)
}
