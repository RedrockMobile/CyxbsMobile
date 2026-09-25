package com.cyxbs.pages.course.api

import androidx.compose.runtime.Composable

/**
 * 从课表外部打开一组课程详情时使用的稳定身份。
 *
 * [itemId] 与 [overlapItemIds] 都是不透明业务标识，调用方只能原样传递；课程模块会在展示时重新查询
 * 最新数据，而不是信任入口缓存的标题、地点或时间。
 */
data class CourseItemDetailRequest(
  val week: Int,
  val itemId: String,
  val overlapItemIds: List<String> = emptyList(),
)

/**
 * 课程详情的外部展示入口。
 *
 * API 只约定请求和关闭语义；透明 Activity、桌面 Widget 等容器不依赖 course:view，也不感知课程、
 * 关联课程或日程的具体内容类型。
 */
interface ICourseItemDetailService {

  /**
   * 查询并展示 [request] 对应的最新详情。
   *
   * 数据不存在、账号已切换或弹窗正常收起时都会调用 [onDismiss]，容器应据此结束自身。
   */
  @Composable
  fun CourseItemDetailDialog(
    request: CourseItemDetailRequest,
    onDismiss: () -> Unit,
  )
}
