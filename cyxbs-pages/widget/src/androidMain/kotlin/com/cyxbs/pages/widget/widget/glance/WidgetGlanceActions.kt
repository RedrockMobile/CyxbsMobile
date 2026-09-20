package com.cyxbs.pages.widget.widget.glance

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.pages.widget.api.CourseWidgetAction
import com.cyxbs.pages.widget.widget.normal.NormalGlanceWidget
import com.cyxbs.pages.widget.widget.oversize.OversizedGlanceWidget
import kotlinx.serialization.encodeToString

internal const val ACTION_WIDGET_REFRESH = "com.cyxbs.pages.widget.action.REFRESH"
internal const val ACTION_WIDGET_FLUSH_LEGACY = "flush"
internal val DayDeltaKey = ActionParameters.Key<Int>("widget_day_delta")
internal val WidgetDayOffsetKey = intPreferencesKey("widget_day_offset")
internal val OversizedTimelineSectionIndexKey =
  ActionParameters.Key<Int>("oversized_timeline_section_index")
internal val OversizedExpandedTimelineMaskKey =
  intPreferencesKey("oversized_expanded_timeline_mask")
internal const val COURSE_WIDGET_ACTION_EXTRA = "course_widget_action"
private val CourseWidgetActionKey = ActionParameters.Key<String>(COURSE_WIDGET_ACTION_EXTRA)

/** 修改单个 normal widget 在当前周内的星期偏移，不修改发布者生成的快照。 */
class ChangeNormalDayAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    val delta = parameters[DayDeltaKey] ?: 0
    val today = java.util.Calendar.getInstance().mondayBasedDay()
    updateAppWidgetState(context, glanceId) { preferences ->
      preferences[WidgetDayOffsetKey] = normalizedDayOffset(
        today = today,
        storedOffset = preferences[WidgetDayOffsetKey] ?: 0,
        delta = delta,
      )
    }
    NormalGlanceWidget.update(context, glanceId)
  }
}

/** 切换单个大号 Widget 的时间段展开位，不影响同一桌面上的其他实例。 */
class ToggleOversizedTimelineSectionAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    val sectionIndex = parameters[OversizedTimelineSectionIndexKey] ?: return
    if (sectionIndex !in 0 until Int.SIZE_BITS - 1) return
    updateAppWidgetState(context, glanceId) { preferences ->
      val mask = preferences[OversizedExpandedTimelineMaskKey] ?: 0
      preferences[OversizedExpandedTimelineMaskKey] = mask xor (1 shl sectionIndex)
    }
    OversizedGlanceWidget.update(context, glanceId)
  }
}

/**
 * 直接启动透明入口 Activity；参数只包含通用 CourseWidgetAction，不携带渲染内容。
 */
internal fun openCourseWidgetItemAction(action: CourseWidgetAction) =
  actionStartActivity(
    CourseWidgetEntryActivity::class.java,
    actionParametersOf(CourseWidgetActionKey to defaultJson.encodeToString(action)),
  )

/**
 * 将新刷新 action 与旧 flush 转换为系统更新广播，交回 Glance receiver 异步处理。
 * receiver 不执行网络或业务数据刷新。
 */
internal fun dispatchRefreshToGlanceReceiver(context: Context, receiverClass: Class<*>) {
  val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, receiverClass))
  if (ids.isEmpty()) return
  context.sendBroadcast(
    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
      .setComponent(ComponentName(context, receiverClass))
      .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
  )
}
