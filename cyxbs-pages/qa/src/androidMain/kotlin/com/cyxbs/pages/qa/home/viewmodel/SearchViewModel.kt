package com.cyxbs.pages.qa.home.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.network.api
import com.cyxbs.components.utils.network.mapOrInterceptException
import com.cyxbs.pages.qa.home.model.bean.QaData
import com.cyxbs.pages.qa.home.model.network.QaHomeApiService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * description ： TODO:类的作用
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/16 20:29
 */
class SearchViewModel : BaseViewModel() {
    private val _mutableQaData = MutableLiveData<QaData>()
    val QaDataLiveData: LiveData<QaData>
        get() = _mutableQaData

    private val _mutableErrorLiveData = MutableLiveData<Boolean>()
    val errorLiveData: LiveData<Boolean>
        get() = _mutableErrorLiveData

    /**
     * 获取Qa主页数据
     */
    fun getQaData(name:String) {
        QaHomeApiService::class.api
            .getSearch(name)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .mapOrInterceptException {
                _mutableErrorLiveData.postValue(true)
            }
            .safeSubscribeBy {
                _mutableErrorLiveData.postValue(false)
                _mutableQaData.postValue(it)
            }
    }

}