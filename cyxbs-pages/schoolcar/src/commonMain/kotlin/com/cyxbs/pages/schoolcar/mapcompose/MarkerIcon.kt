package com.cyxbs.pages.schoolcar.mapcompose

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_1
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_2
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_3
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_4
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_site_0
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_site_1
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_site_2
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_site_3
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_site_default
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**  
 * description ： 各种MarkerIcon
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/19 22:34
 */
@Composable
fun StationIconCompose(state: MapMarkerState, currentSelectLine: Int?) {
	if (!state.visible) return

	Image(
		painter = painterResource(getStationResByLineId(currentSelectLine)),
		contentDescription = null
	)
}

fun getStationResByLineId(lineId: Int?): DrawableResource {
	return when (lineId) {
		null -> Res.drawable.schoolcar_ic_site_default
		0 -> Res.drawable.schoolcar_ic_site_0
		1 -> Res.drawable.schoolcar_ic_site_1
		2 -> Res.drawable.schoolcar_ic_site_2
		3 -> Res.drawable.schoolcar_ic_site_3
		else -> Res.drawable.schoolcar_ic_site_1
	}
}

fun getBackGroundByLineId(lineId: Int): DrawableResource {
	return when (lineId) {
		0 -> Res.drawable.schoolcar_ic_background_1
		1 -> Res.drawable.schoolcar_ic_background_2
		2 -> Res.drawable.schoolcar_ic_background_3
		3 -> Res.drawable.schoolcar_ic_background_4
		else -> Res.drawable.schoolcar_ic_background_1
	}
}
