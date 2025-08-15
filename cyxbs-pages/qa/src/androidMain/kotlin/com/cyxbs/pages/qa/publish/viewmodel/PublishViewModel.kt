package com.cyxbs.pages.qa.publish.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.network.api
import com.cyxbs.components.utils.network.mapOrInterceptException
import com.cyxbs.components.utils.network.throwOrInterceptException
import com.cyxbs.pages.qa.publish.network.PublishApiService
import com.cyxbs.pages.qa.publish.network.bean.request.PublishQuestionRequest
import com.cyxbs.pages.qa.publish.network.bean.response.SearchData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * description ： QA发布页的ViewModel
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/12 13:18
 */
class PublishViewModel : BaseViewModel() {

    //tag的list
    val dataTag = listOf("新生类", "生活类", "学习类", "其他")

    val _searchData: MutableLiveData<List<SearchData>> = MutableLiveData()
    val searchData: LiveData<List<SearchData>> = _searchData

    val _publishSuccess: MutableLiveData<Boolean> = MutableLiveData()
    val publishSuccess: LiveData<Boolean> = _publishSuccess

    //用于保存点赞状态
    val _likeResult: MutableLiveData<Boolean> = MutableLiveData()
    val likeResult: LiveData<Boolean> = _likeResult

    private val likeInProgress = mutableSetOf<Long>() // 正在请求的 id

    /**
     * 提出问题
     * 先search相关搜索项，如果没有已回答的问题相关搜索为空再提出问题
     */
    fun publishQuestion(q: String, tags: String) {
        PublishApiService::class.api
            .searchQuestion(q)
            .subscribeOn(Schedulers.io())
            .mapOrInterceptException {
                //发布失败
                _publishSuccess.postValue(false)
            }
            .flatMap { it ->
                val answeredQuestions = it.items?.filter { it.status == 2L }

                if (answeredQuestions.isNullOrEmpty()) {
                    // 没有已回答的相关问题，直接提问
                    PublishApiService::class.api
                        .publishQuestion(PublishQuestionRequest(q, tags))
                        .subscribeOn(Schedulers.io())
                        .mapOrInterceptException {
                            _publishSuccess.postValue(false)
                        }
                        .map { publishResp ->
                            // 发布成功，返回空列表占位
                            emptyList<SearchData>()
                        }
                } else {
                    // 搜索到了已回答的相关问题则直接返回已回答的问题
                    Single.just(answeredQuestions)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .safeSubscribeBy {
                if (it.isEmpty()) {
                    _publishSuccess.postValue(true)
                } else {
                    // 搜索有结果，更新 UI
                    _searchData.postValue(it)
                }
            }
    }

    //点赞
    fun likeQuestion(id: Long, oldItem: SearchData, onPartialUpdate: (Long) -> Unit) {
        PublishApiService::class.api
            .likeQuestion(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .throwOrInterceptException {
                _likeResult.postValue(false)
                likeInProgress.remove(id)
                rollback(id, oldItem, onPartialUpdate)
            }
            .safeSubscribeBy {
                _likeResult.postValue(true)
                likeInProgress.remove(id)
            }
    }

    /**
     * 取消点赞
     * @param id 就是id
     * @param oldItem 因为有回滚设计，所以这里最好传入一个变化前的item实例，提供回滚时的数据
     */
    fun unlikeQuestion(id: Long, oldItem: SearchData, onPartialUpdate: (Long) -> Unit) {
        PublishApiService::class.api
            .unLikeQuestion(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .throwOrInterceptException {
                likeInProgress.remove(id)
                _likeResult.postValue(false)
                rollback(id, oldItem, onPartialUpdate)
            }
            .safeSubscribeBy {
                likeInProgress.remove(id)
                _likeResult.postValue(true)
            }
    }


    /**
     * 点赞/取消点赞的入口
     * 点赞功能，当传入isLike = true时候调用取消点赞，当传入isLike=false时候取消点赞
     * @param onPartialUpdate 这是用于调用adapter的局部刷新的回调
     */
    fun toggleLikeQuestion(id: Long, isLike: Boolean, onPartialUpdate: (Long) -> Unit) {
        val currentList = _searchData.value ?: return
        val index = currentList.indexOfFirst { it.id == id }
        if (index < 0 || likeInProgress.contains(id)) {
            //如果在请求队列，则直接返回
            return
        }
        val oldItem = currentList[index]

        //先本地更新一下View
        val updatedItem = oldItem.copy(
            isLike = !oldItem.isLike,
            likeCount = if (!oldItem.isLike) oldItem.likeCount + 1 else oldItem.likeCount - 1
        )
        // 本地 UI 立即更新
        _searchData.value = currentList.toMutableList().apply { this[index] = updatedItem }
        onPartialUpdate(id)

        likeInProgress.add(id)

        if (isLike) {
            unlikeQuestion(id, oldItem, onPartialUpdate)
        } else {
            likeQuestion(id, oldItem, onPartialUpdate)
        }
    }

    /**
     * 用于回滚点赞状态的函数
     * @param id 需要回滚的item的id
     * @param onPartialUpdate 这是用于调用adapter的局部刷新的回调
     */
    private fun rollback(id: Long, oldItem: SearchData, onPartialUpdate: (Long) -> Unit) {
        val currentList = _searchData.value ?: return
        val index = currentList.indexOfFirst { it.id == id }
        if (index == -1) return

        _searchData.value = currentList.toMutableList().apply { this[index] = oldItem }
        onPartialUpdate(id)
    }

}