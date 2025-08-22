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
import com.cyxbs.components.utils.network.throwOrInterceptException
import com.cyxbs.pages.qa.detail.LikeManager
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
 *
 * paging的点赞处理方法不同因为flow是流不能直接.value获取本次网络请求的数据
 *
 * 这里需要做一个本地缓存用来保存全部的网络请求数据
 * 并且还需要一个差量缓存
 *
 * 这里的差量缓存_localCacheFlow是MutableStateFlow
 *
 * StateFlow 是热流，每次它的值 .value 发生变化，都会立即触发收集它的 Flow。
 *
 * 其中combine 会监听 _localCacheFlow，任何一次更新都会被立即触发。
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

    // 用 StateFlow 存差量缓存(点赞)
    private val _localCacheFlow = MutableStateFlow<Map<Int, Item>>(emptyMap())

    /**
     * 全量缓存，保证 Detail 回调时能拿到任何 item
     *
     * 值得注意的是这里的全量缓存包含所有已经被 PagingData发射过的 Item，fullCache 不会包含未加载的页。
     *
     * 这里可能有个疑问为什么全量缓存不能替代ta，因为_localCacheFlow是热流
     * 数据变化时都会立即触发收集它的 Flow，然后被combine捕捉然后通知ui更新数据
     *
     * fullCache会经常更新数据，特别是在加载时
     */

    private val fullCache = mutableMapOf<Int, Item>()

    //点赞请求队列，避免重复请求
    private val likeInProgress = mutableSetOf<Int>()

    // 原始 PagingData
    private val originPagingDataFlow = Pager(
        config = PagingConfig(PAGE_SIZE),
        pagingSourceFactory = {
            QaHomePagingSource(QaHomeApiService::class.api, QADATABODY)
        }
    ).flow.cachedIn(viewModelScope)


    /**
     * 当 _localCacheFlow 更新时：
     * combine 会拿最新的 _localCacheFlow.value 和 最近一次的 PagingData 一起调用 lambda。
     *
     * 对 PagingData 中的每个 Item 执行合并逻辑（差量覆盖）。
     *
     * emit 一个新的 PagingData 流。
     *
     * 结果：UI 列表立即收到最新状态，无需重新拉取网络数据
     */
    val pagingDataFlow: Flow<PagingData<Item>> =
        combine(originPagingDataFlow, _localCacheFlow) { pagingData, cache ->
            pagingData.map { item ->
                val merged = cache[item.ID] ?: item
                // 同步更新 fullCache(这里拿到已加载页的全部数据)
                fullCache[item.ID] = merged
                merged
            }
        }

    // LikeManager监听器
    private val likeStateListener = object : LikeManager.LikeStateListener {
        override fun onLikeQuestion(id: Long,source:Int) {
            updateItemLikeState(id.toInt(), true)
        }

        override fun onUnLikeQuestion(id: Long,source:Int) {
            updateItemLikeState(id.toInt(), false)
        }
    }

    init {
        LikeManager.addLikeStateListener(likeStateListener)
    }

    /**
     * 统一更新本地状态（来自 Detail 或 Home 的点击）
     * @param id 对应需要更新item的id
     * @param isLike 更新后的点赞状态
     * */
    private fun updateItemLikeState(id: Int, isLike: Boolean) {
        _localCacheFlow.update { cache ->
            val oldItem = fullCache[id] ?: return@update cache
            val updated = oldItem.copy(
                is_like = isLike,
                like_count = if (isLike) oldItem.like_count + 1 else oldItem.like_count - 1
            )
            /*
            只在这里更新 fullCache 在高频点击下如果 `LikeManager` 回调很快、
            跨页面回来时，`fullCache` 的状态可能比 `_localCacheFlow` 的状态落后 1 步，导致主页丢一条数据。
             */
            fullCache[id] = updated
            cache + (id to updated)
        }
    }


    /**
     * 点赞或取消点赞入口
     * @param item 需要改变的item
     */
    fun toggleLike(item: Item) {
        val id = item.ID
        if (likeInProgress.contains(id)) return

        val oldItem = fullCache[id] ?: item
        val newItem = oldItem.copy(
            is_like = !oldItem.is_like,
            like_count = if (!oldItem.is_like) oldItem.like_count + 1 else oldItem.like_count - 1
        )

        // 乐观更新
        _localCacheFlow.update { cache ->
            fullCache[id] = newItem
            cache + (id to newItem)
        }

        likeInProgress.add(id)

        requestLikeChange(id, oldItem, !oldItem.is_like)
    }


    /**
     * 回滚单个 Item
     *
     * */
    private fun rollbackItem(item: Item) {
        _localCacheFlow.update { cache -> cache + (item.ID to item) }
        fullCache[item.ID] = item
    }

    /**
     * 网络请求点赞或取消点赞
     * @param id Item的id
     * @param oldItem 如果失败就回滚这个Item
     * @param isLike 点赞状态
     * */
    private fun requestLikeChange(
        id: Int,
        oldItem: Item,
        isLike: Boolean
    ) {
        val apiCall = if (isLike) {
            QaHomeApiService::class.api.getLike(id)
        } else {
            QaHomeApiService::class.api.getUnlike(id)
        }

        apiCall
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .throwOrInterceptException {
                likeInProgress.remove(id)
                rollbackItem(oldItem) // 回滚旧状态
            }
            .safeSubscribeBy {
                likeInProgress.remove(id)
            }
    }

    override fun onCleared() {
        super.onCleared()
        LikeManager.removeLikeStateListener(likeStateListener)
    }
}


