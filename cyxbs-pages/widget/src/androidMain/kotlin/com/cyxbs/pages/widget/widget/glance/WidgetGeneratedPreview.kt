package com.cyxbs.pages.widget.widget.glance

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetManager.Companion.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.cyxbs.components.init.appContext
import com.cyxbs.pages.widget.api.CourseWidgetAction
import com.cyxbs.pages.widget.api.CourseWidgetBackgroundPattern
import com.cyxbs.pages.widget.api.CourseWidgetItemStyle
import com.cyxbs.pages.widget.api.CourseWidgetRenderItem
import com.cyxbs.pages.widget.api.CourseWidgetSnapshot
import com.cyxbs.pages.widget.api.CourseWidgetTimelineMark
import com.cyxbs.pages.widget.api.CourseWidgetVisibleRange
import com.cyxbs.pages.widget.api.CourseWidgetWeekSnapshot
import com.cyxbs.pages.widget.widget.normal.NormalWidget
import com.cyxbs.pages.widget.widget.oversize.OversizedAppWidget
import com.cyxbs.pages.widget.widget.single.SingleWidgetReceiver
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 读取生成预览使用的课表快照。
 *
 * 已登录用户优先预览真实快照；首次安装或快照损坏时使用本地示例，避免 widget 选择器只显示空白卡片。
 */
internal fun readCourseWidgetPreviewSnapshot(
  calendar: Calendar = Calendar.getInstance(),
): CourseWidgetSnapshot {
  val stored = readCourseWidgetSnapshot()
  if (stored.maxWeek > 0 && stored.weeks.any { it.items.isNotEmpty() }) return stored
  return createCourseWidgetPreviewSnapshot(calendar)
}

/**
 * 为三种尺寸构造确定性的示例课表。
 *
 * 示例包含当前课程、全天事项、普通课程和斜纹事务，使高级预览能够覆盖现有主要视觉语义；
 * 点击 ID 保持为空，防止预览内容被误认为真实业务条目。
 */
internal fun createCourseWidgetPreviewSnapshot(calendar: Calendar): CourseWidgetSnapshot {
  val today = calendar.mondayBasedDay()
  val nowMinute = currentMinute(calendar)
  val activeBegin = (nowMinute - 30).coerceAtLeast(0)
  val activeEnd = (nowMinute + 60).coerceAtMost(24 * 60).coerceAtLeast(activeBegin + 1)

  /** 把周一开头的列下标转换为协议使用的 ISO 1..7。 */
  fun day(offset: Int): Int = (today + offset) % 7 + 1

  /** 创建一个不响应点击的示例时间段。 */
  fun timedItem(
    id: String,
    dayOfWeek: Int,
    title: String,
    content: String,
    beginMinute: Int,
    endMinute: Int,
    style: CourseWidgetItemStyle,
    pattern: CourseWidgetBackgroundPattern = CourseWidgetBackgroundPattern.SOLID,
  ) = CourseWidgetRenderItem(
    id = id,
    dayOfWeek = dayOfWeek,
    title = title,
    content = content,
    beginMinute = beginMinute,
    endMinute = endMinute,
    visibleRanges = listOf(
      CourseWidgetVisibleRange(
        beginMinute = beginMinute,
        endMinute = endMinute,
        beginRatio = 0F,
        endRatio = 1F,
      ),
    ),
    lightStyle = style,
    darkStyle = style.copy(contentArgb = 0xFFF0F0F2L),
    backgroundPattern = pattern,
    action = CourseWidgetAction(week = 1),
  )

  val items = listOf(
    CourseWidgetRenderItem(
      id = "preview-all-day",
      dayOfWeek = day(0),
      title = "英语作业",
      content = "",
      isAllDay = true,
      lightStyle = BLUE_STYLE,
      darkStyle = BLUE_STYLE.copy(contentArgb = 0xFFF0F0F2L),
      action = CourseWidgetAction(week = 1),
    ),
    timedItem(
      id = "preview-current",
      dayOfWeek = day(0),
      title = "数据结构",
      content = "综合楼 305",
      beginMinute = activeBegin,
      endMinute = activeEnd,
      style = lessonPreviewStyle(activeBegin),
    ),
    timedItem(
      id = "preview-math",
      dayOfWeek = day(0),
      title = "高等数学",
      content = "二教 201",
      beginMinute = 8 * 60,
      endMinute = 9 * 60 + 40,
      style = MORNING_STYLE,
    ),
    timedItem(
      id = "preview-physics",
      dayOfWeek = day(0),
      title = "大学物理",
      content = "三教 101",
      beginMinute = 14 * 60,
      endMinute = 15 * 60 + 40,
      style = AFTERNOON_STYLE,
    ),
    timedItem(
      id = "preview-next-day",
      dayOfWeek = day(1),
      title = "计算机网络",
      content = "逸夫楼 203",
      beginMinute = 10 * 60 + 15,
      endMinute = 11 * 60 + 55,
      style = MORNING_STYLE,
    ),
    timedItem(
      id = "preview-affair",
      dayOfWeek = day(2),
      title = "社团例会",
      content = "学生活动中心",
      beginMinute = 19 * 60,
      endMinute = 20 * 60 + 40,
      style = AFFAIR_STYLE,
      pattern = CourseWidgetBackgroundPattern.DIAGONAL_STRIPE,
    ),
  )
  return CourseWidgetSnapshot(
    generatedAtEpochMillis = 0L,
    maxWeek = 1,
    timelineBeginMinute = PREVIEW_TIMELINE_BEGIN_MINUTE,
    timelineEndMinute = PREVIEW_TIMELINE_END_MINUTE,
    // 示例刻度和横条使用同一线性范围，保证预览中的小时标签等距且与条目对齐。
    timelineMarks = (8..22 step 2).map { hour ->
      val minute = hour * 60
      CourseWidgetTimelineMark(
        label = hour.toString(),
        ratio = (minute - PREVIEW_TIMELINE_BEGIN_MINUTE).toFloat() /
          (PREVIEW_TIMELINE_END_MINUTE - PREVIEW_TIMELINE_BEGIN_MINUTE),
      )
    },
    oversizedTimelineSections = DEFAULT_OVERSIZED_TIMELINE_SECTIONS,
    weeks = listOf(CourseWidgetWeekSnapshot(week = 1, items = items)),
  )
}

private const val PREVIEW_TIMELINE_BEGIN_MINUTE = 8 * 60
private const val PREVIEW_TIMELINE_END_MINUTE = 22 * 60 + 30

/**
 * Android 15+ 生成预览发布器。
 *
 * 系统 API 有频率限制，因此每个 receiver 使用独立 key 记录最近尝试时间与成功快照；不同数据源不与
 * 课表渲染快照共用 SharedPreferences。失败不会影响小组件刷新，后续快照发布会在节流窗口后重试。
 */
internal object CourseWidgetGeneratedPreviewPublisher {
  private const val PREFERENCES_NAME = "course_widget_generated_preview"
  private const val PREVIEW_SCHEMA_VERSION = 1
  private const val MIN_ATTEMPT_INTERVAL_MILLIS = 30 * 60 * 1000L

  /**
   * 在真实快照持久化后发布三种高级预览。
   *
   * [snapshotGeneratedAtEpochMillis] 参与成功标识，保证真实数据更新后能在系统限频允许时刷新预览。
   */
  suspend fun publishIfAllowed(snapshotGeneratedAtEpochMillis: Long) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    publishAndroid15(
      context = appContext,
      snapshotToken = "$PREVIEW_SCHEMA_VERSION:$snapshotGeneratedAtEpochMillis",
    )
  }

  /** 串行发布每个 provider，避免三个 RemoteViews 组合同时占用过多资源。 */
  @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
  private suspend fun publishAndroid15(
    context: Context,
    snapshotToken: String,
  ) = withContext(Dispatchers.Default) {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val manager = GlanceAppWidgetManager(context)
    val now = System.currentTimeMillis()
    PREVIEW_TARGETS.forEach { target ->
      val key = target.receiver.name
      val hasCurrentPreview =
        preferences.getString("success_$key", null) == snapshotToken &&
          context.hasGeneratedPreview(target)
      if (hasCurrentPreview) return@forEach

      val lastAttempt = preferences.getLong("attempt_$key", 0L)
      if (now - lastAttempt in 0 until MIN_ATTEMPT_INTERVAL_MILLIS) return@forEach
      // 先提交尝试时间；即使进程在系统调用期间结束，也不会形成高频重试。
      if (!preferences.edit().putLong("attempt_$key", now).commit()) return@forEach

      val result = try {
        manager.setWidgetPreviews(
          receiver = target.receiver.kotlin,
          widgetCategories = intSetOf(*target.categories),
        )
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (_: Throwable) {
        null
      }
      if (result == SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
        preferences.edit().putString("success_$key", snapshotToken).commit()
      }
    }
  }

  /** 检查 launcher 是否仍保留该 provider 对应类别的生成预览。 */
  @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
  private fun Context.hasGeneratedPreview(target: PreviewTarget): Boolean {
    val component = ComponentName(this, target.receiver)
    val manager = getSystemService(AppWidgetManager::class.java) ?: return false
    val categories = manager.installedProviders
      .firstOrNull { it.provider == component }
      ?.generatedPreviewCategories
      ?: return false
    val expectedCategories = target.categories.fold(0) { mask, category -> mask or category }
    return categories and expectedCategories == expectedCategories
  }
}

/** 单个 Glance receiver 及其在 manifest 中声明的 widget 类别。 */
private class PreviewTarget(
  val receiver: Class<out GlanceAppWidgetReceiver>,
  /** 系统 API 要求逐个传入类别常量，不能传入按位或后的组合值。 */
  val categories: IntArray,
)

private val PREVIEW_TARGETS = listOf(
  PreviewTarget(
    NormalWidget::class.java,
    intArrayOf(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN),
  ),
  PreviewTarget(
    OversizedAppWidget::class.java,
    intArrayOf(
      AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
      AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
    ),
  ),
  PreviewTarget(
    SingleWidgetReceiver::class.java,
    intArrayOf(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN),
  ),
)

private val BLUE_STYLE = CourseWidgetItemStyle(
  contentArgb = 0xFF4066EAL,
  backgroundArgb = 0xFFDDE3F8L,
)

private val MORNING_STYLE = CourseWidgetItemStyle(
  contentArgb = 0xFFFF8015L,
  backgroundArgb = 0xFFF9E7D8L,
)

private val AFTERNOON_STYLE = CourseWidgetItemStyle(
  contentArgb = 0xFFFF6262L,
  backgroundArgb = 0xFFF9E3E4L,
)

/** 系统生成预览的模拟课程与真实课表采用相同的时段颜色规则。 */
private fun lessonPreviewStyle(beginMinute: Int): CourseWidgetItemStyle = when {
  beginMinute < 12 * 60 -> MORNING_STYLE
  beginMinute < 18 * 60 -> AFTERNOON_STYLE
  else -> BLUE_STYLE
}

private val AFFAIR_STYLE = CourseWidgetItemStyle(
  contentArgb = 0xFF112C57L,
  backgroundArgb = 0x00000000L,
  stripeArgb = 0xFFE4E7ECL,
)
