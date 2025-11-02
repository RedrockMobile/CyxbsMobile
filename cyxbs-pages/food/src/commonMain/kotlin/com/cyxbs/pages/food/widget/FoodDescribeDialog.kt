package com.cyxbs.pages.food.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.view.ui.ChooseDialogCompose
import com.cyxbs.pages.food.viewmodel.FoodViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * description ： 描述 美食咨询处的Dialog
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/11/1 23:31
 */
@Composable
fun FoodDescribeDialog() {
	val viewModel = viewModel(FoodViewModel::class)
	ChooseDialogCompose(
		modifier = Modifier.width(255.dp).wrapContentHeight(),
		showState = viewModel.showDescribe,
		btnSize = DpSize(130.dp, 37.dp),
		positiveBtnText = "确定",
		onDismissRequest = viewModel::closeDescribe,
		onClickPositiveBtn = viewModel::closeDescribe
	) {
		Column(
			modifier = Modifier
				.padding(top = 28.dp, start = 18.dp, end = 18.dp, bottom = 38.dp),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Text(
				text = "温馨提示",
				color = LocalAppColors.current.tvLv3,
				fontSize = 18.sp
			)
			Spacer(modifier = Modifier.height(10.dp))
			Text(
				text = "美食咨询处的设置，一" +
						"是为了帮助各位选择综合症的邮子们更好的选择自己的需要的美食，对选择综合症说拜拜！二是为" +
						"了各位初来学校的新生学子更好的体验学校各处的美食！按照要求通过标签进行选择，" +
						"卷卷会帮助你选择最符合要求的美食哦！",
				color = 0x9915315B.dark(0xFF5A5A5A),
				fontSize = 14.sp,
			)
		}
	}
}

@Preview
@Composable
fun FoodDescribeDialogPreview() {
	FoodDescribeDialog()
}
