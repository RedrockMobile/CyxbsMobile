package com.cyxbs.pages.schoolcar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.pages.schoolcar.viewmodel.SchoolCarViewModel

/**  
 * description ： 地图下载的Dialog
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/18 18:22
 */

@Composable
fun DownLoadProgressDialog() {
	val viewModel = viewModel(SchoolCarViewModel::class)
	DownLoadProgressDialogCompose(
		viewModel.downProgressDialogState.value,
		viewModel.downProgress.value,
		viewModel::closeDownLoadProgressDialog
	)
}

@Composable
fun DownLoadProgressDialogCompose(
	showState: Boolean,
	progress: Float,
	onDismissRequest: () -> Unit
) {
	if (showState) {
		Dialog(
			onDismissRequest = onDismissRequest
		) {
			Box(
				modifier = Modifier
					.width(314.dp)
					.clip(RoundedCornerShape(16.dp))
					.background(LocalAppColors.current.topBg)
			) {
				Column(
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Text(
						text = "下载地图",
						fontSize = 18.sp,
						color = LocalAppColors.current.tvLv4,
						modifier = Modifier.padding(top = 28.dp)
					)
					Text(
						text = "仅需初次载入时下载地图哦",
						fontSize = 16.sp,
						modifier = Modifier.padding(top = 17.dp),
						color = LocalAppColors.current.tvLv4
					)
					LinearProgressIndicator(
						progress = progress,
						modifier = Modifier.padding(top = 30.dp, start = 37.dp, end = 37.dp).fillMaxWidth()
							.height(10.dp),
						color = Color(0xFF4B44E5),
						backgroundColor = Color(0xFFE7EFFD),
						strokeCap = StrokeCap.Round
					)
					Text(
						text = "${(progress * 100).toInt()}%",
						fontSize = 16.sp,
						color = LocalAppColors.current.tvLv4,
						modifier = Modifier.padding(top = 20.dp, bottom = 28.dp)
					)
				}
			}
		}

	}
}