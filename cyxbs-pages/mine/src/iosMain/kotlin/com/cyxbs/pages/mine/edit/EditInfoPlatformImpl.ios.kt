package com.cyxbs.pages.mine.edit

import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.extensions.mapCatchingCoroutine
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.pages.mine.edit.network.EditApiService
import com.g985892345.provider.api.annotation.ImplProvider
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import platform.UIKit.UIApplication
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UISceneActivationStateForegroundActive

@ImplProvider
object EditInfoPlatformImpl : EditInfoPlatform {
  private val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
  override val avatarUpdatedEvents = events.asSharedFlow()
  private var busy = false

  /** 在当前 session 的作用域中选择并上传头像，退出登录会取消任务，回写前复核账户身份。 */
  override fun editAvatar() {
    if (busy) return
    val account = IAccountService::class.impl()
    val session = account.session.value
    val studentNumber = session.accountId ?: return
    busy = true
    session.accountCoroutineScope.launch(Dispatchers.Main.immediate) {
      val file = runCatchingCoroutine {
        val window = UIApplication.sharedApplication.connectedScenes
          .filterIsInstance<UIWindowScene>()
          .filter { it.activationState == UISceneActivationStateForegroundActive }
          .flatMap { it.windows.filterIsInstance<UIWindow>() }
          .firstOrNull { it.isKeyWindow() }
          ?: UIApplication.sharedApplication.keyWindow
        var presenter = window?.rootViewController ?: error("无法打开相册")
        while (true) {
          presenter = presenter.presentedViewController ?: when (val current = presenter) {
            is UINavigationController -> current.visibleViewController
            is UITabBarController -> current.selectedViewController
            else -> null
          } ?: break
        }
        // 显式传入 presenter，兼容没有 Scene 生命周期的混合壳。
        FileKit.openFilePicker(
          type = FileKitType.Image,
          dialogSettings = FileKitDialogSettings(presenter = presenter),
        )
      }.onFailure {
        "无法打开或读取相册，请重试".toast()
        logg(it.message)
      }.getOrNull() ?: return@launch

      "正在上传头像...".toast()
      runCatchingCoroutine {
        val image = file.prepareAvatarJpeg()
        val body = MultiPartFormDataContent(formData {
          append("stunum", studentNumber)
          append("fold", image, Headers.build {
            append(HttpHeaders.ContentType, "image/jpeg")
            append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
          })
        })
        EditApiService::class.impl().uploadAvatar(body, session)
      }.mapCatchingCoroutine {
        it.throwApiExceptionIfFail()
        require(it.data.photoSrc.isNotBlank()) { "服务器未返回头像地址" }
        it.data
      }.onSuccess {
        if (account.session.value != session) return@launch
        events.emit(it.photoSrc)
        IAccountEditService::class.impl().refreshInfo()
        "头像更新成功".toast()
      }.onFailure {
        (if (it is IllegalArgumentException) it.message ?: "图片处理失败" else "上传头像失败，请重试").toast()
        logg(it.message)
      }
    }.invokeOnCompletion { busy = false }
  }
}
