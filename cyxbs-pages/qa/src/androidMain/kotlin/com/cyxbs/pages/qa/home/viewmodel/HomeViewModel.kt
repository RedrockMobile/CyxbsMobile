package com.cyxbs.pages.qa.home.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.network.api
import com.cyxbs.pages.qa.home.model.QaHomePagingSource
import com.cyxbs.pages.qa.home.model.bean.Item
import com.cyxbs.pages.qa.home.model.bean.QaRequestData
import com.cyxbs.pages.qa.home.model.network.QaHomeApiService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * description ： QA主页的viewModel
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/15 16:42
 */

class HomeViewModel : BaseViewModel() {
    private val PAGE_SIZE = 10

    private val QADATABODY = QaRequestData(
        tags = "",
        page = 1,
        page_size = PAGE_SIZE
    )

    private val _mutableErrorLiveData = MutableLiveData<Boolean>()
    val errorLiveData: LiveData<Boolean> get() = _mutableErrorLiveData

    // 用 StateFlow 存缓存（Map<ID, Item>）
    private val _localCacheFlow = MutableStateFlow<Map<Int, Item>>(emptyMap())

    // 原始 PagingData
    private val originPagingDataFlow = Pager(
        config = PagingConfig(PAGE_SIZE),
        pagingSourceFactory = {
            QaHomePagingSource(QaHomeApiService::class.api, QADATABODY)
        }
    ).flow.cachedIn(viewModelScope)

    // 合并：每次 cacheFlow 变化时都会重新覆盖 PagingData
    val pagingDataFlow: Flow<PagingData<Item>> =
        combine(originPagingDataFlow, _localCacheFlow) { pagingData, cache ->
            pagingData.map { item ->
                cache[item.ID]?.copy() ?: item //  copy 确保 DiffUtil 能感知变化
            }
        }

    fun likeItem(item: Item, onFail: () -> Unit) {
        val updated = item.copy(
            is_like = true,
            like_count = item.like_count + 1,
            UpdatedAt = item.UpdatedAt ?: ""
        )
        _localCacheFlow.update { it + (item.ID to updated) }

        getLike(item.ID) { success ->
            if (!success) {
                // 回滚：也要 copy，否则 DiffUtil 可能不刷新
                _localCacheFlow.update { it + (item.ID to item.copy()) }
                onFail()
            }
        }
    }

    fun unlikeItem(item: Item, onFail: () -> Unit) {
        val updated = item.copy(
            is_like = false,
            like_count = item.like_count - 1,
            UpdatedAt = item.UpdatedAt ?: ""
        )
        _localCacheFlow.update { it + (item.ID to updated) }

        getUnlike(item.ID) { success ->
            if (!success) {
                _localCacheFlow.update { it + (item.ID to item.copy()) }
                onFail()
            }
        }
    }

    private fun getLike(id: Int, callback: (Boolean) -> Unit) {
        QaHomeApiService::class.api
            .getLike(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .safeSubscribeBy(
                onSuccess = { callback(true) },
                onError = { callback(false) }
            )
    }

    private fun getUnlike(id: Int, callback: (Boolean) -> Unit) {
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

