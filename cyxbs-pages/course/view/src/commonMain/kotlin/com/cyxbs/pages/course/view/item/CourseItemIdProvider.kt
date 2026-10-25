package com.cyxbs.pages.course.view.item

/**
 * 为一个实际课表 Item 提供跨刷新稳定的不透明身份。
 *
 * ID 包含 Item 所在教学周；同一 Item 因重叠裁剪成多个可见区间时仍共享 [courseItemId]，但同一业务
 * 生成的不同 Item 必须使用不同 ID。该协议不依赖桌面小组件，可供 deeplink、搜索等入口复用。
 */
interface CourseItemIdProvider {
  val courseItemId: String
}
