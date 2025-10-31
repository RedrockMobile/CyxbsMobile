package com.cyxbs.functions.update

import android.content.Intent
import androidx.core.net.toUri
import com.afollestad.materialdialogs.MaterialDialog
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.functions.update.api.AppUpdateStatus
import com.cyxbs.functions.update.api.IAppUpdateService
import com.cyxbs.functions.update.bean.UpdateInfo
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Create By Hosigus at 2020/5/2
 */
@ImplProvider
internal object AppUpdateService : IAppUpdateService {
    override fun getUpdateStatus(): StateFlow<AppUpdateStatus> {
        return AppUpdateModel.status
    }

    override suspend fun checkUpdate(): AppUpdateStatus.Result {
        return AppUpdateModel.checkUpdate()
    }

    override fun noticeUpdate() {
        val info = AppUpdateModel.updateInfo ?: return
        noticeUpdateInternal(info)
    }
    
    private fun noticeUpdateInternal(info: UpdateInfo) {
        val activity = appTopActivity.get() ?: return
        MaterialDialog(activity).show {
            title(text = "有新版本更新")
            message(text = "最新版本:" + info.versionName + "\n\n" + info.updateContent + "\n\n点击点击，现在就更新一发吧~")
            positiveButton(text = "下载最新安装包") {
                val uri = info.apkUrl.toUri()
                /*
                * 22-8-30
                * 因为应用内更新有很多毛病，所以采用浏览器下载
                * */
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                )
            }
            negativeButton(text = "下次吧") {
                dismiss()
            }
            cornerRadius(16F)
        }
        if (isDebug() && AppUpdateModel.mockDated) {
            toast("⚠️注意：当前处于测试更新弹窗状态中")
        }
    }
    
    override fun tryNoticeUpdate(needFrequency: Boolean) {
        appCoroutineScope.launch {
            val status = checkUpdate()
            if (status == AppUpdateStatus.Result.Dated) {
                val nowTime = System.currentTimeMillis()
                if (!needFrequency) {
                    noticeUpdate()
                    defaultSettings.putLong("上次提醒更新时间", nowTime)
                    return@launch
                }
                val lastTime = defaultSettings.getLong("上次提醒更新时间", 0L)
                val diff = TimeUnit.HOURS.convert(nowTime - lastTime, TimeUnit.MILLISECONDS)
                if (diff >= 12) {
                    // 如果有更新，则每隔 12 个小时提醒一次更新
                    noticeUpdate()
                    defaultSettings.putLong("上次提醒更新时间", nowTime)
                }
            }
        }
    }

    override fun debug() {
        if (!isDebug()) return
        AppUpdateModel.mockDated = true
        tryNoticeUpdate(needFrequency = false)
    }
}