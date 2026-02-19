package com.cyxbs.pages.schoolcar.widget

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.pages.schoolcar.bean.CarLine
import com.cyxbs.pages.schoolcar.viewmodel.SchoolCarViewModel
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_bts_btn_change_no_select
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_bts_btn_change_select
import org.jetbrains.compose.resources.painterResource

/**
 * description ： 底部抽屉展示线路信息
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/19 10:09
 */
@Composable
fun CarInfoButtonSheet() {
	val viewModel = viewModel(SchoolCarViewModel::class)
	val selectedId by viewModel.selectedLineId
	val list by viewModel.lineSelectorItem
	Box {
		BottomSheetCompose(
			modifier = Modifier.navigationBarsPadding(),
			bottomSheetState = viewModel.bottomSheetState,
			peekHeight = 94.dp,
			dismissOnBackPress = false,
			dismissOnClickOutside = false,
			scrimColor = Color.Transparent
		) {
			ConstraintLayout(
				modifier = Modifier
					.then(bottomSheetDraggable())
					.shadow(
						elevation = 10.dp,
						shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
					)
					.background(LocalAppColors.current.topBg)
					.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
				animateChangesSpec = spring(
					stiffness = Spring.StiffnessMediumLow
				),
				constraintSet = createConstraintSet(viewModel.displayMode.value)
			) {
				ShapeTipCompose(modifier = Modifier.layoutId(CarInfoBtsElement.ShapeTip))
				LineSelectorCompose(
					modifier = Modifier.layoutId(layoutId = CarInfoBtsElement.LineSelector),
					list,
					selectedId,
					viewModel::toggleSelectLine
				)
				val mode = viewModel.displayMode.value
				when (mode) {
					is CarInfoBtsDisplayMode.Empty -> {}
					is CarInfoBtsDisplayMode.LineOverview -> {
						TittleCompose(
							mode.line.name, Modifier.layoutId(CarInfoBtsElement.LineTitle)
						)
						RuntimeCompose(mode.line.runTime, Modifier.layoutId(CarInfoBtsElement.LineRunTime))
						LineTypeCompose(
							sendType = mode.line.sendType,
							runType = mode.line.runType,
							modifier = Modifier.layoutId(CarInfoBtsElement.LineTypeTags)
						)
						RouteListCompose(
							modifier = Modifier.layoutId(CarInfoBtsElement.RouteList),
							siteId = -1,
							line = mode.line
						)
					}

					is CarInfoBtsDisplayMode.SiteOverView -> {
						TittleCompose(
							mode.site.name,
							modifier = Modifier.layoutId(CarInfoBtsElement.SiteName)
						)

						RouteListCompose(
							modifier = Modifier.layoutId(CarInfoBtsElement.SiteList),
							siteId = mode.site.id,
							line = mode.currentLine
						)

						LineChangeButtonCompose(
							modifier = Modifier.layoutId(CarInfoBtsElement.SwitchLineButton),
							avaibleLines = mode.availableLines,
							onClick = viewModel::toggleCurrentLine
						)
					}
				}
			}
		}
	}
	LaunchedEffect(selectedId) {
		viewModel.bottomSheetState.expand()
	}

}

@Composable
fun LineChangeButtonCompose(
	avaibleLines: List<CarLine>,
	modifier: Modifier = Modifier,
	onClick: (List<CarLine>) -> Unit
) {
	val selectable = avaibleLines.size > 1
	val backgroundColor = if (selectable) 0xFF2921D1.dark(0xFF2921D1) else 0xFFE8F0FC.dark(0xFFC3D4EE)
	Row(
		modifier = modifier.clip(RoundedCornerShape(16.dp)).background(backgroundColor)
			.clickable(selectable) { onClick(avaibleLines) }
	) {
		Image(
			painter = painterResource(if (selectable) Res.drawable.schoolcar_ic_bts_btn_change_select else Res.drawable.schoolcar_ic_bts_btn_change_no_select),
			contentDescription = null
		)
	}
}


@Composable
fun LineTypeCompose(sendType: String, runType: String, modifier: Modifier = Modifier) {
	Row(
		modifier,
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		TypeItemCompose(sendType, 0x1707BFE1.toLong().dark(0xFF1C2B2E), 0xFF07BFE1.dark(0xFF26BFE0))
		TypeItemCompose(runType, 0x17FF45B9.dark(0x17FF45B9), 0xFFFF45B9.dark(0xFFFF45B9))
	}
}

@Composable
fun TypeItemCompose(msg: String, backgroundColor: Color, textColor: Color) {
	Box(
		modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(backgroundColor)
			.padding(vertical = 2.dp, horizontal = 9.dp)
	) {
		Text(
			modifier = Modifier.align(Alignment.Center),
			text = msg,
			color = textColor,
			fontSize = 11.sp,
		)
	}
}


@Composable
fun RuntimeCompose(runtime: String, modifier: Modifier = Modifier) {
	Row(modifier) {
		Text(text = "运行时间: ${runtime}", color = 0xFF112C54.dark(0xFFF0F0F2), fontSize = 12.sp)
	}
}

@Composable
fun TittleCompose(text: String, modifier: Modifier = Modifier) {
	Text(
		modifier = modifier,
		text = text,
		fontSize = 22.sp,
		fontWeight = FontWeight.Bold,
		color = 0xFF112C54.dark(0xFFF0F0F2)
	)
}


@Composable
private fun ShapeTipCompose(modifier: Modifier = Modifier) {
	Box(
		modifier = modifier
			.width(36.dp)
			.height(5.dp)
			.background(
				color = 0xFFE2EDFB.dark(0xFF000000),
				shape = RoundedCornerShape(6.dp)
			)
	)
}

@Composable
private fun createConstraintSet(disPlayMode: CarInfoBtsDisplayMode): ConstraintSet {
	val windowSize = getWindowScreenSize()
	return ConstraintSet {
		CarInfoBtsConstraintSet(
			scope = this,
			windowSize = windowSize,
			displayMode = disPlayMode
		).createConstrain()
	}
}