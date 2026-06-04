package com.cyxbs.pages.mine.edit.viewmodel

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountEditService
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.UserInfo
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.mine.edit.EditInfoPlatform
import com.cyxbs.pages.mine.edit.network.EditApiService
import com.cyxbs.pages.mine.user.bean.DownMessageBean
import com.cyxbs.pages.mine.user.bean.DownMessageParams
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * 资料编辑页 ViewModel
 *
 * - 初始字段值来自 [IAccountService.userInfo]
 * - 提交走 [EditApiService.updatePersonInfo]（PUT /magipoke/person/info，multipart）
 * - 头像 URL ([photoSrc]) 由平台层 EditInfoPlatform 上传图片后回写
 */
class EditInfoComposeViewModel : BaseViewModel() {

  private val accountService = IAccountService::class.impl()

  /** 用户信息（用于只读字段回显），随账户状态变化而更新 */
  @OptIn(ExperimentalCoroutinesApi::class)
  val userInfo: StateFlow<UserInfo?> = accountService.state
    .flatMapLatest { state ->
      (state as? AccountState.Login)?.userInfo ?: flowOf(null)
    }
    .stateIn(viewModelScope, SharingStarted.Lazily, accountService.userInfo)

  // 可编辑字段，TextFieldState 直接绑定 BasicTextField2
  val nicknameState = TextFieldState()
  val introductionState = TextFieldState()
  val phoneState = TextFieldState()
  val qqState = TextFieldState()

  // 头像 URL，由 EditInfoPlatform 上传成功后回写
  val photoSrc = mutableStateOf("")

  // 是否处于编辑态：默认 false 仅展示，点击 TopBar「编辑」后切换为 true
  val isEditing = mutableStateOf(false)

  // 保存中状态，用于禁用「保存」按钮
  val saving = mutableStateOf(false)

  // 头像使用协议的下发文案（懒加载，第一次点开问号时拉取）
  val portraitAgreement = mutableStateOf<List<DownMessageBean.DownMessageText>>(emptyList())
  val portraitAgreementLoading = mutableStateOf(false)

  init {
    // 进入页面时主动刷新一次用户信息（与「我的」主页一致）
    IAccountEditService::class.impl().refreshInfo()
    // 订阅 userInfo 流：刚启动会立刻收到一次本地缓存值；refreshInfo() / 其他模块刷新账户信息
    // 时也会带新数据进来。预览态下自动同步到输入框；编辑态下跳过，避免覆盖用户正在输入的内容。
    userInfo.collectLaunch { latest ->
      if (!isEditing.value) syncFieldsFromUserInfo(latest)
    }
    // 订阅头像上传结果（在 viewModelScope 内，页面销毁时自动 cancel，不会泄漏）
    EditInfoPlatform::class.implOrNull()?.avatarUpdatedEvents?.collectLaunch { newUrl ->
      // 后端 upload/avatar 接口内部已经把 photo_src 写回用户 info，不需要再调 updatePersonInfo。
      photoSrc.value = newUrl
    }
  }

  /** 将账户中心的 [UserInfo] 同步到本地输入框 / 头像 URL */
  private fun syncFieldsFromUserInfo(info: UserInfo?) {
    nicknameState.setTextAndPlaceCursorAtEnd(info?.nickname.orEmpty())
    introductionState.setTextAndPlaceCursorAtEnd(info?.introduction.orEmpty())
    phoneState.setTextAndPlaceCursorAtEnd(info?.phone.orEmpty())
    qqState.setTextAndPlaceCursorAtEnd(info?.qq.orEmpty())
    photoSrc.value = info?.photoSrc.orEmpty()
  }

  /** 进入编辑态 */
  fun enterEditing() {
    if (saving.value) return
    isEditing.value = true
  }

  /** 退出编辑态并把输入框重置为账户中心当前值（取消编辑） */
  fun cancelEditing() {
    if (saving.value) return
    syncFieldsFromUserInfo(accountService.userInfo)
    isEditing.value = false
  }

  /**
   * 拉取「头像使用协议」下发文案，第一次点开问号时调用。
   * 已有数据则跳过；失败仅 toast，不影响弹窗打开。
   */
  fun loadPortraitAgreement() {
    if (portraitAgreement.value.isNotEmpty() || portraitAgreementLoading.value) return
    launchByViewModelScope {
      portraitAgreementLoading.value = true
      runCatchingCoroutine {
        EditApiService::class.impl()
          .getDownMessage(DownMessageParams("zscy-portrait-agreement"))
      }.mapCatching {
        it.throwApiExceptionIfFail()
        it.data.textList
      }.onSuccess {
        portraitAgreement.value = it
      }
      portraitAgreementLoading.value = false
    }
  }

  /**
   * 提交修改。
   */
  fun save() {
    if (saving.value) return
    launchByViewModelScope {
      saving.value = true
      val body = MultiPartFormDataContent(
        formData {
          append("nickname", nicknameState.text.toString())
          append("introduction", introductionState.text.toString())
          append("phone", phoneState.text.toString())
          append("qq", qqState.text.toString())
        }
      )
      runCatchingCoroutine {
        EditApiService::class.impl().updatePersonInfo(body)
      }.mapCatching {
        it.throwApiExceptionIfFail()
        it
      }.onSuccess {
        toast("修改成功")
        IAccountEditService::class.impl().refreshInfo()
        isEditing.value = false
      }.onFailure {
        toast("修改失败")
      }
      saving.value = false
    }
  }
}
