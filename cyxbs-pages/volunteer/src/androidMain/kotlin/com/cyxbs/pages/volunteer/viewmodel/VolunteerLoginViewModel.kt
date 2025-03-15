package com.cyxbs.pages.volunteer.viewmodel

import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.utils.network.ApiGenerator
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.store.api.IStoreService
import com.cyxbs.pages.volunteer.VolunteerLoginActivity
import com.cyxbs.pages.volunteer.bean.VolunteerTime
import com.cyxbs.pages.volunteer.network.ApiService
import com.mredrock.cyxbs.common.network.exception.RedrockApiException
import com.mredrock.cyxbs.common.utils.extensions.setSchedulers
import com.mredrock.cyxbs.common.utils.extensions.unsafeSubscribeBy
import com.mredrock.cyxbs.common.viewmodel.BaseViewModel

/**
 * Created by yyfbe, Date on 2020/9/3.
 */
class VolunteerLoginViewModel : BaseViewModel() {
    var loginCode = MutableLiveData<Int?>()
    var volunteerTime = MutableLiveData<VolunteerTime>()
    fun login(account: String, encodingPassword: String, onError: () -> Unit) {
        ApiGenerator.getApiService(ApiService::class.java)
                .volunteerLogin(account, encodingPassword)
                .flatMap {
                    loginCode.postValue(it.code)
                    if (it.code == VolunteerLoginActivity.BIND_SUCCESS) {
                        ApiGenerator.getApiService(ApiService::class.java)
                                .getVolunteerRecord()
                    } else {
                        throw RedrockApiException("response code not correct")
                    }

                }
                .setSchedulers()
                .unsafeSubscribeBy(
                        onNext = {
                            volunteerTime.value = it
                            // 登录了就更新积分商城的任务, 后端已做重复处理
                          IStoreService::class.impl()
                            .postTask(IStoreService.Task.LOGIN_VOLUNTEER, "")
                        },
                        onError = {
                            if (it !is RedrockApiException) {
                                onError()
                            }
                        }).lifeCycle()
    }
}