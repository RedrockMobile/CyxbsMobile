package com.cyxbs.pages.schoolcar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SCHOOL_CAR
import com.cyxbs.components.utils.compose.backHandler
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.pages.schoolcar.api.SchoolCarNavArgument
import com.cyxbs.pages.schoolcar.viewmodel.SchoolCarViewModel
import com.cyxbs.pages.schoolcar.widget.CarInfoButtonSheet
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_map_back
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_positioning
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_zoomexpand
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_zoomout
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * description ： 校车查询页
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/18 21:19
 */
@AppNav(route = NAV_SCHOOL_CAR)
class SchoolCarNavDestination : AppNavEntry<SchoolCarNavArgument>() {

	override fun isNeedLogin(argument: SchoolCarNavArgument): Boolean {
		return false
	}

	@Composable
	override fun Content(argument: SchoolCarNavArgument) {
		viewModel { SchoolCarViewModel() } // wasm 无法反射 new 对象，这里需要提供 factory
		val viewModel = viewModel(SchoolCarViewModel::class)
		DownLoadProgressDialog(argument)
		DownLoadErrorDialog(argument)
		AnimatedContent(
			targetState = viewModel.schoolCarPage.value,
			transitionSpec = {
				if (targetState > initialState) {
					slideInHorizontally { width -> width } togetherWith
						slideOutHorizontally { width -> -width }
				} else {
					slideInHorizontally { width -> -width } togetherWith
						slideOutHorizontally { width -> width }
				}
			},
			modifier = Modifier.backHandler {
				if (viewModel.schoolCarPage.value == 1) {
					viewModel.backFromDetail()
				} else {
					argument.popBackStack()
				}
			}
		) { page ->
			if (page == 0) {
				SchoolCarPage(argument)
			} else {
				SchoolCarDetailPage()
			}
		}
	}
}

@Composable
fun SchoolCarPage(argument: SchoolCarNavArgument) {
	val viewModel = viewModel(SchoolCarViewModel::class)
	SchoolCarMapCompose()
	CarInfoButtonSheet(
		state = viewModel.btsState,
		toggleSelectLine = viewModel::toggleSelectLine,
		toggleCurrentLine = viewModel::toggleCurrentLine,
		onSelectClosedSite = viewModel::selectClosedSite
	)
	SchoolCarMapFunctionButtonCompose(argument)
}

@Composable
fun SchoolCarMapFunctionButtonCompose(argument: SchoolCarNavArgument) {
	val windowScreenSize = getWindowScreenSize()
	val heigh = windowScreenSize.height / 5


	Box(Modifier.fillMaxSize()) {
		ZoomButtonCompose(Modifier.align(Alignment.TopEnd).padding(top = heigh, end = 16.dp))
		// 返回按钮
		Image(
			modifier = Modifier.align(Alignment.TopStart).systemBarsPadding()
				.padding(top = 5.dp, start = 25.dp)
				.clickableNoIndicator {
					argument.popBackStack()
				},
			painter = painterResource(Res.drawable.schoolcar_ic_map_back),
			contentDescription = null
		)
	}
}

@Composable
fun ZoomButtonCompose(modifier: Modifier = Modifier) {
	val viewModel = viewModel(SchoolCarViewModel::class)
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(18.dp)
	) {
		FunctionButtonItemCompose(
			res = Res.drawable.schoolcar_ic_zoomexpand,
			onClick = viewModel::zoomExpand
		)
		FunctionButtonItemCompose(res = Res.drawable.schoolcar_ic_zoomout, onClick = viewModel::zoomOut)
		if (viewModel.shouldShowUserPositionMarker.value){
			FunctionButtonItemCompose(
				res = Res.drawable.schoolcar_ic_positioning,
				onClick = viewModel::positioning
			)
		}
	}
}

@Composable
fun FunctionButtonItemCompose(
	modifier: Modifier = Modifier,
	res: DrawableResource,
	onClick: () -> Unit
) {
	Box(
		modifier = modifier.size(48.dp).clip(RoundedCornerShape(90.dp))
			.background(0xFFFFFFFF.dark(0xFF000000).copy(alpha = 0.7f)).clickableNoIndicator { onClick() }
	) {
		Image(
			modifier = Modifier.size(18.dp).align(Alignment.Center),
			painter = painterResource(res),
			contentDescription = null
		)
	}
}


@Composable
fun SchoolCarDetailPage() {
	val viewModel = viewModel(SchoolCarViewModel::class)
	Column(
		modifier = Modifier.fillMaxSize()
			.background(LocalAppColors.current.topBg)
			.systemBarsPadding(),
	) {
		SchoolCarTopbarCompose(onBack = viewModel::backFromDetail)
		LineInfoCompose(carLineJson = viewModel.carLineInfo.value)
	}
}

