package com.cyxbs.pages.food.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.food.bean.FoodMainBean
import com.cyxbs.pages.food.bean.FoodPraiseBean
import com.cyxbs.pages.food.bean.FoodRefreshBean
import com.cyxbs.pages.food.bean.FoodResultBeanItem
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST

/**
 * description ： 美食咨询处的网络访问API
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/11/2 22:09
 */
interface FoodApiServiceKtorfit {
	/**
	 * 美食首页图片数据；就餐区域、就餐人数、餐饮特征数据
	 */
	@GET("magipoke-delicacy/HomePage")
	suspend fun getFoodMain(): ApiWrapper<FoodMainBean>

	/**
	 * 获取美食数据
	 */
	@POST("magipoke-delicacy/food/result")
	@FormUrlEncoded
	suspend fun postFoodResult(
		@Field("eat_area") eatArea: List<String>,
		@Field("eat_num") eatNum: String, @Field("eat_property") eatProperty: List<String>
	): ApiWrapper<List<FoodResultBeanItem>>


	/**
	 *点赞
	 */
	@POST("/magipoke-delicacy/food/praise")
	@FormUrlEncoded
	suspend fun postFoodPraise(@Field("name") name: String): ApiWrapper<FoodPraiseBean>

	/**
	 * 刷新餐饮特征
	 */
	@POST("/magipoke-delicacy/food/refresh")
	@FormUrlEncoded
	suspend fun postFoodRefresh(
		@Field("eat_area") eatArea: List<String>,
		@Field("eat_num") eatNum: String
	): ApiWrapper<FoodRefreshBean>


}