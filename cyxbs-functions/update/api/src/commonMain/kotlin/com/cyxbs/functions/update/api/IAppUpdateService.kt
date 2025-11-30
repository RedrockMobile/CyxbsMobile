package com.cyxbs.functions.update.api

import com.cyxbs.components.config.service.impl
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
    fun noticeUpdate(newVersion: UpdateInfo)

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

    companion object : IAppUpdateService by IAppUpdateService::class.impl()
}