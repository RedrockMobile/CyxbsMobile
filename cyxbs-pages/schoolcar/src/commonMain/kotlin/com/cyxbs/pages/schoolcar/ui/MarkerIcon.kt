package com.cyxbs.pages.schoolcar.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_1
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_2
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_3
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_background_4
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_1
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_2
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_3
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_4
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
fun StationIconCompose(
	state: StationMarkerState,
	currentSelectLine: Int?,
	isSelect: Boolean = false
) {
	if (!state.visible.value) return

	Box(
		contentAlignment = Alignment.BottomCenter
	) {
		if (isSelect && currentSelectLine != null) {
			val infiniteTransition = rememberInfiniteTransition()
			val scale = infiniteTransition.animateFloat(
				initialValue = 0.8f, targetValue = 1.2f,
				animationSpec = infiniteRepeatable(
					animation = tween(1500),
					repeatMode = RepeatMode.Reverse
				)
			)

			Image(
				modifier = Modifier
					.graphicsLayer {
						scaleX = scale.value
						scaleY = scale.value
						transformOrigin = TransformOrigin.Center
						translationY = size.height / 2f
					},
				painter = painterResource(getBackGroundByLineId(currentSelectLine)),
				contentDescription = null
			)
		}


		Image(
			painter = painterResource(getStationResByLineId(currentSelectLine)),
			contentDescription = null
		)
	}
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

fun getCarResByLineId(lineId: Int): DrawableResource {
	return when (lineId) {
		0 -> Res.drawable.schoolcar_ic_car_1
		1 -> Res.drawable.schoolcar_ic_car_2
		2 -> Res.drawable.schoolcar_ic_car_3
		3 -> Res.drawable.schoolcar_ic_car_4
		else -> Res.drawable.schoolcar_ic_car_1
	}
}

@Composable
fun CarIconCompose(lineId: Int) {
	Image(
		painter = painterResource(getCarResByLineId(lineId)),
		contentDescription = null
	)
}