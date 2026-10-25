package com.cyxbs.pages.mine.edit

import kotlinx.coroutines.flow.SharedFlow

/**
 * 「资料编辑」页平台相关能力，由 Android / iOS 实现。
 *
 * Android 使用 Activity + UCrop，iOS 使用 FileKit 选图并转换为方形 JPEG。
 * 其它平台无实现时提示暂不支持。
 */
interface EditInfoPlatform {

  /**
   * 头像上传成功事件流，参数为服务器返回的 photo_src 远程地址。
   *
   * 使用 SharedFlow 而非回调，是为了避免 ViewModel / Composable 闭包被平台层
   * 静态字段长生命周期持有而导致泄漏：业务方在 viewModelScope 内 collect，
   * 离开页面时随作用域自动 cancel。
   */
  val avatarUpdatedEvents: SharedFlow<String>

  /**
   * 触发平台支持的选图、图片处理和上传流程。
   * 成功后会向 [avatarUpdatedEvents] 发射一个 photo_src URL。
   */
  fun editAvatar()
}
