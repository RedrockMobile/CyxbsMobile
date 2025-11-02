package com.cyxbs.pages.mine.about.service

/**
 * @Desc : 关于我的页面多平台扩展的服务类
 * @Author : zzx
 * @Date : 2025/10/30 17:32
 */

// 使用方法: IAboutService::class.implOrNull()?.debugUpdateInfo()
interface IAboutService {

    // 点击用户协议
    fun clickUserAgreement()

    // 点击隐私政策
    fun clickPrivacyPolicy()
}