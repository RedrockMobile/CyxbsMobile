package com.cyxbs.pages.food.model

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.food.bean.FoodMainBean
import com.cyxbs.pages.food.bean.FoodPraiseBean
import com.cyxbs.pages.food.bean.FoodRefreshBean
import com.cyxbs.pages.food.bean.FoodResultBeanItem
import com.cyxbs.pages.food.network.FoodApiServiceKtorfit

/**
 * description ： 美食咨询处的Repository
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/11/2 23:45
 */
object FoodRepository {
	/**
	 * 美食首页图片数据；就餐区域、就餐人数、餐饮特征数据
	 */
	suspend fun requestFoodMain(): Result<FoodMainBean> {
		return runCatchingCoroutine {
			FoodApiServiceKtorfit::class.impl().getFoodMain()
		}.mapCatching {
			it.data
		}
	}

	suspend fun requestRandomGenerate(
		eatArea: List<String>,
		eatNumber: String,
		eatProperty: List<String>
	): Result<List<FoodResultBeanItem>> {
		return runCatchingCoroutine {
			FoodApiServiceKtorfit::class.impl().postFoodResult(
				eatArea = eatArea,
				eatNum = eatNumber,
				eatProperty = eatProperty
			)
		}.mapCatching {
			//随机出来的推荐美食
			it.data
		}
	}

	/**
	 * 点赞Food
	 */
	suspend fun doPraiseFood(
		name: String
	): Result<FoodPraiseBean> {
		return runCatchingCoroutine {
			FoodApiServiceKtorfit::class.impl().postFoodPraise(name)
		}.mapCatching {
			it.data
		}
	}

	/**
	 * 刷新餐饮特征
	 */
	suspend fun refreshProperty(
		eatArea: List<String>,
		eatNum: String
	): Result<FoodRefreshBean> {
		return runCatchingCoroutine {
			FoodApiServiceKtorfit::class.impl().postFoodRefresh(eatArea, eatNum)
		}.mapCatching {
			it.data
		}
	}
}