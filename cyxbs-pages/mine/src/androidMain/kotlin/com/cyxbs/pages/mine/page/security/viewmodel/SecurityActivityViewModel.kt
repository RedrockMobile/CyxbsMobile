package com.cyxbs.pages.mine.page.security.viewmodel

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.mine.network.model.BindingResponse
import com.cyxbs.pages.mine.util.apiService
import com.mredrock.cyxbs.common.viewmodel.BaseViewModel
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import kotlinx.coroutines.launch

/**
 * Author: RayleighZ
 * Time: 2020-11-29 20:36
 */
class SecurityActivityViewModel : BaseViewModel() {
    private data class BindingState(
        val isBindingEmail: Boolean,
        val isSetProtect: Boolean
    )

    private var bindingState: BindingState? = null

    val netRequestSuccess: Boolean
        get() = bindingState != null

    val canClick: Boolean
        get() = bindingState != null

    val isSetProtect: Boolean
        get() = bindingState?.isSetProtect == true

    val isBindingEmail: Boolean
        get() = bindingState?.isBindingEmail == true

    fun checkBinding() {
        viewModelScope.launch {
            runCatchingCoroutine {
                apiService.checkBindingDirect(
                    IAccountService::class.impl().stuNum.orEmpty()
                ).data
            }.onSuccess {
                updateBindingState(it)
            }.onFailure {
                showCheckBindingError(it)
            }
//                .mapCatching {
//                it.data
//            }.getOrElse {
//                showCheckBindingError(it)
//                null
//            }?.let {
//                updateBindingState(it)
//            }
        }
    }

    private fun updateBindingState(bindingResponse: BindingResponse) {
        bindingState = BindingState(
            isBindingEmail = bindingResponse.email_is != 0,
            isSetProtect = bindingResponse.question_is != 0
        )
    }

    private fun showCheckBindingError(throwable: Throwable) {
        toast("对不起，获取是否绑定邮箱和密保失败，错误原因:$throwable")
    }

}
