package com.cyxbs.pages.food.service

import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.store.api.IStoreService
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * description ：  美食的扩展服务类的实现类
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/11/10 22:38
 */
@ImplProvider(clazz = IFoodService::class)
object FoodServiceImpl : IFoodService {
	override fun doFinishTask() {
		IStoreService::class.impl().postTask(
			IStoreService.Task.JOIN_FOOD,
			"",
			"今日已使用美食咨询处一次，获得10邮票"
		)
	}
}