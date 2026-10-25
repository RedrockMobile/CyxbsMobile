package com.cyxbs.pages.widget.repo

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.init.appContext
import com.cyxbs.pages.widget.api.CourseWidgetSnapshot
import com.cyxbs.pages.widget.api.CourseWidgetTimelineMark
import com.cyxbs.pages.widget.api.CourseWidgetTimelineSection
import com.cyxbs.pages.widget.api.CourseWidgetWeekSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Android 桌面课表的跨进程持久化快照。
 *
 * 一个专属 SharedPreferences 文件内把元数据和每周条目分 key 保存：更新时通过同一个 Editor 清空并写入全部 key
 * 后 commit，读取方只会看到旧快照或完整新快照；读取单周 JSON 失败只将该周降级为空，不影响其他周。
 */
object CourseWidgetSnapshotStore {

  /** 唯一的 Widget 渲染存储文件，避免与旧模块偏好及其他数据源混用。 */
  const val PREFERENCES_NAME = "course_widget_render_snapshot"

  private const val KEY_METADATA = "metadata"
  private const val KEY_WEEK_PREFIX = "week_"

  private val mutableSnapshotRevision = MutableStateFlow(0L)

  /** 进程内快照版本；活跃 Glance 会话观察它，避免更新时继续渲染会话启动时的旧对象。 */
  internal val snapshotRevision = mutableSnapshotRevision.asStateFlow()

  /** 在调用方的 IO 上下文中原子替换完整快照，并移除已不存在的旧周 key。 */
  fun replace(snapshot: CourseWidgetSnapshot) {
    val committed = preferences().edit().clear().apply {
      putString(KEY_METADATA, defaultJson.encodeToString(snapshot.toMetadata()))
      snapshot.weeks.forEach { week ->
        putString(weekKey(week.week), defaultJson.encodeToString(week))
      }
    }.commit()
    check(committed) { "课表 Widget 快照提交失败" }
    // 必须在 commit 成功后递增，观察者被唤醒时才能只读取到完整的新快照。
    mutableSnapshotRevision.update { it + 1 }
  }

  /** 读取最近一次完整快照；元数据失败时返回空快照，单周失败仅返回该周的空列表。 */
  fun read(): CourseWidgetSnapshot {
    // SharedPreferences.all 返回一次内存映射快照，防止 commit 在多次 getString 之间导致 metadata/week 拼接。
    val values = preferences().all
    val metadata = (values[KEY_METADATA] as? String)
      ?.let { decode<CourseWidgetSnapshotMetadata>(it) }
      // 限制周数避免损坏 JSON 构造超大列表；与课表导航允许的周范围保持一致。
      ?.takeIf { it.schemaVersion == 1 && it.maxWeek in 0..60 }
      ?: return emptySnapshot()
    return CourseWidgetSnapshot(
      schemaVersion = metadata.schemaVersion,
      generatedAtEpochMillis = metadata.generatedAtEpochMillis,
      firstWeekBeginEpochDays = metadata.firstWeekBeginEpochDays,
      maxWeek = metadata.maxWeek,
      timelineBeginMinute = metadata.timelineBeginMinute,
      timelineEndMinute = metadata.timelineEndMinute,
      timelineMarks = metadata.timelineMarks.orEmpty(),
      oversizedTimelineSections = metadata.oversizedTimelineSections.orEmpty(),
      weeks = (1..metadata.maxWeek).map { week ->
        (values[weekKey(week)] as? String)
          ?.let { decode<CourseWidgetWeekSnapshot>(it) }
          ?.takeIf { it.week == week }
          ?: CourseWidgetWeekSnapshot(week, emptyList())
      },
    )
  }

  /** 当前不支持未知 schema，返回显式空快照以避免新旧协议混读。 */
  private fun emptySnapshot() = CourseWidgetSnapshot(
    generatedAtEpochMillis = 0L,
    maxWeek = 0,
    weeks = emptyList(),
  )

  /** 将已提交的快照变更通知三个明确的小组件 receiver；广播回调只负责读取本地快照。 */
  fun notifyWidgets() {
    val manager = AppWidgetManager.getInstance(appContext)
    WIDGET_RECEIVER_CLASS_NAMES.forEach { className ->
      val component = ComponentName(appContext, className)
      manager.getAppWidgetIds(component).takeIf { it.isNotEmpty() }?.let { ids ->
        appContext.sendBroadcast(
          android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .setComponent(component)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
        )
      }
    }
  }

  private fun preferences() = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  private fun weekKey(week: Int) = "$KEY_WEEK_PREFIX$week"

  /** 可单测的单份 JSON 解析；解析失败必须只影响传入 key 对应的数据。 */
  internal inline fun <reified T> decode(json: String): T? =
    runCatching { defaultJson.decodeFromString<T>(json) }.getOrNull()
}

/** 元数据独立于每周项存储，便于安全确定当前快照的周范围。 */
@Serializable
private data class CourseWidgetSnapshotMetadata(
  val schemaVersion: Int,
  val generatedAtEpochMillis: Long,
  val firstWeekBeginEpochDays: Int? = null,
  val maxWeek: Int,
  val timelineBeginMinute: Int? = null,
  val timelineEndMinute: Int? = null,
  /** 旧版 metadata 不含该字段，保持可空以兼容升级前的本地快照。 */
  val timelineMarks: List<CourseWidgetTimelineMark>? = null,
  /** 旧版 metadata 不含该字段，读取时回退到 Widget 的兼容分段。 */
  val oversizedTimelineSections: List<CourseWidgetTimelineSection>? = null,
)

/** 将协议快照投影为单独存储的元数据 key。 */
private fun CourseWidgetSnapshot.toMetadata() = CourseWidgetSnapshotMetadata(
  schemaVersion = schemaVersion,
  generatedAtEpochMillis = generatedAtEpochMillis,
  firstWeekBeginEpochDays = firstWeekBeginEpochDays,
  maxWeek = maxWeek,
  timelineBeginMinute = timelineBeginMinute,
  timelineEndMinute = timelineEndMinute,
  timelineMarks = timelineMarks,
  oversizedTimelineSections = oversizedTimelineSections,
)

/** 使用字符串类名与 lane-02 的 Glance receiver 解耦，避免数据发布端依赖 UI 实现。 */
private val WIDGET_RECEIVER_CLASS_NAMES = listOf(
  "com.cyxbs.pages.widget.widget.normal.NormalWidget",
  "com.cyxbs.pages.widget.widget.oversize.OversizedAppWidget",
  "com.cyxbs.pages.widget.widget.single.SingleWidgetReceiver",
)
