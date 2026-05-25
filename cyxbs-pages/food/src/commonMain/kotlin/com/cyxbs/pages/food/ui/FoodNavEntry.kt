package com.cyxbs.pages.food.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_FOOD
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.components.utils.extensions.ImageFromUrlCompose
import com.cyxbs.pages.food.api.FoodNavArgument
import com.cyxbs.pages.food.viewmodel.FoodViewModel
import com.cyxbs.pages.food.widget.FoodDescribeDialog
import com.cyxbs.pages.food.widget.FoodDetailDialog
import com.cyxbs.pages.food.widget.FoodSuggestMoreTagsDialog
import com.cyxbs.pages.food.widget.FoodWarnDialog
import com.cyxbs.pages.food.widget.TagSelector
import cyxbsmobile.cyxbs_pages.food.generated.resources.Res
import cyxbsmobile.cyxbs_pages.food.generated.resources.food_ic_notification
import cyxbsmobile.cyxbs_pages.food.generated.resources.food_ic_toolbar_navigation
import org.jetbrains.compose.resources.painterResource

/**
 * description ： 美食咨询处界面
 * author : HI-IR
 * email : qq2420226433@outlook.comx`
 * date : 2025/10/29 23:58
 */
@AppNav(route = NAV_FOOD)
class FoodNavEntry : AppNavEntry<FoodNavArgument>() {

	override fun isNeedLogin(argument: FoodNavArgument): Boolean {
		return true
	}

	@Composable
	override fun Content(argument: FoodNavArgument) {
		viewModel { FoodViewModel() }
		FoodPage(argument)
	}
}

@Composable
fun FoodPage(argument: FoodNavArgument) {
	val scrollState = rememberScrollState()
	Column(
		Modifier.fillMaxSize().background(LocalAppColors.current.topBg)
			.systemBarsPadding(),
	) {
		TopbarCompose(Modifier.fillMaxWidth(), argument)
		ConstraintLayout(
			modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
			constraintSet = createConstraintSet()
		) {
			WelcomePictureCompose(Modifier.layoutId(FoodElement.WelcomePicture))
			DiningAreaCompose(Modifier.layoutId(FoodElement.DiningArea))
			DiningNumberCompose(Modifier.layoutId(FoodElement.DiningNumber))
			DiningPropertyCompose(Modifier.layoutId(FoodElement.DiningProperty))
			MealResultCompose(Modifier.layoutId(FoodElement.MealResult))
		}
	}
	FoodDescribeDialog()
	FoodSuggestMoreTagsDialog()
	FoodWarnDialog()
	FoodDetailDialog()
}

@Composable
private fun createConstraintSet(): ConstraintSet {
	val windowSize = getWindowScreenSize()
	return ConstraintSet {
		FoodConstraintSet(
			scope = this,
			windowSize = windowSize,
		).createConstrain()
	}
}


@Composable
private fun TopbarCompose(modifier: Modifier, argument: FoodNavArgument) {
	TopAppBar(
		modifier = modifier.height(65.dp)
			.clip(RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp)),
		backgroundColor = Color.Transparent,
		elevation = 0.dp
	) {
		Box(
			modifier = Modifier.fillMaxWidth()
		) {
			val viewModel = viewModel(FoodViewModel::class)
			Image(
				modifier = Modifier.padding(start = 16.dp).align(Alignment.CenterStart)
					.clickableNoIndicator {
						argument.popBackStack()
					},
				painter = painterResource(Res.drawable.food_ic_toolbar_navigation),
				contentDescription = "back",
				contentScale = ContentScale.Crop,
				colorFilter = ColorFilter.tint(LocalAppColors.current.tvLv3)
			)

			Text(
				modifier = Modifier.padding(start = 37.dp).align(Alignment.CenterStart),
				text = "美食咨询处",
				color = LocalAppColors.current.tvLv3,
				fontSize = 20.sp,
				textAlign = TextAlign.Center
			)

			Image(
				modifier = Modifier.padding(end = 15.dp).align(Alignment.CenterEnd)
					.clickableNoIndicator(onClick = viewModel::openDescribe),
				painter = painterResource(Res.drawable.food_ic_notification),
				contentDescription = "describe",
				contentScale = ContentScale.Crop,
			)
		}
	}
}

@Composable
private fun WelcomePictureCompose(modifier: Modifier = Modifier) {
	val viewModel = viewModel(FoodViewModel::class)
	ImageFromUrlCompose(
		url = viewModel.welcomePicture.value,
		modifier = modifier.width(410.dp),
		contentDescription = "welcome",
		contentScale = ContentScale.Fit,
	)
}

@Composable
private fun DiningAreaCompose(modifier: Modifier = Modifier) {
	val viewModel = viewModel(FoodViewModel::class)
	TagSelector(
		modifier = modifier,
		title = "就餐区域",
		subTitle = "可多选",
		tagList = viewModel.diningArea,
		onSelectChange = viewModel::toggleDiningAreaSelect
	)
}

@Composable
private fun DiningNumberCompose(modifier: Modifier = Modifier) {
	val viewModel = viewModel(FoodViewModel::class)
	TagSelector(
		modifier = modifier,
		title = "就餐人数",
		subTitle = "仅可选择一个",
		tagList = viewModel.diningNumber,
		onSelectChange = viewModel::toggleDiningNumberSelect
	)
}

@Composable
private fun DiningPropertyCompose(modifier: Modifier = Modifier) {
	val viewModel = viewModel(FoodViewModel::class)
	TagSelector(
		modifier = modifier,
		title = "餐饮特征",
		subTitle = "可多选",
		tagList = viewModel.diningProperty,
		onSelectChange = viewModel::toggleDiningPropertySelect,
		canRefresh = true,
		onRefresh = viewModel::refreshFoodProperty
	)
}

@Composable
fun MealResultCompose(modifier: Modifier = Modifier) {
	val viewModel = viewModel(FoodViewModel::class)
	Column(
		modifier.width(238.dp)
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(51.dp)
				.clip(RoundedCornerShape(8.dp))
				.background(color = 0xFFEFF4FF.dark(0xFF111111)),
			contentAlignment = Alignment.Center
		) {
			AnimatedContent(
				targetState = viewModel.current.value,
				transitionSpec = {
					slideInVertically(
						animationSpec = tween(durationMillis = 500)
					) togetherWith fadeOut()
				}
			) { current ->
				//在嵌套一层，contentAlignment 实际只影响 动画中两个内容的过渡对齐方式
				Box(
					modifier = Modifier.fillMaxSize(),
					contentAlignment = Alignment.Center
				) {
					Text(
						text = current?.name.orEmpty(),
						textAlign = TextAlign.Center,
						fontSize = 16.sp,
						color = 0xFF2F5085.dark(0xFF4A6A9E)
					)
				}
			}
		}



		Spacer(modifier = Modifier.height(18.dp))

		Row(
			modifier = Modifier.align(Alignment.CenterHorizontally),
			horizontalArrangement = Arrangement.spacedBy(14.dp)
		) {
			//这里需要根据有没有内容显示不同的效果
			if (viewModel.current.value == null) {
				//此时是未第一次点击的时候，查看详情还未显示出来
				Box(
					modifier = Modifier
						.size(80.dp, 40.dp)
						.clip(RoundedCornerShape(30.dp))
						.background(
							color = 0xFF5D5DF7.dark(0xFF4841E2)
						).clickableNoIndicator {
							viewModel.doRandomGenerated()
						}
				) {
					Text(
						modifier = Modifier.align(Alignment.Center),
						text = "随机生成",
						textAlign = TextAlign.Center,
						fontSize = 14.sp,
						color = Color.White
					)
				}

			} else {
				Box(
					modifier = Modifier.size(80.dp, 40.dp)
						.clip(RoundedCornerShape(30.dp))
						.background(0xFF5D5DF7.dark(0xFF4841E2))
						.clickableNoIndicator {
							viewModel.openDetail()
						}
				) {
					Text(
						text = "查看详情",
						modifier = Modifier.align(Alignment.Center),
						textAlign = TextAlign.Center,
						fontSize = 14.sp,
						color = Color.White
					)
				}

				Box(
					modifier = Modifier.size(80.dp, 40.dp)
						.border(
							width = 1.dp,
							color = 0xFF5D5DF7.dark(0xFF4841E2),
							shape = RoundedCornerShape(22.dp)
						)
						.clickableNoIndicator {
							viewModel.doChange()
						}

				) {
					Text(
						modifier = Modifier.align(Alignment.Center),
						textAlign = TextAlign.Center,
						text = "换一个",
						fontSize = 14.sp,
						color = Color(0xFF5C5CF6)
					)
				}
			}
		}
	}
}