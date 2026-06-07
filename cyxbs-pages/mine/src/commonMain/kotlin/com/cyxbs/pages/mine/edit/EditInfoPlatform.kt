package com.cyxbs.pages.mine.edit

import kotlinx.coroutines.flow.SharedFlow

/**
 * 「资料编辑」页平台相关能力（commonMain 声明，androidMain 实现）。
 *
 * 头像选取依赖 Activity 上下文 + UCrop 裁剪，无法直接在 commonMain 完成，
 * 故抽到平台层。其它平台无实现时可优雅降级（toast 提示暂不支持）。
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
   * 触发「拍照 / 相册选择 → 裁剪 → 上传到服务器」流程。
   * 成功后会向 [avatarUpdatedEvents] 发射一个 photo_src URL。
   */
  fun editAvatar()
}
