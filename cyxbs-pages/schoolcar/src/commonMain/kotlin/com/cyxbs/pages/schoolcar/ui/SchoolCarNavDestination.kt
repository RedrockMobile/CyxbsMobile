package com.cyxbs.pages.schoolcar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_SCHOOL_CAR
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.compose.backHandler
import com.cyxbs.pages.schoolcar.api.SchoolCarNavArgument
import com.cyxbs.pages.schoolcar.viewmodel.SchoolCarViewModel
import com.cyxbs.pages.schoolcar.widget.CarInfoButtonSheet
import com.g985892345.provider.api.annotation.ImplProvider

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
	CarInfoButtonSheet()
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

