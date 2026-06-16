package com.cyxbs.pages.todo.viewmodel

import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.pages.todo.service.TodoIosPlatform

/**
 * 邮子清单 feed ViewModel 在 iOS 的占位实现。
 *
 * todo 的数据层（iOS 端 TodoSyncTool / Android 端 Room + 网络 + 小组件）形态差异大，
 * 不在本次主页 cmp 迁移范围内统一。本类仅把"点击整张卡片"接通到 iOS 原生 ToDoVC，
 * 让用户能从 feed 进入完整邮子清单页；其它三个方法显式 override 留空，等 todo 模块
 * 整体 cmp 化后再统一对接。
 *
 * @author 985892345
 */
actual class TodoFeedViewModel actual constructor() : CommonTodoFeedViewModel() {

  override fun onCardClick() {
    // 实现由 cyxbs-applications/multiplatform 的 IOSKmpInterfaceLink 注入，
    // 最终落到 iosApp 的 KmpInterfaceImpl.jumpTodoMain()。
    // 实现缺失时降级为 toast，避免点击无响应。
    TodoIosPlatform::class.implOrNull()?.jumpTodoMain() ?: toast("暂不支持跳转")
  }

  // 以下三个方法显式 override 留空，避免后续被误读成"漏写"：
  // iOS 端原生数据源（TodoSyncTool）暂未接入 KMP，feed 列表保持基类 Empty 默认状态。
  // 等 todo 模块整体 cmp 化后再对齐 Android 实现（参见 TodoFeedViewModel.android.kt）。

  override fun refresh() {
    // no-op
  }

  override fun onItemClick(id: Long) {
    // no-op：feed 列表恒为空，UI 上理论上不会触发到这里。
  }

  override fun onItemCheck(id: Long) {
    // no-op：iOS 旧版 ToDoFinderVC 主页上也没有"勾选完成"能力，
    // 这条 Android 引入的新能力是否带到 iOS 留到 cmp 化后再做产品决策。
  }
}
