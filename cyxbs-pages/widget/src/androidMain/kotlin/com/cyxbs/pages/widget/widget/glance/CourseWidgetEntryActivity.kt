package com.cyxbs.pages.widget.widget.glance

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.api.CourseItemDetailRequest
import com.cyxbs.pages.course.api.ICourseItemDetailService
import com.cyxbs.pages.widget.api.CourseWidgetAction

/**
 * Glance 课程详情的透明 Activity 容器。
 *
 * 本类只校验 Widget 参数并提供 Compose 生命周期；课程实时查询、重叠分页和业务内容均由
 * [ICourseItemDetailService] 提供，避免 Widget 依赖 course:view 或课程数据模型。
 */
class CourseWidgetEntryActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // 透明入口需要让桌面一直绘制到系统栏下方；图标明暗仍交由 ComponentActivity 按系统主题判断。
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // Android 10+ 默认可能给三键导航栏叠加对比度底色，透明弹窗中必须主动关闭。
      window.isNavigationBarContrastEnforced = false
    }
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
    val overlapItemIds = action.overlapItemIds.asSequence()
      .filter { it.isNotBlank() && it.length <= MAX_ID_LENGTH }
      .distinct()
      .take(MAX_OVERLAP_ITEM_COUNT)
      .toList()
    val request = CourseItemDetailRequest(
      week = action.week,
      itemId = itemId,
      overlapItemIds = overlapItemIds,
    )
    setContent {
      AppTheme {
        ICourseItemDetailService::class.impl().CourseItemDetailDialog(
          request = request,
          onDismiss = ::finish,
        )
      }
    }
  }

  private companion object {
    const val MAX_VALID_WEEK = 60
    const val MAX_ID_LENGTH = 256
    const val MAX_ACTION_JSON_LENGTH = 32 * 1024
    const val MAX_OVERLAP_ITEM_COUNT = 16
  }
}
