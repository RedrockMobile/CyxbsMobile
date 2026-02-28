package com.cyxbs.pages.schoolcar.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
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
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.components.utils.extensions.log
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.schoolcar.bean.CarLine
import com.cyxbs.pages.schoolcar.viewmodel.CommonSchoolCarViewModel
import com.cyxbs.pages.schoolcar.widget.CarInfoBtsDisplayMode.Empty
import com.cyxbs.pages.schoolcar.widget.CarInfoBtsDisplayMode.ErrorOverView
import com.cyxbs.pages.schoolcar.widget.CarInfoBtsDisplayMode.LineOverview
import com.cyxbs.pages.schoolcar.widget.CarInfoBtsDisplayMode.SiteOverView
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_bts_btn_change_no_select
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_bts_btn_change_select
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.compose.resources.painterResource

/**
 * description ： 底部抽屉展示线路信息
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/19 10:09
 */
@Composable
fun CarInfoButtonSheet(
	state: CarInfoBtsState,
	toggleSelectLine: (LineSelectorItem) -> Unit,
	toggleCurrentLine: (List<CarLine>) -> Unit,
	onSelectClosedSite: () -> Unit
) {
	val selectedId by state.selectedLineId
	val peekHeight = state.peekHeight
	val list by state.lineSelectorItem
	val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
		val realPeekHeight = peekHeight + navBarHeight
	BottomSheetCompose(
		bottomSheetState = state.bottomSheetState,
		peekHeight = realPeekHeight,
		dismissOnBackPress = false,
		dismissOnClickOutside = false,
		scrimColor = Color.Transparent
	) {
		ConstraintLayout(
			modifier = Modifier
				.then(bottomSheetDraggable())
				.heightIn(min = realPeekHeight + 2.dp)
				.shadow(
					elevation = 10.dp,
					shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
				)
				.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
				.background(LocalAppColors.current.topBg)
				.navigationBarsPadding(),
			constraintSet = createConstraintSet(state.displayMode.value)
		) {
			ShapeTipCompose(modifier = Modifier.layoutId(CarInfoBtsElement.ShapeTip))
			LineSelectorCompose(
				modifier = Modifier.layoutId(layoutId = CarInfoBtsElement.LineSelector),
				list,
				selectedId,
				toggleSelectLine
			)
			when (val mode = state.displayMode.value) {
				is ErrorOverView -> {
					ErrorInfoCompose(Modifier.layoutId(CarInfoBtsElement.ErrorInfo))
				}

				is LineOverview -> {
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

				is SiteOverView -> {
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
						lineName = mode.currentLine.name,
						availableLines = mode.availableLines,
						onClick = toggleCurrentLine
					)
				}

				is Empty -> {}
			}
		}
	}
	// 选择线路/选择站点的时候自动展开
	LaunchedEffect(state.displayMode.value){
		try {
			delay(64)
			state.isStateChanging.value = true
			when (state.displayMode.value) {
				is Empty -> {
					state.bottomSheetState.collapse()
				}

				else -> {
					state.bottomSheetState.expand()
				}
			}
		} finally {
			state.isStateChanging.value = false
		}
	}

	// 监听当用户处于Empty模式的时候上滑选择最近的站点
	LaunchedEffect(Unit) {
		state.bottomSheetState.stateFlow.collect {
			if (it == BottomSheetValueState.Scrolling &&
				state.displayMode.value is Empty &&
				!state.isStateChanging.value
			) {
				onSelectClosedSite()
			}
		}
	}

}

@Composable
fun ErrorInfoCompose(modifier: Modifier = Modifier) {
	Text(
		modifier = modifier,
		text = CommonSchoolCarViewModel.NETWORK_ERROR_INFO,
		fontSize = 22.sp,
		fontWeight = FontWeight.Bold,
		color = 0xFF112C54.dark(0xFFF0F0F2)
	)
}

@Composable
fun LineChangeButtonCompose(
	availableLines: List<CarLine>,
	lineName: String,
	modifier: Modifier = Modifier,
	onClick: (List<CarLine>) -> Unit
) {
	val selectable = availableLines.size > 1
	val backgroundColor = if (selectable) 0xFF2921D1.dark(0xFF2921D1) else 0xFFE8F0FC.dark(0xFFC3D4EE)
	val textColor = if (selectable) 0xFFFFFFFF.dark(0xFFF0F0F2) else 0xFF112C54.dark(0xFFF0F0F2)
	Row(
		modifier = modifier.clip(RoundedCornerShape(16.dp)).background(backgroundColor)
			.padding(horizontal = 12.dp, vertical = 5.dp)
			.clickable(selectable) { onClick(availableLines) },
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(4.dp)
	) {
		Text(
			text = lineName,
			fontWeight = FontWeight.Bold,
			fontSize = 14.sp,
			color = textColor
		)
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
		Text(text = "运行时间: $runtime", color = 0xFF112C54.dark(0xFFF0F0F2), fontSize = 12.sp)
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