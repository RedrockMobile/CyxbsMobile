package com.cyxbs.pages.course.view.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.pages.course.view.item.extension.CourseItemExtension
import com.cyxbs.pages.course.view.page.LocalCoursePageContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.DayOfWeek
import kotlin.reflect.KClass

/**
 * .
 *
 * @author 985892345
 * @date 2025/2/14
 */

val LocalCourseItemState = staticCompositionLocalOf<CourseItemState> { error("未初始化") }

// CourseItem 自身引用将作为 Compose 重组的 key()
// 更新后应保证等价的 item 前后属于同一个对象
abstract class CourseItem(
  val whatTime: CourseItemWhatTime, // item 的时间信息
  val coroutineScope: CoroutineScope, // item 所在的协程作用域
) {

  // item 支持的扩展功能
  val extensions = CourseItemExtensionContainer()

  // item 当前 Compose 中的状态
  lateinit var itemState: CourseItemState

  // item 所在的页面上下文
  val coursePage: LocalCoursePageContext
    get() = itemState.coursePageFlow.value!!

  /**
   * 绘制 item 内容，使用 [CourseDefaultItemContent]
   */
  @Composable
  abstract fun CourseItemContent()
}

// item 的时间信息
interface CourseItemWhatTime {
  val now: StateFlow<Fixed>

  val beginTime: MinuteTime
    get() = now.value.beginTime

  val finalTime: MinuteTime
    get() = now.value.finalTime

  data class Fixed(
    val page: Int, // 为 0 则表示整学期，否则表示第几周
    val dayOfWeek: DayOfWeek,
    val beginTime: MinuteTime,
    val finalTime: MinuteTime,
  )
}

// item 的扩展功能
class CourseItemExtensionContainer {

  private val extensions = mutableListOf<CourseItemExtension>()

  fun add(extension: CourseItemExtension) {
    extensions.add(extension)
  }

  fun <T : CourseItemExtension> get(clazz: KClass<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return extensions.find { clazz.isInstance(it) } as T?
  }
}
