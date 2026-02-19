package com.cyxbs.pages.schoolcar.model

import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.PreferencesSettings
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.schoolcar.bean.CarLineJson

/**
 * description ： 路线信息存储与管理
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/18 22:56
 */

val carData = PreferencesSettings.get("CarData")

object CarDataModel {
	private const val DATA_KEY_LINE_INFO = "line_info" // 线路信息

	/**
	 * 保存线路信息
	 */
	fun saveCarLineInfo(carLineInfo: CarLineJson) {
		carData.putString(DATA_KEY_LINE_INFO, defaultJson.encodeToString<CarLineJson>(carLineInfo))
	}


	/**
	 * 拿取路线信息
	 */
	fun getCarLine(): CarLineJson? {
		return carData.getStringOrNull(DATA_KEY_LINE_INFO)?.let { json ->
			runCatching {
				defaultJson.decodeFromString<CarLineJson>(json)
			}.onFailure {
				carData.remove(DATA_KEY_LINE_INFO)
				if (isDebug()) toast("线路转换异常, ${it.message}")
			}.getOrNull()
		}
	}

}
