package com.cyxbs.pages.mine.edit

import android.content.Intent
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.components.utils.extensions.toast
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 「资料编辑」页 Android 平台实现：拉起 [AvatarPickerActivity] 完成
 * 拍照 / 相册 / UCrop 裁剪 / 上传到服务器全流程。
 *
 * 上传结果通过 [avatarUpdatedEvents] 暴露而非回调，避免业务方（ViewModel /
 * Composable）的闭包被本对象的静态字段长期持有导致泄漏；订阅端在 viewModelScope
 * 内 collect，退出页面随作用域 cancel。
 */
@ImplProvider
object EditInfoPlatformImpl : EditInfoPlatform {

  // extraBufferCapacity = 1：即便 collect 未及时挂起也不丢事件；replay = 0：
  // 不缓存历史，避免下次进入页面重复触发旧结果。
  private val _avatarUpdatedEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
  override val avatarUpdatedEvents: SharedFlow<String> = _avatarUpdatedEvents.asSharedFlow()

  override fun editAvatar() {
    val activity = appTopActivity.get() ?: run {
      toast("当前无法修改头像")
      return
    }
    activity.startActivity(Intent(activity, AvatarPickerActivity::class.java))
  }

  /**
   * 由 [AvatarPickerActivity] 在结果回来时调用。
   * 仅负责把 URL 转发给订阅方；后续"写回个人信息 + 刷新账户"由订阅方（ViewModel）统一处理。
   */
  internal fun consumeCallback(photoSrc: String?) {
    if (photoSrc != null) {
      _avatarUpdatedEvents.tryEmit(photoSrc)
    }
  }
}
