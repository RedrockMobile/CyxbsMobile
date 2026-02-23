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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_SCHOOL_CAR
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.compose.backHandler
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.pages.schoolcar.api.SchoolCarNavArgument
import com.cyxbs.pages.schoolcar.mapcompose.PlatformSchoolCarMapCompose
import com.cyxbs.pages.schoolcar.viewmodel.SchoolCarViewModel
import com.cyxbs.pages.schoolcar.widget.CarInfoButtonSheet
import com.g985892345.provider.api.annotation.ImplProvider
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_map_back
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_positioning
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_zoomexpand
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_zoomout
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * description ： 校车查询页
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/18 21:19
 */
@ImplProvider(clazz = MainNavDestination::class, name = NAV_SCHOOL_CAR)
class SchoolCarNavDestination :
	MainNavDestination<SchoolCarNavArgument>(SchoolCarNavArgument::class) {
	override val needLogin: Boolean
		get() = false

	@Composable
	override fun DestinationContent(parcel: DestinationParcel<SchoolCarNavArgument>) {
		viewModel { SchoolCarViewModel() } // wasm 无法反射 new 对象，这里需要提供 factory
		val viewModel = viewModel(SchoolCarViewModel::class)
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
					viewModel.schoolCarPage.value = 0
				} else {
					MainNavController.popBackStack()
				}
			}
		) { page ->
			if (page == 0) {
				SchoolCarPage()
			} else {
				SchoolCarDetailPage()
			}
		}
	}
}

@Composable
fun SchoolCarPage() {
	val viewModel = viewModel(SchoolCarViewModel::class)
	PlatformSchoolCarMapCompose(
		modifier = Modifier.fillMaxSize(),
		markers = viewModel.markers.value,
		cameraEventFlow = viewModel.cameraEventFlow,
		currentLine = viewModel.selectedLineId.value,
		selectSiteId = viewModel.selectedStationId.value,
		onEvent = viewModel::onMapEvent
	)
	CarInfoButtonSheet()
	SchoolCarMapFunctionButtonCompose()
	LaunchedEffect(Unit) {
		val bottomSheetState = viewModel.bottomSheetState
		snapshotFlow {
			bottomSheetState.fraction.coerceIn(0F, 1F)
		}.onEach {
			// 定位按钮随着bts的变化而变化
			viewModel.offsetYRadio.floatValue = it
		}.launchIn(this)
	}
}

@Composable
fun SchoolCarMapFunctionButtonCompose() {
	val windowScreenSize = getWindowScreenSize()
	val heigh = windowScreenSize.height / 5


	Box(Modifier.fillMaxSize()) {
		ZoomButtonCompose(Modifier.align(Alignment.TopEnd).padding(top = heigh, end = 16.dp))
		// 返回按钮
		Image(
			modifier = Modifier.align(Alignment.TopStart).systemBarsPadding()
				.padding(top = 5.dp, start = 25.dp)
				.clickableNoIndicator {
					MainNavController.popBackStack()
				},
			painter = painterResource(Res.drawable.schoolcar_ic_map_back),
			contentDescription = null
		)
	}
}

@Composable
fun ZoomButtonCompose(modifier: Modifier = Modifier) {
	val viewModel = viewModel(SchoolCarViewModel::class)
	val screenHeight = getWindowScreenSize().height
	val btsFraction = viewModel.offsetYRadio.floatValue
	// 屏幕总高 - 顶部Margin - 两个缩放按钮高度 - 按钮间距 - 自身高度 - bts折叠高度 - bts上方留白
	val maxTranslateY =
		screenHeight - (screenHeight / 5) - (48.dp * 2) - 18.dp - 48.dp - viewModel.peekHeight.value - 45.dp

	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(18.dp)
	) {
		FunctionButtonItemCompose(
			res = Res.drawable.schoolcar_ic_zoomexpand,
			onClick = viewModel::zoomExpand
		)
		FunctionButtonItemCompose(res = Res.drawable.schoolcar_ic_zoomout, onClick = viewModel::zoomOut)

		FunctionButtonItemCompose(modifier = Modifier.graphicsLayer {
			val safeOffset = maxOf(0f, maxTranslateY.toPx())
			translationY = safeOffset * (1f - btsFraction)
		}, res = Res.drawable.schoolcar_ic_positioning,{})
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

