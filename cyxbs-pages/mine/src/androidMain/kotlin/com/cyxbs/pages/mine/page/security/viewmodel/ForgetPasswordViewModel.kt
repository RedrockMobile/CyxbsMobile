package com.cyxbs.pages.mine.page.security.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.mredrock.cyxbs.common.viewmodel.BaseViewModel
import com.cyxbs.pages.mine.util.apiService
import kotlinx.coroutines.launch

/**
 *@Date 2020-11-03
 *@Time 22:11
 *@author SpreadWater
 *@description
 */
class ForgetPasswordViewModel : BaseViewModel() {
    var defaultPassword = MutableLiveData<Boolean>()

    //是否绑定邮箱
    var bindingEmail = MutableLiveData<Boolean>()

    //是否绑定密保
    var bindingPasswordProtect = MutableLiveData<Boolean>()

    //检查是否为默认密码
    fun checkDefaultPassword(stu_num: String, onError: () -> Unit) {
        viewModelScope.launch {
            runCatchingCoroutine {
                apiService.checkDefaultPasswordDirect(stu_num)
            }.onSuccess { response ->
                defaultPassword.value = response.status == 10000
            }.onFailure {
                toast(it.toString())
                onError()
            }
        }
    }

    //检查是否绑定信息
    fun checkBinding(stu_num: String, onSucceed: () -> Unit) {
        viewModelScope.launch {
            runCatchingCoroutine {
                apiService.checkBindingDirect(stu_num)
            }.getOrElse {
                toast(it.toString())
                null
            }?.let { response ->
                if (response.status == 10000) {
                    bindingEmail.value = response.data.email_is == 1
                    bindingPasswordProtect.value = response.data.question_is == 1
                    onSucceed()
                } else {
                    toast("检查绑定失败")
                }
            }
        }
    }
}
