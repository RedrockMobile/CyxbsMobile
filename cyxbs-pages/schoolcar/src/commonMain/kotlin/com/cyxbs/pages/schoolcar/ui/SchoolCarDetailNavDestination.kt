package com.cyxbs.pages.schoolcar.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_SCHOOL_CAR
import com.cyxbs.components.config.navigation.NAV_SCHOOL_CAR_DETAIL
import com.cyxbs.pages.schoolcar.viewmodel.SchoolCarViewModel
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.serialization.Serializable

/**
 * description ： 乘车指南页
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/19 21:42
 */
@Serializable
object SchoolCarDetailNavArgument

@ImplProvider(clazz = MainNavDestination::class, name = NAV_SCHOOL_CAR_DETAIL)
class SchoolCarDetailNavDestination :
	MainNavDestination<SchoolCarDetailNavArgument>(SchoolCarDetailNavArgument::class) {
	override val needLogin: Boolean
		get() = false

	@Composable
	override fun DestinationContent(parcel: DestinationParcel<SchoolCarDetailNavArgument>) {
		viewModel { SchoolCarViewModel() } // wasm 无法反射 new 对象，这里需要提供 factory

	}

}