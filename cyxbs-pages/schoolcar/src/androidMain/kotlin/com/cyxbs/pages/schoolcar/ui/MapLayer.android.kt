package com.cyxbs.pages.schoolcar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.pages.schoolcar.mapcompose.MapScope
import com.cyxbs.pages.schoolcar.viewmodel.SchoolCarViewModel
import dev.icerock.moko.permissions.compose.BindEffect

@Composable
actual fun MapScope.UserPositionLayer(user: UserPositionMarkerState) {
	val viewModel = viewModel(SchoolCarViewModel::class)
	BindEffect(viewModel.permissionsHelper.controller)
	DisposableEffect(Unit) {
		viewModel.tryStartLocation()
		onDispose {
			viewModel.stopLocation()
		}
	}

	if (viewModel.hasPermission.value && viewModel.isSupportLocation) {
		key(user.id) {
			MovableMarker(
				user
			) {
				UserPositionIconCompose()
			}
		}
	}
}