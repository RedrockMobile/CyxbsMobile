package com.cyxbs.pages.qa.detail

/**
 * description ： 用于向各个界面同步点赞数据更新
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/20 21:11
 */
object LikeManager {
    private val listenerList = mutableListOf<LikeStateListener>()


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
    fun notifyLikeQuestion(id: Long){
        listenerList.forEach {
            it.onLikeQuestion(id)
        }
    }

    //通知不喜欢某个问题
    fun notifyUnLikeQuestion(id: Long){
        listenerList.forEach {
            it.onUnLikeQuestion(id)
        }
    }


    interface LikeStateListener{
        //当喜欢某个问题时回调
        fun onLikeQuestion(id: Long)

        //当取消喜欢某个问题时回调
        fun onUnLikeQuestion(id: Long)
    }
}
