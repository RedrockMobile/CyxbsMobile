package com.cyxbs.pages.mine.about.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.Platform
import com.cyxbs.components.config.appPlatform
import com.cyxbs.functions.update.api.AppUpdateStatus
import com.cyxbs.functions.update.api.IAppUpdateService
import com.cyxbs.components.utils.utils.get.getAppUpdateContent
import com.cyxbs.components.utils.utils.get.getAppVersionName

/**
 * @Desc : 展示更新信息的dialog
 * @Author : zzx
 * @Date : 2025/10/30 18:49
 */

@Composable
fun UpdateInfoDialog(showState: MutableState<Boolean>) {
    if (showState.value) {
        val title: String
        val content: String
        if (appPlatform == Platform.IOS) {
            val status by IAppUpdateService.getUpdateStatus().collectAsState()
            val info by IAppUpdateService.getUpdateInfo().collectAsState()
            val latestInfo = info
            title = "当前版本：${getAppVersionName()}"
            content = when (status) {
                AppUpdateStatus.Checking -> "正在获取版本信息..."
                is AppUpdateStatus.Result.Error -> "获取失败，请关闭后点击「版本更新」重试"
                else -> latestInfo?.let {
                    "App Store 最新版本：${it.versionName}\n\n${it.updateContent}"
                } ?: "暂无更新说明"
            }
        } else {
            title = "${getAppVersionName()}版本信息"
            content = getAppUpdateContent()
        }
        Dialog(
            onDismissRequest = { showState.value = false }
        ) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    .background(LocalAppColors.current.topBg).padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(
                        modifier = Modifier.padding(top = 10.dp),
                        text = title,
                        color = LocalAppColors.current.tvLv2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15f.sp,
                        lineHeight = 27f.sp
                    )
                    Text(
                        modifier = Modifier.padding(top = 10.dp),
                        text = content,
                        color = LocalAppColors.current.tvLv2,
                        fontSize = 15f.sp,
                        lineHeight = 27f.sp
                    )
                }
            }
        }
    }
}
