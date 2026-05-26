package com.cyxbs.components.account.api

import androidx.compose.runtime.Composable

/**
 * 登录 dialog 内容，非 dialog 形式，仅内容，子类使用 @ImplProvider 默认实现
 *
 * @author 985892345
 * @date 2026/5/25
 */
interface ILoginDialogContent {
  /**
   * @param function 当前功能名称，为 null 时默认为 “此功能”
   * @param clickLoginObserver 点击去登录按钮后的监听，跳转登录页逻辑已经由内部实现
   */
  @Composable
  fun Content(function: String? = null, clickLoginObserver: (() -> Unit)? = null)
}