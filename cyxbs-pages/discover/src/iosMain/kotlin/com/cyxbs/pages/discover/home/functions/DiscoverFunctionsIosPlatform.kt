package com.cyxbs.pages.discover.home.functions

/**
 * 发现页"功能按钮区"在 iOS 端的跳转能力契约
 *
 * cmp 端 [DiscoverFunctions] 里部分 click 默认是 toast("该平台未实现")，iOS 端通过
 * KtProvider 拿到本接口实现，把它们接通到对应的原生 VC（对齐旧 FinderToolsView.push）。
 *
 * 关于 [jumpTodoMain] / [jumpSportDetail]：与
 * [com.cyxbs.pages.todo.service.TodoIosPlatform.jumpTodoMain] /
 * [com.cyxbs.pages.sport.service.SportIosPlatform.jumpSportDetail] 行为一致。
 * 因为 discover 模块只依赖 todo.api / sport.api 而不依赖各自的实现模块，无法跨模块
 * 复用那两个接口，所以这里重复声明；在 cyxbs-applications/multiplatform 的
 * IOSKmpInterfaceLink 中统一转发到 IOSKmpInterface 上已有的同名方法。
 */
interface DiscoverFunctionsIosPlatform {

  /** push 没课约（WeDateVC） */
  fun jumpWeDate()

  /** push 校历（CalendarViewController） */
  fun jumpSchoolCalendar()

  /** push 邮子清单主页（ToDoVC），与 TodoIosPlatform.jumpTodoMain 行为一致 */
  fun jumpTodoMain()

  /** push 体育打卡详情页（SportAttendanceViewController），与 SportIosPlatform.jumpSportDetail 行为一致 */
  fun jumpSportDetail()

  /** push 我的考试（TestArrangeViewController） */
  fun jumpTestArrange()
}
