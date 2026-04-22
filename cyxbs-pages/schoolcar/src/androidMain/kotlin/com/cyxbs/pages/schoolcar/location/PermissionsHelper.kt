package com.cyxbs.pages.schoolcar.location

import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.location.LOCATION

/**  
 * description ： 用于配合Moko-Permissions获取定位权限
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/3/26 18:08
 */
class PermissionsHelper(val controller: PermissionsController) {
	suspend fun requestLocationPermission(
		onSuccess: () -> Unit,
		onError: (Exception) -> Unit = {}
	) {
		try {
			controller.providePermission(Permission.LOCATION)
			onSuccess()
		} catch (e: Exception) {
			onError(e)
		}
	}

	suspend fun hasPermission(): Boolean {
		return controller.isPermissionGranted(Permission.LOCATION)
	}
}