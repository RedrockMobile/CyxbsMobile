package com.cyxbs.pages.volunteer.viewmodel

import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.utils.network.ApiGenerator
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.store.api.IStoreService
import com.cyxbs.pages.volunteer.bean.VolunteerTime
import com.cyxbs.pages.volunteer.network.ApiService
import com.mredrock.cyxbs.common.network.exception.RedrockApiIllegalStateException
import com.mredrock.cyxbs.common.utils.extensions.setSchedulers
import com.mredrock.cyxbs.common.utils.extensions.unsafeSubscribeBy
import com.mredrock.cyxbs.common.viewmodel.BaseViewModel
import com.mredrock.cyxbs.common.viewmodel.event.SingleLiveEvent

class DiscoverVolunteerFeedViewModel : BaseViewModel() {
    val volunteerData = MutableLiveData<VolunteerTime?>()
    var isQuerying: Boolean = false
    val loadFailed = SingleLiveEvent<Boolean?>()
    var isBind = false

    //是否用户主动退出
    var requestUnBind = false

    fun loadVolunteerTime() {
        isQuerying = true
        requestUnBind = false
        ApiGenerator.getApiService(ApiService::class.java).judgeBind()
                .flatMap {
                    if (it.code != 0) {
                        throw RedrockApiIllegalStateException()
                    }
                    ApiGenerator.getApiService(ApiService::class.java).getVolunteerRecord()
                }
                .setSchedulers()
                .doOnError {
                    loadFailed.value = true
                    isQuerying = false
                }
                .unsafeSubscribeBy {
                    isBind = true
                    volunteerData.value = it
                    isQuerying = false

                    // 登录了就更新积分商城的任务, 后端已做重复处理
                  IStoreService::class.impl()
                    .postTask(IStoreService.Task.LOGIN_VOLUNTEER, "")
                }.lifeCycle()
    }

    fun unbind() {
        volunteerData.value = null
        loadFailed.value = null
        isBind = false
        requestUnBind = true
    }
}