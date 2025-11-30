package com.cyxbs.pages.food.service

/**
 * description ： 关于美食的扩展的服务类
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/11/10 22:36
 */
interface IFoodService {

	//完成每日任务
	//TODO 因为store.api模块中的完成任务是在androidMain中，无法再commonMain中直接使用
	fun doFinishTask()
}