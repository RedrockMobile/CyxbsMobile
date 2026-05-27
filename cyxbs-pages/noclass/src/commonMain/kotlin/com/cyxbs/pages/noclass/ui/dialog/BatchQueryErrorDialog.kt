package com.cyxbs.pages.noclass.ui.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark

/**
 * description ： TODO:没课约批量添加查询错误弹窗
 * author : 我不抽火哪儿来的烟
 * email : 3114795332qq.com
 * date : 2026/5/24 10:30
 */

/* 查询错误弹窗  —  BatchQueryErrorDialog*/
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BatchQueryErrorDialog(
    errList: List<String>,
    onDismiss: () -> Unit,
) {
    val text = if (errList.size == 1) {
        "${errList[0]}信息有误，请重新输入"
    } else {
        "${errList[0]}等${errList.size}人信息有误，请重新输入"
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(238.dp).height(133.dp),
            shape = RoundedCornerShape(11.dp),
            color = Color.White.dark(Color(0xFF2D2D2D))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(25.dp))
                Text(
                    text,
                    color = Color(0xFF15315B).dark(Color(0xFFF0F0F2)),
                    fontSize = 15.sp
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier
                        .padding(bottom = 25.dp)
                        .width(92.dp)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = LocalAppColors.current.positive,
                    elevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "确定",
                            color = Color.White,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
}
