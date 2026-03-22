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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.cyxbs.components.view.ui.BottomSheetCompose
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

	// UI实际渲染内容，线路模式切换到Empty后，先维持显示内容不变完成关闭动画后在清空内容
	val render = remember { mutableStateOf(state.displayMode.value) }

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
			constraintSet = createConstraintSet()
		) {
			ShapeTipCompose(modifier = Modifier.layoutId(CarInfoBtsElement.ShapeTip))
			LineSelectorCompose(
				modifier = Modifier.layoutId(layoutId = CarInfoBtsElement.LineSelector),
				list,
				selectedId,
				toggleSelectLine
			)
			when (val showMode = render.value) {
				is ErrorOverView -> {
					ErrorInfoCompose(Modifier.layoutId(CarInfoBtsElement.ErrorInfo))
				}

				is LineOverview -> {
					TittleCompose(
						showMode.line.name, Modifier.layoutId(CarInfoBtsElement.LineTitle)
					)
					RuntimeCompose(showMode.line.runTime, Modifier.layoutId(CarInfoBtsElement.LineRunTime))
					LineTypeCompose(
						sendType = showMode.line.sendType,
						runType = showMode.line.runType,
						modifier = Modifier.layoutId(CarInfoBtsElement.LineTypeTags)
					)
					RouteListCompose(
						modifier = Modifier.layoutId(CarInfoBtsElement.RouteList),
						siteId = -1,
						line = showMode.line
					)
				}

				is SiteOverView -> {
					TittleCompose(
						showMode.site.name,
						modifier = Modifier.layoutId(CarInfoBtsElement.SiteName)
					)

					RouteListCompose(
						modifier = Modifier.layoutId(CarInfoBtsElement.SiteList),
						siteId = showMode.site.id,
						line = showMode.currentLine
					)

					LineChangeButtonCompose(
						modifier = Modifier.layoutId(CarInfoBtsElement.SwitchLineButton),
						lineName = showMode.currentLine.name,
						availableLines = showMode.availableLines,
						onClick = toggleCurrentLine
					)
				}

				is Empty -> {}
			}
		}
	}
	// 选择线路/选择站点的时候自动展开
	LaunchedEffect(state.displayMode.value) {
		try {
			state.isStateChanging.value = true
			when (state.displayMode.value) {
				is Empty -> {
					//切换到empty
					state.bottomSheetState.collapse()
					render.value = Empty
				}

				else -> {
					render.value = state.displayMode.value
					//等待一会完成测量
					delay(100)
					state.bottomSheetState.expand()
				}
			}
		} finally {
			state.isStateChanging.value = false
		}
	}

	// 监听当用户处于Empty模式的时候上滑选择最近的站点
	LaunchedEffect(Unit) {
		var lastFraction = 0f
		snapshotFlow { state.bottomSheetState.fraction }
			.collect { currentFraction ->
				val isPullingUp = currentFraction > lastFraction
				if (state.displayMode.value is Empty) {
					if (currentFraction > 0.03f &&
						isPullingUp &&
						lastFraction < 0.1f
					) {
						onSelectClosedSite()
					}
				}
				lastFraction = currentFraction
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
private fun createConstraintSet(): ConstraintSet {
	val windowSize = getWindowScreenSize()
	return ConstraintSet {
		CarInfoBtsConstraintSet(
			scope = this,
			windowSize = windowSize,
		).createConstrain()
	}
}