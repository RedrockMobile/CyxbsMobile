package com.cyxbs.pages.qa.home.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.network.api
import com.cyxbs.components.utils.network.mapOrInterceptException
import com.cyxbs.pages.qa.home.model.bean.Item
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

    // 区分更新类型：true = 全量刷新，false = 局部刷新
    private val _isFullRefresh = MutableLiveData<Boolean>()
    val isFullRefresh: LiveData<Boolean> get() = _isFullRefresh


    /**
     * 获取Qa主页数据
     */
    fun getQaData(name: String) {
        _isFullRefresh.postValue(true) // 搜索触发 → 全量刷新
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

    /**
     * Qa点赞(网络请求)
     */
    fun getLike(id: Int, callback: (Boolean) -> Unit) {
        QaHomeApiService::class.api
            .getLike(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .safeSubscribeBy(
                onSuccess = { callback(true) },
                onError = { callback(false) }
            )
    }
    /**
     * Qa取消点赞(网络请求)
     */
    fun getUnlike(id: Int, callback: (Boolean) -> Unit) {
        QaHomeApiService::class.api
            .getUnlike(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .safeSubscribeBy(
                onSuccess = { callback(true) },
                onError = { callback(false) }
            )
    }
    /**
     * Qa本地点赞处理
     */
    fun likeItem(item: Item, onFail: () -> Unit) {
        _isFullRefresh.postValue(false) // 本地点赞 → 局部刷新
        getLike(item.ID) { success ->
            if (success) {
                val oldValue = _mutableQaData.value ?: return@getLike
                val newItems = oldValue.items.map {
                    if (it.ID == item.ID) item else it
                }
                val safeInfo = oldValue.info ?: ""
                _mutableQaData.postValue(oldValue.copy(items = newItems, info = safeInfo))
            } else {
                onFail()
            }
        }
    }
    /**
     * Qa本地取消点赞处理
     */
    fun unlikeItem(item: Item, onFail: () -> Unit) {
        _isFullRefresh.postValue(false) // 本地取消点赞 → 局部刷新
        getUnlike(item.ID) { success ->
            if (success) {
                val oldValue = _mutableQaData.value ?: return@getUnlike
                val newItems = oldValue.items.map {
                    if (it.ID == item.ID) item else it
                }
                val safeInfo = oldValue.info ?: ""
                _mutableQaData.postValue(oldValue.copy(items = newItems, info = safeInfo))
            } else {
                onFail()
            }
        }
    }


}