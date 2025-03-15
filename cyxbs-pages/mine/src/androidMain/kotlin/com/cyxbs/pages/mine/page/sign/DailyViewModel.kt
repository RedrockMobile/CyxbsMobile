package com.cyxbs.pages.mine.page.sign

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.mine.network.model.ScoreStatus
import com.cyxbs.pages.mine.util.apiService
import com.cyxbs.pages.mine.util.extension.normalWrapper
import com.cyxbs.pages.store.api.IStoreService
import com.mredrock.cyxbs.common.utils.extensions.unsafeSubscribeBy
import com.mredrock.cyxbs.common.viewmodel.BaseViewModel
import com.mredrock.cyxbs.common.viewmodel.event.SingleLiveEvent
import io.reactivex.rxjava3.functions.Function

/**
 * Created by zia on 2018/8/22.
 * 需要确保用户已登录
 */
class DailyViewModel : BaseViewModel() {

    //签到状态
    private val _status = MutableLiveData<ScoreStatus>()
    val status: LiveData<ScoreStatus>
        get() = _status

    //作为唯一置信源，用来弹出寒暑假不可签到的toast，同时为了解决LiveData粘性事件的限制，采用SingleLiveEvent
    private val _isInVacation = SingleLiveEvent<Boolean>()
    val isInVacation: LiveData<Boolean>
        get() = _isInVacation

    fun loadAllData() {
        apiService.getScoreStatus()
                .normalWrapper(this)
                .unsafeSubscribeBy(
                        onNext = {
                            _status.postValue(it)
                        },
                        onError = {
                            toast("获取积分失败")
                        }
                )
                .lifeCycle()
    }

    //用flatmap解决嵌套请求的问题
    fun checkIn() {
        apiService.checkIn()
                .flatMap(Function {
                    //如果status为405，说明是在寒暑假，此时不可签到
                    if (it.status == 405) {
                        _isInVacation.postValue(true)
                    }
                    return@Function apiService.getScoreStatus()
                })
                .normalWrapper(this)
                .unsafeSubscribeBy(
                        onNext = {
                            _status.postValue(it)
                          IStoreService::class.impl()
                            .postTask(IStoreService.Task.DAILY_SIGN, "")
                        },
                        onError = {
                            toast("签到失败")
                        }
                )
                .lifeCycle()
    }
}
