package com.cyxbs.pages.mine.about.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cyxbs.components.config.compose.theme.LocalAppColors
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
        Dialog(
            onDismissRequest = { showState.value = false }
        ) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    .background(LocalAppColors.current.topBg).padding(20.dp)
            ) {
                Column {
                    Text(
                        modifier = Modifier.padding(top = 10.dp),
                        text = "${getAppVersionName()}版本信息",
                        color = LocalAppColors.current.tvLv2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15f.sp,
                        lineHeight = 27f.sp
                    )
                    Text(
                        modifier = Modifier.padding(top = 10.dp),
                        text = getAppUpdateContent(),
                        color = LocalAppColors.current.tvLv2,
                        fontSize = 15f.sp,
                        lineHeight = 27f.sp
                    )
                }
            }
        }
    }
}