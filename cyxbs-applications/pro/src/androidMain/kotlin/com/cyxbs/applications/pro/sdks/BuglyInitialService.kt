package com.cyxbs.applications.pro.sdks

import com.cyxbs.applications.pro.BuildConfig
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.BaseApp
import com.cyxbs.components.base.crash.CrashMonitor
import com.cyxbs.components.init.InitialService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.init.appApplication
import com.g985892345.provider.api.annotation.ImplProvider
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.vasdolly.helper.ChannelReaderUtil

/**
 * 配置的话可以先看看官方文档：https://bugly.qq.com/docs/
 * 官方文档讲的有点不详细，可以看看这篇文章：https://blog.csdn.net/RungBy/article/details/88794875
 *
 * @author ZhiQiang Tu
 * @time 2022/3/24  19:40
 * @signature 我将追寻并获取我想要的答案
 */
@ImplProvider(clazz = InitialService::class, name = "BuglyInitialService")
object BuglyInitialService : InitialService {
    
    // bugly 会读取 Android id
    override fun onPrivacyAgreed() {
        init()
    }
    
    private fun init() {
        val appContext = appApplication.applicationContext
        
        if (BuildConfig.DEBUG) {
            CrashReport.setUserSceneTag(appContext, 83913)
        } else {
            CrashReport.setUserSceneTag(appContext, 202291)
        }
        
        //设置上报进程
        val strategy = CrashReport.UserStrategy(appContext).apply {
            deviceID = BaseApp.getAndroidID() // 设备 id
            deviceModel = BaseApp.getDeviceModel() // 设备型号
            isUploadProcess = true // 增加上报进程
            appVersion = BuildConfig.VERSION_NAME
            appPackageName = BuildConfig.APPLICATION_ID
            appChannel = ChannelReaderUtil.getChannel(appApplication)
        }
        
        CrashReport.setUserId(
            IAccountService::class.impl().stuNum.orEmpty()
        )

        //初始化bugly
        CrashReport.initCrashReport(appContext, BuildConfig.BUGLY_APP_ID, BuildConfig.DEBUG, strategy)

        // 配置 module_crash 的异常上传
        CrashMonitor.crashReport = {
            CrashReport.postCatchedException(it)
        }
    }
}