package com.cyxbs.pages.qa.detail

/**
 * description ： 用于向各个界面同步点赞数据更新
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/20 21:11
 */
object LikeManager {
    private val listenerList = mutableListOf<LikeStateListener>()

    //用来判定点赞状态发生的源头，避免重复刷新
    const val SOURCE_DETAIL = 0
    const val SOURCE_SEARCH = 1
    const val SOURCE_PUBLISH = 2

    //添加监听
    fun addLikeStateListener(l: LikeStateListener) {
        if (!listenerList.contains(l)) {
            listenerList.add(l)
        }
    }

    //移除监听
    fun removeLikeStateListener(l: LikeStateListener) {
        listenerList.remove(l)
    }

    // 通知喜欢某个问题
    fun notifyLikeQuestion(id: Long,source:Int){
        listenerList.forEach {
            it.onLikeQuestion(id,source)
        }
    }

    //通知不喜欢某个问题
    fun notifyUnLikeQuestion(id: Long,source: Int){
        listenerList.forEach {
            it.onUnLikeQuestion(id,source)
        }
    }


    interface LikeStateListener{
        //当喜欢某个问题时回调
        fun onLikeQuestion(id: Long,source: Int)

        //当取消喜欢某个问题时回调
        fun onUnLikeQuestion(id: Long,source: Int)
    }
}
