package com.cyxbs.pages.qa.detail.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.network.api
import com.cyxbs.components.utils.network.mapOrInterceptException
import com.cyxbs.components.utils.network.throwOrInterceptException
import com.cyxbs.pages.qa.detail.bean.Data
import com.cyxbs.pages.qa.detail.network.DetailApiService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 *description:vm
 * author WYF
 * email 1206897770@qq.com
 * date 2025-8-16
 */
class QaDetailVm : BaseViewModel() {
    val detailLiveData: LiveData<Data>
        get() = _mutableDetailLiveData
    private var _mutableDetailLiveData = MutableLiveData<Data>()

    val errorLiveData: LiveData<Boolean>
        get() = _mutableErrorLiveData
    private var _mutableErrorLiveData = MutableLiveData<Boolean>()

    val likestatus: LiveData<Boolean>
        get() = _mutableLikeStatus
    private var _mutableLikeStatus = MutableLiveData<Boolean>()


    fun getQaDetail(id: Long) {
        DetailApiService::class.api
            .getDetail(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .mapOrInterceptException {
                _mutableErrorLiveData.postValue(true)
            }
            .safeSubscribeBy {
                _mutableErrorLiveData.postValue(false)
                _mutableDetailLiveData.postValue(it)
            }
    }

    fun putLike(id: Long) {
        DetailApiService::class.api
            .likeQuestion(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .throwOrInterceptException {
                _mutableLikeStatus.postValue(false)
            }
            .safeSubscribeBy {
                _mutableLikeStatus.postValue(true)
            }
    }

    fun cancelLike(id: Long) {
        DetailApiService::class.api
            .unLikeQuestion(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .throwOrInterceptException {
                _mutableLikeStatus.postValue(false)
            }
            .safeSubscribeBy {
                _mutableLikeStatus.postValue(true)
            }
    }
}