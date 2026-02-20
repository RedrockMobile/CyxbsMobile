package com.cyxbs.pages.schoolcar.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintSetScope

/**
 * description ： CarInfo抽屉的布局约束
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/19 14:24
 */
enum class CarInfoBtsElement {
	ShapeTip,
	LineSelector,


	//线路模式特有元素
	LineTitle,
	LineRunTime,
	LineTypeTags,
	RouteList,

	//站点模式特有元素
	SiteName,
	SiteList,
	SwitchLineButton,

	// Empty模式时的提示
	ErrorInfo
}


class CarInfoBtsConstraintSet(
	val scope: ConstraintSetScope,
	val windowSize: DpSize,
	val displayMode: CarInfoBtsDisplayMode
) {
	val shapeTip = scope.createRefFor(CarInfoBtsElement.ShapeTip)
	val lineSelector = scope.createRefFor(CarInfoBtsElement.LineSelector)
	val lineTitle = scope.createRefFor(CarInfoBtsElement.LineTitle)
	val lineRunTime = scope.createRefFor(CarInfoBtsElement.LineRunTime)
	val lineTypeTags = scope.createRefFor(CarInfoBtsElement.LineTypeTags)
	val siteName = scope.createRefFor(CarInfoBtsElement.SiteName)
	val siteList = scope.createRefFor(CarInfoBtsElement.SiteList)
	val switchLineButton = scope.createRefFor(CarInfoBtsElement.SwitchLineButton)
	val routeList = scope.createRefFor(CarInfoBtsElement.RouteList)
	val errorInfo = scope.createRefFor(CarInfoBtsElement.ErrorInfo)

	fun createConstrain() {
		// 后续可根据这个进行适配
		val ratio = windowSize.height / windowSize.width
		wh100vInfinity()
	}

}

/**
 *竖屏
 */
private fun CarInfoBtsConstraintSet.wh100vInfinity() {
	wh100vInfinityCommonConstraints()
	when (displayMode) {
		CarInfoBtsDisplayMode.Empty -> {
			wh100vInfinityEmptyMode()
		}

		is CarInfoBtsDisplayMode.LineOverview -> wh100vInfinityLineMode()
		is CarInfoBtsDisplayMode.SiteOverView -> wh100vInfinitySiteMode()
	}
}

// 竖屏的公有元素
private fun CarInfoBtsConstraintSet.wh100vInfinityCommonConstraints() {
	with(scope) {
		constrain(shapeTip) {
			linkTo(parent.start, parent.end)
			top.linkTo(parent.top, 7.dp)
		}
		constrain(lineSelector) {
			linkTo(parent.start, parent.end)
			top.linkTo(shapeTip.bottom)
		}
	}
}

// 竖屏的线路模式
private fun CarInfoBtsConstraintSet.wh100vInfinityLineMode() {
	with(scope) {
		constrain(lineTitle) {
			start.linkTo(parent.start, 16.dp)
			top.linkTo(lineSelector.bottom, 19.dp)
		}

		constrain(routeList) {
			start.linkTo(parent.start)
			top.linkTo(lineTitle.bottom, 16.dp)
		}
		constrain(lineRunTime) {
			end.linkTo(parent.end, 16.dp)
			top.linkTo(lineSelector.bottom, 12.dp)
		}
		constrain(lineTypeTags) {
			end.linkTo(parent.end, 16.dp)
			top.linkTo(lineRunTime.bottom, 8.dp)
		}
	}
}


// 竖屏的空内容模式
private fun CarInfoBtsConstraintSet.wh100vInfinityEmptyMode() {
	with(scope) {
		constrain(errorInfo) {
			linkTo(lineSelector.bottom, parent.bottom)
			linkTo(parent.start, parent.end)
		}
	}
}

// 竖屏的站点模式
private fun CarInfoBtsConstraintSet.wh100vInfinitySiteMode() {
	with(scope) {
		constrain(siteName) {
			start.linkTo(parent.start, 16.dp)
			top.linkTo(lineSelector.bottom, 19.dp)
		}
		constrain(siteList) {
			start.linkTo(parent.start)
			top.linkTo(siteName.bottom, 22.dp)
		}
		constrain(switchLineButton) {
			end.linkTo(parent.end, 10.dp)
			top.linkTo(siteName.top, 10.dp)
		}
	}
}