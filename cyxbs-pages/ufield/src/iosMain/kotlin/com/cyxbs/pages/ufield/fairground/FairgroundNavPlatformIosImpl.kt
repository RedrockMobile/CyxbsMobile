package com.cyxbs.pages.ufield.fairground

import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.utils.extensions.toast
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * 邮乐园平台能力的 iOS 实现，供 commonMain 的 [FairgroundPage] 通过
 * `FairgroundNavPlatform::class.implOrNull()` 调用。
 *
 * 实际跳转动作下放到 [FairgroundIosPlatform]，由 cyxbs-applications/multiplatform 的
 * IOSKmpInterfaceLink 注入。实现缺失时降级为 toast，避免崩溃。
 */
@ImplProvider
object FairgroundNavPlatformIosImpl : FairgroundNavPlatform {

  override fun jumpQaEntry() {
    FairgroundIosPlatform::class.implOrNull()?.jumpQaEntry() ?: toast("暂不支持跳转")
  }

  override fun jumpUfieldMainEntry() {
    FairgroundIosPlatform::class.implOrNull()?.jumpUfieldMainEntry() ?: toast("暂不支持跳转")
  }
}
