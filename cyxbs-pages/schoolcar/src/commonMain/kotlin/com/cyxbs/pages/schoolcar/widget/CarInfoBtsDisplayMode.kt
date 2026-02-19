package com.cyxbs.pages.schoolcar.widget

import com.cyxbs.pages.schoolcar.bean.CarLine
import com.cyxbs.pages.schoolcar.bean.CarStation

/**
 * description ： 底部抽屉栏的显示模式
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/19 14:43
 */
sealed class CarInfoBtsDisplayMode {
	// 线路概览模式
	data class LineOverview(val line: CarLine) : CarInfoBtsDisplayMode()

	// 站点概览模式
	data class SiteOverView(
		val site: CarStation,// 当前站点
		val currentLine: CarLine,// 显示的当前线路
		val availableLines: List<CarLine> // 所有可以达到该站点的线路
	) : CarInfoBtsDisplayMode()

	// 空状态（初始状态）
	object Empty : CarInfoBtsDisplayMode()
}