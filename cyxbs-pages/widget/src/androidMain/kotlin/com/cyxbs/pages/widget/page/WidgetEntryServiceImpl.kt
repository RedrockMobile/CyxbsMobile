package com.cyxbs.pages.widget.page

import com.cyxbs.pages.widget.api.IWidgetEntryService
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * Android 设置入口实现。
 *
 * 设置模块只调用公开接口；页面路由及其参数始终由 Widget 模块自己维护。
 */
@ImplProvider(clazz = IWidgetEntryService::class)
object WidgetEntryServiceImpl : IWidgetEntryService {

  override val isSettingEntryVisible: Boolean = true

  /** 进入 Widget 模块维护的小组件目录页。 */
  override fun navigateToWidgetPage() {
    CourseWidgetNavArgument.navigate()
  }
}
