package com.cyxbs.pages.schoolcar.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.schoolcar.bean.CarInfoVersion
import com.cyxbs.pages.schoolcar.bean.CarLineJson
import com.cyxbs.pages.schoolcar.bean.CarLocationJson
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST

/**
 * description ： 校车轨迹的网络访问API
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/18 23:10
 */
interface SchoolCarApiService {
	//获得校车线路版本号
	@GET("schoolbus/map/version")
	suspend fun getCarInfoVersion(): ApiWrapper<CarInfoVersion>

	//获得线路信息
	@GET("schoolbus/map/line")
	suspend fun getCarLine(): ApiWrapper<CarLineJson>

	//获取校车位置
	@POST("schoolbus/status")
	@FormUrlEncoded
	suspend fun getCarLocation(
		@Header("Authorization") authorization: String,
		@Field("s") s: String, // 当前时间.Redrock md5加密
		@Field("t") t: String, // 时间戳
		@Field("r") r: String // 上一秒的时间戳 md5加密
	): ApiWrapper<CarLocationJson>
}