package com.cyxbs.pages.mine.about.service

import androidx.compose.runtime.MutableState

/**
 * @Desc : 关于我的页面多平台扩展的服务类
 * @Author : zzx
 * @Date : 2025/10/30 17:32
 */

// 使用方法: IAboutService::class.implOrNull("about")?.debugUpdateInfo()
interface IAboutService {

    // 点击用户协议
    fun clickUserAgreement()

    // 点击隐私政策
    fun clickPrivacyPolicy()

    // Debug包下长按测试更新文案
    fun debugUpdateInfo()

    // 绑定更新状态
    fun bingUpdate(state: MutableState<String>)

    // 点击检测更新状态
    fun clickUpdate()
}