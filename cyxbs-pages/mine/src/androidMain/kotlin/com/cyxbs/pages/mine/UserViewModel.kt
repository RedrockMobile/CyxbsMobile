package com.cyxbs.pages.mine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.utils.extensions.setSchedulers
import com.cyxbs.components.utils.extensions.unsafeSubscribeBy
import com.cyxbs.pages.mine.network.model.ScoreStatus
import com.cyxbs.pages.mine.util.apiService
import com.mredrock.cyxbs.common.utils.extensions.doOnErrorWithDefaultErrorHandler
import com.mredrock.cyxbs.common.utils.extensions.mapOrThrowApiException
import com.mredrock.cyxbs.common.viewmodel.BaseViewModel


/**
 * Created by zia on 2018/8/26.
 */
class UserViewModel : BaseViewModel() {

    private val _status = MutableLiveData<ScoreStatus>()//签到状态
    val status: LiveData<ScoreStatus>
        get() = _status

    fun getScoreStatus() {
        apiService.getScoreStatus()
            .mapOrThrowApiException()
            .setSchedulers()
            .doOnErrorWithDefaultErrorHandler { true }
            .unsafeSubscribeBy(
                onNext = {
                    _status.postValue(it)
                }
            )
            .lifeCycle()
    }
}