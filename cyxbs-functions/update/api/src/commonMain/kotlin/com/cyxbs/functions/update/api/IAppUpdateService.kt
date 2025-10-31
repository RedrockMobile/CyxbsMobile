package com.cyxbs.functions.update.api

import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.utils.extensions.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Create By Hosigus at 2020/5/3
 */
interface IAppUpdateService {
    // 订阅更新状态
    fun getUpdateStatus(): StateFlow<AppUpdateStatus>
    // 检查更新
    suspend fun checkUpdate(): AppUpdateStatus.Result
    // 通知用户有更新
    fun noticeUpdate()

    /**
     * 尝试通知用户更新，内部有时间和状态判断
     * @param needFrequency 更新弹窗是否需要频控，防止多次出现
     */
    fun tryNoticeUpdate(needFrequency: Boolean = true)


    /**
     * 用于测试更新服务，发版前必测 ！！！
     * debug 包下长按「我的」-「关于我们」-「版本更新」触发
     */
    fun debug()

    companion object : IAppUpdateService by IAppUpdateService::class.implOrNull() ?: object : IAppUpdateService {

        private val stateFlow = MutableStateFlow(
            AppUpdateStatus.Result.Error(IllegalStateException("当前应用无更新功能！！！")))

        override fun getUpdateStatus(): StateFlow<AppUpdateStatus> {
            return stateFlow
        }

        override suspend fun checkUpdate(): AppUpdateStatus.Result {
            toast("当前应用无更新功能！！！")
            return stateFlow.value
        }

        override fun noticeUpdate() {
            toast("当前应用无更新功能！！！")
        }

        override fun tryNoticeUpdate(needFrequency: Boolean) {
            toast("当前应用无更新功能！！！")
        }

        override fun debug() {
            toast("当前应用无更新功能！！！")
        }
    }
}