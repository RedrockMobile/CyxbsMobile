package com.cyxbs.pages.qa.home.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.network.api
import com.cyxbs.components.utils.network.mapOrInterceptException
import com.cyxbs.components.utils.network.throwOrInterceptException
import com.cyxbs.pages.qa.LikeManager
import com.cyxbs.pages.qa.home.model.bean.Item
import com.cyxbs.pages.qa.home.model.network.QaHomeApiService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * description ： QA主页数据的 ViewModel
 * 点赞/取消点赞使用统一入口 toggleLikeItem
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/16 20:29
 *
 * 这里点赞的机制就是点赞后在liveData里面找到对应的item,把它改了后
 * 把变化的item传给liveData，让观察者接受到变化的数据
 *
 * 由于PagingData不让改，而且还取不了val currentList = _items.value ?: return
 *这样的值，所以要单独做全量缓存和点赞刷新缓存
 */
class SearchViewModel : BaseViewModel() {

    // QA数据列表
    private val _items = MutableLiveData<List<Item>>()
    val items: LiveData<List<Item>> get() = _items

    //错误状态
    private val _error = MutableLiveData<Boolean>()
    val error: LiveData<Boolean> get() = _error

    //区分刷新类型：true = 全量刷新，false = 局部刷新
    private val _isFullRefresh = MutableLiveData<Boolean>()
    val isFullRefresh: LiveData<Boolean> get() = _isFullRefresh


    //点赞请求队列，避免重复请求
    private val likeInProgress = mutableSetOf<Int>()

    // LikeManager监听器
    private val likeStateListener = object : LikeManager.LikeStateListener {
        override fun onLikeQuestion(id: Long, source: Int) = updateLikeState(id, true, source)
        override fun onUnLikeQuestion(id: Long, source: Int) = updateLikeState(id, false, source)
    }


    init {
        // 注册监听器
        LikeManager.addLikeStateListener(likeStateListener)
    }

    /**
     * 更新 Like 状态（来自 LikeManager 回调）
     * @param id 需要更新的item对应的id
     * @param isLike 更新后的点赞状态
     */
    private fun updateLikeState(id: Long, isLike: Boolean, source: Int) {
        if (source == LikeManager.SOURCE_SEARCH) {
            //自己不要改自己
            return
        }
        val currentList = _items.value ?: return
        val index = currentList.indexOfFirst { it.ID.toLong() == id }
        if (index == -1) return
        val oldItem = currentList[index]
        val newItem = oldItem.copy(
            is_like = isLike,
            like_count = if (isLike) oldItem.like_count + 1 else oldItem.like_count - 1
        )
        _items.postValue(currentList.map { if (it.ID.toLong() == id) newItem else it })
    }

    /**
     * 获取QA数据
     * @param keyword 搜索关键字样
     */
    fun getQaData(keyword: String) {
        _isFullRefresh.postValue(true) // 标记为全量刷新
        QaHomeApiService::class.api
            .getSearch(keyword)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .mapOrInterceptException { _error.postValue(true) }
            .safeSubscribeBy { response ->
                _error.postValue(false)
                response.items?.let { _items.postValue(it) }

            }
    }

    /**
     * 点赞/取消点赞入口
     * @param id Item的ID
     * @param isLike 当前是否已点赞，true表示已点赞，需要取消；false表示未点赞，需要点赞
     */
    fun toggleLikeItem(id: Int, isLike: Boolean) {
        _isFullRefresh.postValue(false)
        val currentList = _items.value ?: return
        val index = currentList.indexOfFirst { it.ID == id }
        if (index < 0 || likeInProgress.contains(id)) {
            //如果在请求队列，则直接返回
            return
        }

        val oldItem = currentList[index]

        // 本地乐观更新
        val updatedItem = oldItem.copy(
            is_like = !oldItem.is_like,
            like_count = if (!oldItem.is_like) oldItem.like_count + 1 else oldItem.like_count - 1
        )
        _items.postValue(currentList.map { if (it.ID == id) updatedItem else it })


        likeInProgress.add(id)
        requestLikeChange(id, oldItem, !oldItem.is_like)
    }

    /**
     *网络请求--点赞状态的改变
     * @param id Item的id
     * @param oldItem 如果失败就回滚这个Item
     * @param isLike 点赞状态
     */
    private fun requestLikeChange(id: Int, oldItem: Item, isLike: Boolean) {
        val apiCall = if (isLike) {
            LikeManager.notifyLikeQuestion(id.toLong(), LikeManager.SOURCE_SEARCH)
            QaHomeApiService::class.api.getLike(id)
        } else {
            LikeManager.notifyUnLikeQuestion(id.toLong(), LikeManager.SOURCE_SEARCH)
            QaHomeApiService::class.api.getUnlike(id)
        }

        apiCall
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .throwOrInterceptException {
                likeInProgress.remove(id)
                rollback(oldItem)
            }
            .safeSubscribeBy {
                likeInProgress.remove(id)
            }
    }


    /**
     * 回滚单个Item
     * @param oldItem 对应回滚的item
     */
    private fun rollback(oldItem: Item) {
        val currentList = _items.value ?: return
        val index = currentList.indexOfFirst { it.ID == oldItem.ID }
        if (index == -1) return

        _items.postValue(currentList.toMutableList().apply { this[index] = oldItem })
    }

    override fun onCleared() {
        super.onCleared()
        LikeManager.removeLikeStateListener(likeStateListener)
    }
}
