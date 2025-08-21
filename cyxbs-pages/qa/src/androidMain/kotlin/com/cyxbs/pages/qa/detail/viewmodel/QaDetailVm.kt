package com.cyxbs.pages.qa.detail.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.network.api
import com.cyxbs.components.utils.network.mapOrInterceptException
import com.cyxbs.components.utils.network.throwOrInterceptException
import com.cyxbs.pages.qa.detail.LikeManager
import com.cyxbs.pages.qa.detail.bean.QuestionItem
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

    private var _id: Long = -1

    private val _questionData = MutableLiveData<QuestionItem>()
    val questionData: LiveData<QuestionItem> = _questionData


    //进入Activity时数据加载失败的error
    private val _searchError = MutableLiveData<Boolean>()
    val searchError: LiveData<Boolean> = _searchError

    //点赞失败的error
    private val _likeError = MutableLiveData<Boolean>()
    val likeError: LiveData<Boolean> = _likeError

    //是否正在进行进行网络访问
    private var isPerformRequest = false


    fun getQaDetail() {
        DetailApiService::class.api
            .getDetail(_id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .mapOrInterceptException {
                _searchError.postValue(true)
            }
            .safeSubscribeBy {
                _searchError.postValue(false)

                _questionData.postValue(it.item.copy(viewCount = it.item.viewCount + 1))
            }
    }

    fun putLike(id: Long, oldItem: QuestionItem) {
        DetailApiService::class.api
            .likeQuestion(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .throwOrInterceptException {
                //点赞失败回滚
                rollback(oldItem)
                isPerformRequest = false
            }
            .safeSubscribeBy {
                LikeManager.notifyLikeQuestion(id,LikeManager.SOURCE_DETAIL)
                isPerformRequest = false
            }
    }

    fun cancelLike(id: Long, oldItem: QuestionItem) {
        DetailApiService::class.api
            .unLikeQuestion(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .throwOrInterceptException {
                //取消失败，回滚状态
                rollback(oldItem)
                isPerformRequest = false
            }
            .safeSubscribeBy {
                LikeManager.notifyUnLikeQuestion(id,LikeManager.SOURCE_DETAIL)
                isPerformRequest = false
            }
    }

    /**
     * 点赞/取消点赞的入口
     * 点赞功能，当传入isLike = true时候调用取消点赞，当传入isLike=false时候取消点赞
     */
    fun toggleLikeQuestion() {
        val id = _id

        if (isPerformRequest) {
            return
        }
        val oldItem = _questionData.value ?: return

        //直接进行本地更新
        val updateItem = oldItem.copy(
            likeCount = if (!oldItem.isLike) oldItem.likeCount + 1 else oldItem.likeCount - 1,
            isLike = !oldItem.isLike
        )
        _questionData.postValue(updateItem)

        isPerformRequest = true
        if (oldItem.isLike) {
            cancelLike(id, oldItem)
        } else {
            putLike(id, oldItem)
        }
    }


    /**
     * 用于回滚点赞状态的函数
     */
    private fun rollback(oldItem: QuestionItem) {
        _questionData.postValue(oldItem)
        _likeError.postValue(true)
    }

    fun setId(id: Long) {
        _id = id
    }
}