package com.cyxbs.pages.mine.page.security.viewmodel

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.mine.util.apiService
import com.mredrock.cyxbs.common.utils.extensions.doOnErrorWithDefaultErrorHandler
import com.mredrock.cyxbs.common.utils.extensions.setSchedulers
import com.mredrock.cyxbs.common.utils.extensions.unsafeSubscribeBy
import com.mredrock.cyxbs.common.viewmodel.BaseViewModel

/**
 * Author: RayleighZ
 * Time: 2020-11-29 20:36
 */
class SecurityActivityViewModel : BaseViewModel() {
    var netRequestSuccess = false
    var canClick = false
    var isSetProtect = false
    var isBindingEmail = false

    fun checkBinding(onSuccess: () -> Unit) {
        apiService.checkBinding(
                IAccountService::class.impl().stuNum.orEmpty()
        )
                .setSchedulers()
                .doOnErrorWithDefaultErrorHandler {
                    toast("对不起，获取是否绑定邮箱和密保失败，错误原因:$it")
                    true
                }
                .unsafeSubscribeBy {
                    val bindingResponse = it.data
                    isBindingEmail = bindingResponse.email_is != 0
                    isSetProtect = bindingResponse.question_is != 0
                    canClick = true
                    netRequestSuccess = true
                    onSuccess()
                }.lifeCycle()
    }
}