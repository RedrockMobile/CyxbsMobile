package com.cyxbs.pages.course.view.decoration

import androidx.compose.runtime.Composable
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemHierarchy
import com.cyxbs.pages.course.view.page.LocalCoursePage
import com.cyxbs.pages.course.view.page.LocalCoursePageContext

/**
 * 绘制在课表上的装饰物，同时也能拦截触摸事件
 *
 * @author 985892345
 * @date 2026/4/19
 */
abstract class CoursePageDecoration<Item: CourseItem> {

  private enum class AttachmentState {
    New,
    Attached,
    Detached,
  }

  private var attachmentState = AttachmentState.New
  private var decorationManager: CoursePageDecorationManager? = null

  /** 当前 Decoration 所属的课表 Frame，只能在 [onAttached] 及之后访问。 */
  protected val courseFrame
    get() = checkNotNull(decorationManager) { "CoursePageDecoration 当前未挂载" }.courseFrame

  /**
   * 当前 Manager 的数据作用域；Manager 被替换或 Frame 销毁时会自动取消。
   *
   * 该作用域没有 Compose 帧时钟，不得用于 UI 动画；动画应由当前 Composition 提供的协程执行。
   */
  protected val courseCoroutineScope
    get() = checkNotNull(decorationManager) { "CoursePageDecoration 当前未挂载" }.courseCoroutineScope

  val coursePage: LocalCoursePageContext
    @Composable
    get() = LocalCoursePage.current

  /**
   * 当前层级的 item 列表
   */
  val itemHierarchy: CourseItemHierarchy<Item> = CourseItemHierarchy()

  /**
   * 将 Decoration 挂到 Manager；每个实例只能挂载一次，数据订阅应在 [onAttached] 中启动。
   */
  internal fun attach(manager: CoursePageDecorationManager) {
    check(attachmentState == AttachmentState.New) { "CoursePageDecoration 只能挂载一次" }
    decorationManager = manager
    attachmentState = AttachmentState.Attached
    onAttached()
  }

  /**
   * 从指定 Manager 解除挂载；重复关闭 Manager 时不会重复触发 [onDetached]。
   */
  internal fun detach(manager: CoursePageDecorationManager) {
    if (attachmentState != AttachmentState.Attached) return
    check(decorationManager === manager) { "CoursePageDecoration 只能由所属 Manager 解除挂载" }
    try {
      onDetached()
    } finally {
      decorationManager = null
      attachmentState = AttachmentState.Detached
    }
  }

  /** Manager 完成层级绑定后的生命周期回调，子类可在这里启动唯一的数据订阅。 */
  protected open fun onAttached() = Unit

  /**
   * Manager 协程树取消后的生命周期回调。
   *
   * 子类应在这里同步注销监听器或释放其他非协程资源，不能再向 [courseCoroutineScope] 启动任务。
   */
  protected open fun onDetached() = Unit

  /**
   * 绘制在课表 scroll 内层
   *
   * 使用 [coursePage] 获取更多参数
   */
  @Composable
  open fun CoursePageContent() {
    itemHierarchy.CoursePageItemListContent()
  }
}
