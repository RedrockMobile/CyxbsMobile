package com.cyxbs.pages.food.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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

/**
 * description ： 美食咨询处的所有Dialog
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/11/1 23:31
 */

/**
 * 提示选择更多标签的Dialog
 */
@Composable
fun FoodSuggestMoreTagsDialog() {
	val viewModel = viewModel(FoodViewModel::class)

	FoodDialog(
		showSate = viewModel.showTips,
		onDismissRequest = viewModel::closeTips,
		onClickButton = viewModel::closeTips,
		content = "请选择标签"
	)
}

@Composable
fun FoodDescribeDialog() {
	val viewModel = viewModel(FoodViewModel::class)

	FoodDialog(
		showSate = viewModel.showDescribe,
		onDismissRequest = viewModel::closeDescribe,
		onClickButton = viewModel::closeDescribe,
		content = "美食咨询处的设置，一" +
				"是为了帮助各位选择综合症的邮子们更好的选择自己的需要的美食，对选择综合症说拜拜！二是为" +
				"了各位初来学校的新生学子更好的体验学校各处的美食！按照要求通过标签进行选择，" +
				"卷卷会帮助你选择最符合要求的美食哦！"
	)
}


@Composable
fun FoodWarnDialog() {
	val viewModel = viewModel(FoodViewModel::class)
	FoodDialog(
		showSate = viewModel.showWarn,
		onDismissRequest = viewModel::closeWarn,
		onClickButton = viewModel::closeWarn,
		content = "如果还没找到你喜欢的美食，可以尝试多选一些关键词哦!"
	)
}

//Food页统一风格的Dialog
@Composable
fun FoodDialog(
	showSate: State<Boolean>,
	onDismissRequest: () -> Unit,
	onClickButton: () -> Unit,
	content: String
) {
	ChooseDialogCompose(
		modifier = Modifier.width(255.dp).wrapContentHeight(),
		showState = showSate,
		btnSize = DpSize(130.dp, 37.dp),
		positiveBtnText = "确定",
		onDismissRequest = onDismissRequest,
		onClickPositiveBtn = onClickButton
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
				text = content,
				color = 0x9915315B.dark(0xFF5A5A5A),
				fontSize = 14.sp,
			)
		}
	}
}