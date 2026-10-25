package com.cyxbs.applications.pro.sdks

import com.cyxbs.applications.pro.BuildConfig
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.BaseApp
import com.cyxbs.components.base.crash.CrashMonitor
import com.cyxbs.components.init.InitialService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appApplication
import com.cyxbs.components.init.appCoroutineScope
import com.g985892345.provider.api.annotation.ImplProvider
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.vasdolly.helper.ChannelReaderUtil
import kotlinx.coroutines.launch

/**
 * 在主进程取得隐私授权后初始化 Bugly，收集崩溃及用户会话，并同步登录身份。
 * 接入指南：https://bugly.qq.com/docs/user-guide/instruction-manual-android/
 * 官方文档讲的有点不详细，可以看看这篇文章：https://blog.csdn.net/RungBy/article/details/88794875
 *
 * @author ZhiQiang Tu
 * @time 2022/3/24  19:40
 * @signature 我将追寻并获取我想要的答案
 */
@ImplProvider(clazz = InitialService::class, name = "BuglyInitialService")
object BuglyInitialService : InitialService {
    
    /** 宿主会提供 Android ID 给 SDK，因此仅在取得隐私授权后的主线程回调中初始化。 */
    override fun onPrivacyAgreed() {
        init()
    }
    
    /** 初始化 SDK 后再同步账号，避免无 Context 的 setUserId 在初始化前被 SDK 忽略。 */
    private fun init() {
        val appContext = appApplication.applicationContext
        val deviceId = BaseApp.getAndroidID()
        
        if (BuildConfig.DEBUG) {
            CrashReport.setUserSceneTag(appContext, 83913)
        } else {
            CrashReport.setUserSceneTag(appContext, 202291)
        }
        
        // 隐私回调只在主进程分发；会话统计按 Activity 生命周期记录，不限制为每天一次。
        val strategy = CrashReport.UserStrategy(appContext).apply {
            deviceID = deviceId // 设备 id
            deviceModel = BaseApp.getDeviceModel() // 设备型号
            isUploadProcess = true // 增加上报进程
            appVersion = BuildConfig.VERSION_NAME
            appPackageName = BuildConfig.APPLICATION_ID
            appChannel = ChannelReaderUtil.getChannel(appApplication)
            setEnableUserInfo(true)
            setRecordUserInfoOnceADay(false)
        }

        //初始化bugly
        CrashReport.initCrashReport(appContext, BuildConfig.BUGLY_APP_ID, BuildConfig.DEBUG, strategy)

        // 账号可能在初始化后恢复、登录或切换；退出后使用设备匿名身份，避免沿用旧学号。
        val anonymousUserId = "anonymous:$deviceId"
        appCoroutineScope.launch {
            IAccountService::class.impl().stuNumFlow.collect { stuNum ->
                CrashReport.setUserId(stuNum?.takeIf(String::isNotBlank) ?: anonymousUserId)
            }
        }

        // 配置 module_crash 的异常上传
        CrashMonitor.crashReport = {
            CrashReport.postCatchedException(it)
        }
    }
}
