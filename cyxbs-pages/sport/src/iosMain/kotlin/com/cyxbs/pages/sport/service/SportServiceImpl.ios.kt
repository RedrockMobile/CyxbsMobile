package com.cyxbs.pages.sport.service

import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.utils.extensions.toast

internal actual fun jumpSportDetail() {
  // 实现由 cyxbs-applications/multiplatform 的 IOSKmpInterfaceLink 注入，
  // 最终落到 iosApp 的 KmpInterfaceImpl.jumpSportDetail()。
  // 实现缺失时降级为 toast，与其他平台跳转的兜底行为对齐，避免崩溃。
  SportIosPlatform::class.implOrNull()?.jumpSportDetail() ?: toast("暂不支持跳转")
}
