package com.cyxbs.pages.schoolcar.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_SCHOOL_CAR
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
		SchoolCarPage()
	}
}

@Composable
fun SchoolCarPage() {
	CarInfoButtonSheet()
}

