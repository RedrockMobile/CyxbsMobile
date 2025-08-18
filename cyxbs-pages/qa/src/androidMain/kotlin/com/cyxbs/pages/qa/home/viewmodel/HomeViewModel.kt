package com.cyxbs.pages.qa.home.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.network.api
import com.cyxbs.pages.qa.home.model.QaHomePagingSource
import com.cyxbs.pages.qa.home.model.bean.Item
import com.cyxbs.pages.qa.home.model.bean.NewMessageCount
import com.cyxbs.pages.qa.home.model.bean.QaRequestData
import com.cyxbs.pages.qa.home.model.network.QaHomeApiService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.Flow

/**
 * description ： QA主页的viewModel
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/15 16:42
 */
class HomeViewModel : BaseViewModel() {
    private val PAGE_SIZE = 10

    //因接口要求这里传入一个Body
    private val QADATABODY = QaRequestData(
        tags = "",
        page = 1,
        page_size = 10
    )

    private val _mutableErrorLiveData = MutableLiveData<Boolean>()
    val errorLiveData: LiveData<Boolean>
        get() = _mutableErrorLiveData

    private val _mutableNewMessageLiveData = MutableLiveData<NewMessageCount>()
    val NewMessageLiveData: LiveData<NewMessageCount>
        get() = _mutableNewMessageLiveData


    /**
     * 获取Qa主页数据
     */
    fun getQaData(): Flow<PagingData<Item>> {
        return Pager(

            config = PagingConfig(PAGE_SIZE),
            pagingSourceFactory = {
                QaHomePagingSource(QaHomeApiService::class.api, QADATABODY)
            }

        ).flow.cachedIn(viewModelScope)

    }

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


}
