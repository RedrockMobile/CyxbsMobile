package com.cyxbs.pages.food.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.ImageFromUrlCompose
import com.cyxbs.components.view.ui.ChooseDialogCompose
import com.cyxbs.components.view.ui.DialogPositiveBtnCompose
import com.cyxbs.pages.food.viewmodel.FoodViewModel
import cyxbsmobile.cyxbs_pages.food.generated.resources.Res
import cyxbsmobile.cyxbs_pages.food.generated.resources.food_ic_food_detail
import cyxbsmobile.cyxbs_pages.food.generated.resources.food_ic_food_detail_background
import cyxbsmobile.cyxbs_pages.food.generated.resources.food_ic_food_detail_praise
import org.jetbrains.compose.resources.painterResource

/**
 * description ： 美食咨询处的所有Dialog
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/11/1 23:31
 */

/**
 * 美食详情的Dialog
 */

@Composable
fun FoodDetailDialog() {
	val viewModel = viewModel(FoodViewModel::class)
	val showState = viewModel.showDetail
	val current = viewModel.current.value

	// 只有在要展示且数据不为 null 时才创建 Dialog
	if (!showState.value || current == null) return

	FoodDetailDialogCompose(
		showState = showState,
		positiveBtnText = if (current.praiseIs) "已点赞" else "点赞",
		positiveBtnBackgroundColor = if (current.praiseIs) Color(0xFFC3D4EE) else Color(0xFF5D5DF7),
		negativeBtnText = "取消",
		onDismissRequest = viewModel::closeDetail,
		onClickPositiveBtn = viewModel::doPraiseFood,
		onClickNegativeBtn = viewModel::closeDetail
	) {
		//美食图片
		Box(
			modifier = Modifier.align(Alignment.CenterHorizontally).size(193.dp, 125.dp)
				.clip(RoundedCornerShape(6.dp))
		) {
			ImageFromUrlCompose(
				url = current.picture,
				contentDescription = current.name,
				contentScale = ContentScale.FillBounds,
			)
			Row(
				Modifier.clip(RoundedCornerShape(bottomEnd = 6.dp)).background(
					brush = Brush.linearGradient(
						colors = listOf(
							Color(0xFF86C5FF),
							Color(0xFF9980FF)
						)
					)
				).padding(horizontal = 11.dp, vertical = 3.dp),
				horizontalArrangement = Arrangement.Center,
				verticalAlignment = Alignment.CenterVertically
			) {
				Image(
					modifier = Modifier.size(21.dp, 22.dp),
					painter = painterResource(Res.drawable.food_ic_food_detail_praise),
					contentDescription = null,
					contentScale = ContentScale.FillHeight
				)
				Spacer(Modifier.width(5.dp))
				Text(
					text = current.praiseNum.toString(),
					color = Color.White,
					fontSize = 14.sp,
					textAlign = TextAlign.Center
				)
			}
		}

		Spacer(Modifier.height(20.dp))
		Text(
			modifier = Modifier.align(Alignment.CenterHorizontally),
			textAlign = TextAlign.Center,
			text = current.name,
			fontSize = 18.sp,
			color = 0x9915315B.dark(
				0xFF5A5A5A
			)
		)
	}

}

@Composable
private fun FoodDetailDialogCompose(
	showState: State<Boolean>,
	modifier: Modifier = Modifier.width(255.dp).wrapContentHeight(),
	btnSize: DpSize = DpSize(93.dp, 34.dp),
	properties: DialogProperties = DialogProperties(),
	positiveBtnText: String = "点赞",
	positiveBtnBackgroundColor: Color = LocalAppColors.current.positive,
	negativeBtnText: String = "取消",
	onDismissRequest: () -> Unit = { },
	onClickPositiveBtn: () -> Unit = { },
	onClickNegativeBtn: () -> Unit = { },
	content: @Composable ColumnScope.() -> Unit
) {
	if (showState.value) {
		Dialog(
			properties = properties,
			onDismissRequest = onDismissRequest,
		) {
			Box(
				modifier = modifier.clip(RoundedCornerShape(8.dp))
					.paint(
						painter = painterResource(Res.drawable.food_ic_food_detail_background),
						contentScale = ContentScale.FillBounds
					),
			) {
				Column(
					modifier = Modifier.fillMaxWidth().padding(top = 35.dp, start = 31.dp, end = 31.dp),
				) {
					content()
					Spacer(Modifier.height(61.dp))
					FoodDetailDialogTwoButton(
						positiveBtnText = positiveBtnText,
						positiveBtnBackgroundColor = positiveBtnBackgroundColor,
						negativeBtnText = negativeBtnText,
						btnSize = btnSize,
						onClickPositiveBtn = onClickPositiveBtn,
						onClickNegativeBtn = onClickNegativeBtn
					)
				}
			}
		}
	}
}

@Composable
private fun FoodDetailDialogTwoButton(
	modifier: Modifier = Modifier.padding(bottom = 30.dp),
	positiveBtnText: String = "点赞",
	positiveBtnBackgroundColor: Color = LocalAppColors.current.positive,
	negativeBtnText: String = "取消",
	btnSize: DpSize = DpSize(80.dp, 34.dp),
	onClickPositiveBtn: () -> Unit = { },
	onClickNegativeBtn: () -> Unit = { },
) {
	Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
		FoodDetailDialogNegativeBtnCompose(
			negativeBtnText = negativeBtnText,
			modifier = Modifier.size(btnSize).border(
				width = 1.dp, color = Color(0x4A44E4),
				shape = MaterialTheme.shapes.large,
			).clickable(onClick = onClickNegativeBtn),
			textColor = Color(0xFF4A44E4)
		)

		Spacer(Modifier.width(15.dp))

		DialogPositiveBtnCompose(
			positiveBtnText = positiveBtnText,
			modifier = Modifier.size(btnSize).clickable(onClick = onClickPositiveBtn),
			backgroundColor = positiveBtnBackgroundColor,
		)
	}
}

@Composable
private fun FoodDetailDialogNegativeBtnCompose(
	modifier: Modifier = Modifier,
	negativeBtnText: String = "取消",
	textColor: Color = Color.White,
) {
	Box(
		modifier = modifier.clip(MaterialTheme.shapes.large).border(
			width = 1.dp,
			color = textColor,
			shape = MaterialTheme.shapes.large,
		),
		contentAlignment = Alignment.Center
	) {
		Text(text = negativeBtnText, color = textColor)
	}
}


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
	showSate: MutableState<Boolean>,
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