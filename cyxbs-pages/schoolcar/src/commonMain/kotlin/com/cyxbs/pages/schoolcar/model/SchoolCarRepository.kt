package com.cyxbs.pages.schoolcar.model

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.schoolcar.bean.CarLineJson
import com.cyxbs.pages.schoolcar.bean.CarLocationJson
import com.cyxbs.pages.schoolcar.bean.MapStatic
import com.cyxbs.pages.schoolcar.network.SchoolCarApiService
import com.cyxbs.pages.schoolcar.utils.md5Hex
import kotlin.time.Clock

/**
 * description ： SchoolCarRepository的model层
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/18 21:24
 */

object SchoolCarRepository {
	val service = SchoolCarApiService::class.impl()
	suspend fun getCarInfoVersion(): Result<Long> {
		return runCatchingCoroutine {
			service.getCarInfoVersion()
		}.mapCatching {
			it.data.busInfoVersion
		}
	}

	suspend fun getCarLineInfo(): Result<CarLineJson> {
		return runCatchingCoroutine {
			service.getCarLine()
		}.mapCatching {
			it.data
		}
	}

	// TODO 目前没办法获取信息，所以这个接口可能有一定问题等待后续跟进
	// TODO 老代码中这个Authorization是硬编码的Redrock
	suspend fun getCarLocation(
		s: String = md5Hex(Clock.System.now().epochSeconds.toString() + "." + "Redrock"),
		t: String = Clock.System.now().epochSeconds.toString(),
		r: String = md5Hex(((Clock.System.now().toEpochMilliseconds() - 1) / 1000).toString())
	): Result<CarLocationJson> {
		return runCatchingCoroutine {
			service.getCarLocation(
				"Redrock", s, t, r
			)
		}.mapCatching {
			it.data
		}
	}

	suspend fun getMapConfig(): Result<MapStatic> {
		return runCatchingCoroutine {
			service.getMapInfo()
		}.mapCatching {
			it.data
		}
	}
}