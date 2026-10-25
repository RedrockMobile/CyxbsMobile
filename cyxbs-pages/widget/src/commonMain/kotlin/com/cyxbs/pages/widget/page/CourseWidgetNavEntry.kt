package com.cyxbs.pages.widget.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_WIDGET
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.ui.ChooseDialogCompose
import kotlinx.serialization.Serializable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.seconds

/** 小组件目录页路由参数。 */
@Serializable
data object CourseWidgetNavArgument : AppNavArgument

/** Widget 模块独立维护的小组件目录页。 */
@AppNav(route = NAV_WIDGET)
class CourseWidgetNavEntry : AppNavEntry<CourseWidgetNavArgument>() {

  override fun isNeedLogin(argument: CourseWidgetNavArgument): Boolean = false

  @Composable
  override fun Content(argument: CourseWidgetNavArgument) {
    CourseWidgetPage(argument)
  }
}

/** 小组件页面使用的静态说明；具体 Receiver 与系统调用不会泄漏给设置模块。 */
private data class CourseWidgetCatalogItem(
  val kind: CourseWidgetKind,
  val title: String,
  val description: String,
  val sizeHint: String,
  val previewHeight: Dp,
)

private val CourseWidgetCatalog = listOf(
  CourseWidgetCatalogItem(
    kind = CourseWidgetKind.CURRENT_COURSE,
    title = "当前课程",
    description = "显示当前或下一节课程的时间、标题与地点",
    sizeHint = "2 × 1",
    previewHeight = 118.dp,
  ),
  CourseWidgetCatalogItem(
    kind = CourseWidgetKind.DAY_TIMELINE,
    title = "今日课表",
    description = "按连续时间轴展示当天课程和事务，可切换日期",
    sizeHint = "4 × 1",
    previewHeight = 108.dp,
  ),
  CourseWidgetCatalogItem(
    kind = CourseWidgetKind.WEEK_TIMETABLE,
    title = "周课表",
    description = "支持 1、3、5、7 天视图和可展开时间轴",
    sizeHint = "可调整宽高",
    previewHeight = 360.dp,
  ),
)

/**
 * 展示应用提供的全部桌面课表组件。
 *
 * Android 8.0+ 且桌面支持固定请求时显示一键添加；其他环境统一回退为手动添加指引。
 */
@Composable
private fun CourseWidgetPage(argument: CourseWidgetNavArgument) {
  val platform = remember { CourseWidgetPagePlatform::class.implOrNull() }
  val showManualGuide = remember { mutableStateOf(false) }
  val pageScope = rememberCoroutineScope()

  Column(
    modifier = Modifier.fillMaxSize()
      .background(LocalAppColors.current.bottomBg)
      .statusBarsPadding(),
  ) {
    CourseWidgetTopBar(argument)
    LazyColumn(
      modifier = Modifier.fillMaxSize().navigationBarsPadding(),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(
        start = 16.dp,
        top = 16.dp,
        end = 16.dp,
        bottom = 24.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Text(
          text = "选择一种样式添加到桌面，组件内容会跟随课表自动更新。",
          color = LocalAppColors.current.tvLv2,
          fontSize = 14.sp,
          lineHeight = 21.sp,
        )
      }
      items(items = CourseWidgetCatalog, key = { it.kind.name }) { item ->
        CourseWidgetCatalogCard(
          item = item,
          platform = platform,
          onShowManualGuide = { showManualGuide.value = true },
          onPinRequestDelivered = { kind, previousCount ->
            pageScope.launch {
              // MIUI 可能直接吞掉请求；稍等桌面处理后再按实例数判断，成功提示由系统回调统一发送。
              delay(1.seconds)
              if ((platform?.pinnedWidgetCount(kind) ?: 0) <= previousCount) {
                showManualGuide.value = true
              }
            }
          },
        )
      }
    }
  }

  ChooseDialogCompose(
    showState = showManualGuide,
    positiveBtnText = if (platform?.needsPinPermissionGuide == true) "打开应用信息" else "知道了",
    negativeBtnText = if (platform?.needsPinPermissionGuide == true) "知道了" else null,
    onClickPositiveBtn = {
      showManualGuide.value = false
      if (platform?.needsPinPermissionGuide == true && !platform.openAppSettings()) {
        toast("无法打开应用信息，请从系统设置中手动查找")
      }
    },
    onClickNegativeBtn = { showManualGuide.value = false },
  ) {
    Text(
      text = "如何添加桌面小组件",
      color = LocalAppColors.current.tvLv1,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
      textAlign = TextAlign.Center,
    )
    Text(
      text = buildString {
        if (platform?.needsPinPermissionGuide == true) {
          append("若点击后未添加，请先在系统的应用信息 → 权限管理中允许“桌面快捷方式”。\n\n")
        }
        append("也可以手动添加：\n1. 返回手机桌面并长按空白处\n")
        append("2. 选择“小组件”或“桌面挂件”\n3. 找到“掌上重邮”\n")
        append("4. 按住所需样式并拖到桌面")
      },
      color = LocalAppColors.current.tvLv2,
      fontSize = 14.sp,
      lineHeight = 24.sp,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp),
    )
  }
}

/** 绘制标题栏并通过 Navigation3 返回设置页。 */
@Composable
private fun CourseWidgetTopBar(argument: CourseWidgetNavArgument) {
  TopAppBar(backgroundColor = Color.Transparent, elevation = 0.dp) {
    Box(modifier = Modifier.fillMaxSize()) {
      androidx.compose.foundation.Image(
        painter = painterResource(ConfigRes.configIcBack()),
        contentDescription = "返回",
        modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp).size(16.dp)
          .clickableSingle { argument.popBackStack() },
      )
      Text(
        text = "桌面小组件",
        color = LocalAppColors.current.tvLv1,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.align(Alignment.Center),
      )
    }
  }
}

/** 单个小组件样式卡片，预览与添加行为均委托给 Widget 自身的平台实现。 */
@Composable
private fun CourseWidgetCatalogCard(
  item: CourseWidgetCatalogItem,
  platform: CourseWidgetPagePlatform?,
  onShowManualGuide: () -> Unit,
  onPinRequestDelivered: (CourseWidgetKind, Int) -> Unit,
) {
  // 三种目录卡片统一用相同底色；2×1 组件自身仍保持透明，不给它单独绘制背景。
  val cardBackground = if (MaterialTheme.colors.isLight) Color(0xFFE8EDF5)
  else Color(0xFF222A35)
  Column(
    modifier = Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(cardBackground)
      .padding(16.dp),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1F)) {
        Text(
          text = item.title,
          color = LocalAppColors.current.tvLv1,
          fontSize = 17.sp,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = item.description,
          color = LocalAppColors.current.tvLv2,
          fontSize = 13.sp,
          lineHeight = 19.sp,
          modifier = Modifier.padding(top = 4.dp),
        )
      }
      Text(
        text = item.sizeHint,
        color = LocalAppColors.current.positive,
        fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
          .background(LocalAppColors.current.topBg)
          .padding(horizontal = 9.dp, vertical = 5.dp),
      )
    }

    Box(
      modifier = Modifier.fillMaxWidth().height(item.previewHeight).padding(top = 14.dp),
      contentAlignment = Alignment.Center,
    ) {
      if (platform == null) {
        Text(
          text = "当前平台不支持预览",
          color = LocalAppColors.current.tvLv3,
          fontSize = 13.sp,
        )
      } else {
        platform.RenderPreview(item.kind, Modifier.fillMaxSize())
      }
    }

    Spacer(Modifier.height(14.dp))
    Button(
      onClick = {
        if (platform?.isPinRequestSupported == true) {
          val previousCount = if (platform.needsPinPermissionGuide) {
            platform.pinnedWidgetCount(item.kind)
          } else {
            0
          }
          if (platform.requestPinWidget(item.kind)) {
            if (platform.needsPinPermissionGuide) {
              onPinRequestDelivered(item.kind, previousCount)
            }
          } else {
            onShowManualGuide()
          }
        } else {
          onShowManualGuide()
        }
      },
      modifier = Modifier.align(Alignment.End).height(38.dp),
      colors = ButtonDefaults.buttonColors(
        backgroundColor = LocalAppColors.current.positive,
        contentColor = Color.White,
      ),
      shape = RoundedCornerShape(12.dp),
      elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp),
    ) {
      Text(
        text = if (platform?.isPinRequestSupported == true) "添加到桌面" else "查看添加方法",
        fontSize = 14.sp,
      )
    }
  }
}
