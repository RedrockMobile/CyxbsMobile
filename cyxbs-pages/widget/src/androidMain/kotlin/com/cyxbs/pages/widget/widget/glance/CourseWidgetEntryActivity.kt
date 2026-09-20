package com.cyxbs.pages.widget.widget.glance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.pages.widget.api.CourseWidgetAction
import kotlinx.serialization.decodeFromString

/**
 * Glance 课程详情的透明 Activity 占位容器。
 *
 * 当前只保留独立入口和参数边界，暂不接入课表详情内容；后续完成弹窗分层后再由课程侧提供 UI。
 */
class CourseWidgetEntryActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Glance 会按 ActionParameters.Key.name 写入普通 Intent extra；先限制体积再反序列化，
    // 避免损坏或伪造的启动参数占用过多内存。
    val actionJson = intent.getStringExtra(COURSE_WIDGET_ACTION_EXTRA)
      ?.takeIf { it.length <= MAX_ACTION_JSON_LENGTH }
    val action = actionJson?.let { json ->
      runCatching { defaultJson.decodeFromString<CourseWidgetAction>(json) }.getOrNull()
    }
    if (action == null || action.week !in 1..MAX_VALID_WEEK) {
      finish()
      return
    }
    val itemId = action.itemId?.takeIf { it.isNotBlank() && it.length <= MAX_ID_LENGTH }
    if (itemId == null) {
      finish()
      return
    }
    // 暂时保留空 Compose 容器，避免在弹窗架构重构前继续耦合 course:view。
    setContent {}
  }

  private companion object {
    const val MAX_VALID_WEEK = 60
    const val MAX_ID_LENGTH = 256
    const val MAX_ACTION_JSON_LENGTH = 32 * 1024
  }
}
