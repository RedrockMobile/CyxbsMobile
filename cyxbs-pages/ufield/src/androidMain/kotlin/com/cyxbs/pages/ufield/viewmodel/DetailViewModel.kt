package com.cyxbs.pages.ufield.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.components.utils.network.api
import com.cyxbs.components.utils.network.mapOrInterceptException
import com.cyxbs.pages.ufield.bean.ActivityBean
import com.cyxbs.pages.ufield.network.ActivityDetailApiService
import com.cyxbs.pages.schedule.api.IScheduleOccurrenceService
import com.cyxbs.pages.schedule.api.ScheduleExternalCategory
import com.cyxbs.pages.schedule.api.ScheduleExternalCreateRequest
import com.cyxbs.pages.schedule.api.ScheduleExternalCreateResult
import com.cyxbs.pages.schedule.api.ScheduleExternalSource
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceKind
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceTiming
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant


/**
 *
 * author : 苟云东
 * email : 2191288460@qq.com
 * date : 2023/8/24 10:49
 */
class DetailViewModel(id: Int) : BaseViewModel() {
    private val _detailData = MutableLiveData<ActivityBean>()
    val detailData: LiveData<ActivityBean>
        get() = _detailData
    private val _wantToSee = MutableLiveData<Boolean>()

    val wantToSee: LiveData<Boolean>
        get() = _wantToSee
    private val _isAdd = MutableLiveData<Boolean>()
    val isAdd: LiveData<Boolean>
        get() = _isAdd

    init {
        getActivityData(id)
    }

    private fun getActivityData(id: Int) {
        ActivityDetailApiService::class.api
            .getActivityData(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .mapOrInterceptException {
                toast("请求失败")
            }
            .safeSubscribeBy {
                _detailData.postValue(it)
            }

    }

    fun wantToSee(id: Int) {
        ActivityDetailApiService::class.api
            .wantToSee(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError {
                _wantToSee.postValue(false)
            }
            .safeSubscribeBy {
                _wantToSee.postValue(true)
            }
    }

    /**
     * 将活动保存为 Schedule 原生清单，并在本地保存成功后回写活动中心的“已添加”标记。
     *
     * 默认在活动开始前 10 分钟提醒。回写接口失败不会撤销已经保存的日程；稳定来源 ID 会阻止用户
     * 再次点击时产生重复清单。
     */
    fun addActivityToSchedule(activity: ActivityBean) {
        launchByViewModelScope {
            val start = Instant.fromEpochSeconds(activity.activityStartAt)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .toMinuteTimeDate()
            val result = runCatching {
                IScheduleOccurrenceService::class.impl().createExternalSchedule(
                    ScheduleExternalCreateRequest(
                        source = ScheduleExternalSource.UFIELD_ACTIVITY,
                        sourceId = activity.activityId.toString(),
                        title = activity.activityTitle,
                        description = activity.activityPlace,
                        timing = ScheduleOccurrenceTiming.Deadline(
                            due = start,
                            timeZoneId = TimeZone.currentSystemDefault().id,
                        ),
                        reminderOffsetMinutes = 10,
                        kind = ScheduleOccurrenceKind.TODO,
                        isInTodoList = true,
                        linkedToCourse = false,
                        category = ScheduleExternalCategory.OTHER,
                    ),
                )
            }.getOrNull()
            if (result is ScheduleExternalCreateResult.Success) {
                _isAdd.postValue(true)
                markActivityAdded(activity.activityId)
            } else {
                _isAdd.postValue(false)
            }
        }
    }

    /** 回写活动中心自身的展示状态，不参与日程本地事务。 */
    private fun markActivityAdded(id: Int) {
        ActivityDetailApiService::class.api
            .isAdd(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .mapOrInterceptException {  }
            .safeSubscribeBy { }
    }

}
