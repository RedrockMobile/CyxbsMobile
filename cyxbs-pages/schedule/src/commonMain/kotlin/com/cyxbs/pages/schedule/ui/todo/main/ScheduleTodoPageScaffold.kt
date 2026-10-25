package com.cyxbs.pages.schedule.ui.todo.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrence
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryMutationMode
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepositoryStatus
import com.cyxbs.pages.schedule.ui.model.ScheduleUiOccurrence
import com.cyxbs.pages.schedule.widget.rememberIcAddtodoCategory
import com.cyxbs.pages.schedule.widget.rememberScheduleListModeIcon
import com.cyxbs.pages.schedule.widget.rememberScheduleTimelineModeIcon
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_ic_todo_empty_completed
import cyxbsmobile.cyxbs_pages.schedule.generated.resources.schedule_ic_todo_empty_pending
import org.jetbrains.compose.resources.painterResource

/**
 * 顶部分类筛选直接展示 Schedule 当前账号的真实 Category。
 *
 * null 表示聚合入口“全部”；其余项使用 Category identity 过滤，重命名分类不会丢失当前选择。
 * 横向滚动承接任意数量的自定义分类，不再假设固定存在“学习、生活、其他”。
 */
@Composable
internal fun ScheduleTodoCategoryFilterBar(
  categories: List<ScheduleCategory>,
  selectedCategoryId: CategoryId?,
  onSelect: (CategoryId?) -> Unit,
  onManageCategories: () -> Unit,
) {
  val colors = LocalAppColors.current
  val categoryIcon = rememberIcAddtodoCategory()
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LazyRow(
      modifier = Modifier.weight(1f),
      contentPadding = PaddingValues(
        start = 16.dp,
        top = 16.dp,
        end = 4.dp,
        bottom = 13.dp,
      ),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      items(
        items = listOf<Pair<CategoryId?, String>>(null to "全部") +
          categories.map { it.id to it.name },
      ) { (categoryId, label) ->
        val isSelected = categoryId == selectedCategoryId
        Surface(
          color = if (isSelected) ScheduleTodoAccentColor else Color.Transparent,
          contentColor = if (isSelected) {
            if (MaterialTheme.colors.isLight) colors.topBg else colors.tvLv1
          } else {
            colors.tvLv3
          },
          // 设计稿的分类选中块左下角为直角，其余三个角保留胶囊式圆角。
          shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 20.dp,
            bottomEnd = 20.dp,
            bottomStart = 0.dp,
          ),
          modifier = Modifier
            .height(32.dp)
            .clickableNoIndicator { onSelect(categoryId) },
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = label,
              fontSize = 14.sp,
              fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
              letterSpacing = 0.7.sp,
              modifier = Modifier.padding(horizontal = 18.dp),
            )
          }
        }
      }
    }
    Row(
      modifier = Modifier
        .padding(top = 10.dp, end = 12.dp, bottom = 7.dp)
        .height(32.dp)
        .border(1.dp, colors.tvLv2.copy(alpha = 0.24f), RoundedCornerShape(16.dp))
        .clip(RoundedCornerShape(16.dp))
        .clickableNoIndicator(onClick = onManageCategories)
        .padding(horizontal = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = categoryIcon,
        contentDescription = "管理分组",
        tint = colors.tvLv2,
        modifier = Modifier.size(17.dp),
      )
      Text(
        text = "分组",
        color = colors.tvLv2,
        fontSize = 12.sp,
      )
    }
  }
}
/** 将清单投影恢复成编辑弹窗需要的实例值；所有字段均来自同一有效 occurrence。 */
internal fun ScheduleUiOccurrence.toDomainOccurrence(): ScheduleOccurrence =
  ScheduleOccurrence(
    scheduleId = scheduleId,
    recurrenceId = recurrenceId,
    timing = timing,
    title = title,
    description = description,
    categoryId = categoryId,
    reminder = reminder,
    status = status,
    isAdjusted = isAdjusted,
  )

/**
 * 日程列表页面共用标题栏骨架。
 *
 * 返回按钮、标题基线、状态栏安全区和底部分隔线只在这里维护；[actions] 由清单主页、分组日程页分别
 * 提供，避免两个高度相同的页面继续复制一套难以同步的间距。
 */
@Composable
internal fun ScheduleListPageHeader(
  title: String,
  onBack: () -> Unit,
  actions: @Composable RowScope.() -> Unit,
) {
  val colors = LocalAppColors.current
  Surface(
    color = colors.bottomBg,
    modifier = Modifier.statusBarsPadding(),
  ) {
    Column(modifier = Modifier.padding(top = 13.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(37.dp)
          .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .width(22.dp)
            .height(37.dp)
            .clickableNoIndicator(onClick = onBack),
          contentAlignment = Alignment.CenterStart,
        ) {
          Icon(
            painter = painterResource(ConfigRes.configIcBack()),
            contentDescription = "返回",
            tint = colors.tvLv1,
            modifier = Modifier.width(9.dp).height(19.dp),
          )
        }
        Text(
          text = title,
          color = colors.tvLv1,
          fontSize = 21.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.05.sp,
          modifier = Modifier.weight(1f),
          maxLines = 1,
        )
        actions()
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(1.dp)
          .background(colors.tvLv4.copy(alpha = 0.1f)),
      )
    }
  }
}

/** 清单类页面共用的批量管理入口；进入态使用文字按钮，管理态使用强调色“完成”胶囊。 */
@Composable
internal fun ScheduleManageHeaderAction(
  manageMode: Boolean,
  onEnter: () -> Unit,
  onDone: () -> Unit,
) {
  val colors = LocalAppColors.current
  if (manageMode) {
    Surface(
      color = ScheduleTodoAccentColor,
      contentColor = if (MaterialTheme.colors.isLight) ScheduleTodoHeaderOnAccentColor else colors.tvLv1,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.clickableNoIndicator(onClick = onDone),
    ) {
      Text(
        text = "完成",
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
      )
    }
  } else {
    TextButton(onClick = onEnter) {
      Text(text = "批量管理", color = colors.tvLv2, fontSize = 13.sp)
    }
  }
}

/** 顶部栏保留 Figma 的清单操作，并复用 [ScheduleListPageHeader] 的公共布局。 */
@Composable
internal fun ScheduleTodoHeader(
  manageMode: Boolean,
  failureCount: Int,
  viewMode: ScheduleTodoViewMode,
  onBack: () -> Unit,
  onFailures: () -> Unit,
  onToggleViewMode: () -> Unit,
  onSettings: () -> Unit,
  onManageDone: () -> Unit,
) {
  val colors = LocalAppColors.current
  val timelineIcon = rememberScheduleTimelineModeIcon()
  val listIcon = rememberScheduleListModeIcon()
  ScheduleListPageHeader(title = "邮子清单", onBack = onBack) {
    if (!manageMode && failureCount > 0) {
      TextButton(onClick = onFailures) {
        Text(
          text = "失败 $failureCount",
          color = MaterialTheme.colors.error,
          fontSize = 13.sp,
        )
      }
    }
    if (!manageMode) {
      IconButton(onClick = onToggleViewMode) {
        Icon(
          imageVector = if (viewMode == ScheduleTodoViewMode.LIST) timelineIcon else listIcon,
          contentDescription = if (viewMode == ScheduleTodoViewMode.LIST) {
            "切换到时间轴"
          } else {
            "切换到清单列表"
          },
          tint = colors.tvLv1,
        )
      }
      IconButton(onClick = onSettings) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = "设置",
          tint = colors.tvLv1,
        )
      }
    } else {
      ScheduleManageHeaderAction(
        manageMode = true,
        onEnter = {},
        onDone = onManageDone,
      )
    }
  }
}

/** 顶部提醒条只显示当前临期/超期事实，不承担同步或重试入口。 */
@Composable
internal fun ScheduleTodoUrgentBanner(count: Int) {
  val colors = LocalAppColors.current
  val bannerTextColor = if (MaterialTheme.colors.isLight) colors.topBg else colors.tvLv1
  Surface(
    color = ScheduleTodoAccentColor,
    contentColor = bannerTextColor,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 34.dp, bottomEnd = 34.dp),
    modifier = Modifier
      .fillMaxWidth()
      // 上方留白负责与分类栏分隔；下方距离由列表 contentPadding 统一控制，避免两段间距叠加。
      .padding(start = 16.dp, top = 8.dp, end = 16.dp)
      .height(52.dp),
  ) {
    Box(contentAlignment = Alignment.CenterStart) {
      Text(
        text = buildAnnotatedString {
          append("你有 ")
          withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(count.toString()) }
          append(" 项待办即将到期或已超期")
        },
        color = if (MaterialTheme.colors.isLight) ScheduleTodoOnAccentColor else colors.tvLv1,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.48.sp,
        lineHeight = 25.sp,
        modifier = Modifier.padding(horizontal = 18.dp),
      )
    }
  }
}

/**
 * 清单列表分区标题。
 *
 * [allSelected] 为 null 时只展示标题；非 null 时在右侧展示当前分区的全选/取消全选入口。
 * 选择范围由调用方传入 [onToggleSelectAll] 处理，组件不感知跨分区的批量状态。
 */
@Composable
internal fun ScheduleTodoSectionTitle(
  text: String,
  allSelected: Boolean? = null,
  onToggleSelectAll: () -> Unit = {},
) {
  val colors = LocalAppColors.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = text,
      color = colors.tvLv3,
      fontSize = 20.sp,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = 1.sp,
    )
    Spacer(Modifier.weight(1F))
    if (allSelected != null) {
      Text(
        text = if (allSelected) "取消全选" else "全选",
        color = colors.tvLv2,
        fontSize = 13.sp,
        modifier = Modifier
          .clip(RoundedCornerShape(4.dp))
          .clickableNoIndicator(onClick = onToggleSelectAll)
          .padding(horizontal = 4.dp, vertical = 3.dp),
      )
    }
  }
}

/**
 * 按 Figma 分别展示未完成、已完成为空时的原始矢量插画与提示语。
 *
 * [completed] 决定选用哪一张插画；Compose Resources 会按当前主题自动加载 drawable 或
 * drawable-dark 中的同名资源，因此深色稿不经过运行时猜色或透明度变换。
 */
@Composable
internal fun ScheduleTodoEmptyCard(completed: Boolean) {
  val colors = LocalAppColors.current
  val artwork = if (completed) {
    Res.drawable.schedule_ic_todo_empty_completed
  } else {
    Res.drawable.schedule_ic_todo_empty_pending
  }
  val artworkSize = if (completed) 179.dp to 100.dp else 151.dp to 111.dp
  val message = if (completed) {
    "还没有已完成事项哦，期待你的好消息！"
  } else {
    "还没有待做事项哦，快去添加吧！"
  }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      // Figma 中两个空态都占据 200dp，使下一分区标题不会因插画内容较矮而提前上移。
      .height(200.dp)
      .padding(top = if (completed) 31.dp else 27.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Image(
      painter = painterResource(artwork),
      contentDescription = message,
      modifier = Modifier.size(width = artworkSize.first, height = artworkSize.second),
    )
    // 两张稿件的插画与说明文字均保留 16dp 间距。
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = message,
      color = colors.tvLv3.copy(alpha = if (MaterialTheme.colors.isLight) 0.72f else 0.62f),
      fontSize = 12.sp,
      letterSpacing = 0.3.sp,
    )
  }
}

/** 将提醒偏移量转换为清单页与主页 Feed 共用的完整提示语。 */
internal fun formatScheduleTodoReminder(offsetMinutes: Int): String? = when {
  offsetMinutes < 0 -> null
  offsetMinutes == 0 -> "准时提醒"
  offsetMinutes % 60 == 0 -> "提前${offsetMinutes / 60}小时提醒"
  else -> "提前${offsetMinutes}分钟提醒"
}

/**
 * 展示会直接影响当前操作的仓库状态。
 *
 * 远端暂不可用时仍允许 local-first 编辑，因此这里保持静默；底层继续保留失败状态与待同步数据。
 */
@Composable
internal fun ScheduleTodoSyncStatus(
  status: ScheduleRepositoryStatus,
  mutationMode: ScheduleRepositoryMutationMode,
) {
  val colors = LocalAppColors.current
  when {
    status is ScheduleRepositoryStatus.Corrupted -> {
      Surface(color = MaterialTheme.colors.error.copy(alpha = 0.1f)) {
        Text(
          text = "日程数据暂时无法读取。",
          color = MaterialTheme.colors.error,
          fontSize = 12.sp,
          modifier = Modifier.fillMaxWidth().padding(8.dp),
        )
      }
    }

    mutationMode == ScheduleRepositoryMutationMode.READ_ONLY -> {
      Surface(color = MaterialTheme.colors.error.copy(alpha = 0.1f)) {
        Text(
          text = "当前没有可编辑的登录账号，仅可查看事项。",
          color = MaterialTheme.colors.error,
          fontSize = 12.sp,
          modifier = Modifier.fillMaxWidth().padding(8.dp),
        )
      }
    }

    status == ScheduleRepositoryStatus.Loading -> {
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth(),
        color = colors.positive,
      )
    }

    else -> Unit
  }
}
