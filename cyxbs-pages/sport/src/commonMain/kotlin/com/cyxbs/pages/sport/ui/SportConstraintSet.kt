package com.cyxbs.pages.sport.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintSetScope
import androidx.constraintlayout.compose.Dimension

/**
 * @Desc : 体育页面约束布局元素标识
 * @Author : xt
 */
enum class SportElement {
    TopBar,
    DetailTotalTitle,
    DetailTotal,
    SportImage,
    SportDetailRun,
    SportRecord
}

/**
 * 封装体育页面各区域的 ConstraintLayout 约束关系
 * @param scope 当前约束布局作用域
 * @param windowSize 页面窗口尺寸
 */
@Stable
class SportConstraintSet(
    val scope: ConstraintSetScope,
    val windowSize: DpSize
) {
    val topBar = scope.createRefFor(SportElement.TopBar)
    val detailTotalTitle = scope.createRefFor(SportElement.DetailTotalTitle)
    val detailTotal = scope.createRefFor(SportElement.DetailTotal)
    val sportImage = scope.createRefFor(SportElement.SportImage)
    val sportDetailRun = scope.createRefFor(SportElement.SportDetailRun)
    val sportRecord = scope.createRefFor(SportElement.SportRecord)

    // 创建体育页面的完整约束集合
    fun createConstrain() {
        wh100vInfinity()
    }
}

// 按纵向全屏布局连接体育页面的各个区域
private fun SportConstraintSet.wh100vInfinity() {
    with(scope) {
        constrain(topBar) {
            linkTo(start = parent.start, end = parent.end)
            top.linkTo(parent.top, 16.dp)
        }
        constrain(detailTotalTitle) {
            start.linkTo(parent.start, 4.dp)
            top.linkTo(topBar.bottom, 4.dp)
        }
        constrain(detailTotal) {
            start.linkTo(parent.start, 16.dp)
            top.linkTo(detailTotalTitle.bottom)
        }
        constrain(sportImage) {
            end.linkTo(parent.end, 4.dp)
            top.linkTo(topBar.bottom)
        }
        constrain(sportDetailRun) {
            linkTo(start = parent.start, end = parent.end)
            top.linkTo(detailTotal.bottom)
        }
        constrain(sportRecord) {
            linkTo(start = parent.start, end = parent.end)
            top.linkTo(sportDetailRun.bottom)
            bottom.linkTo(parent.bottom)
            height = Dimension.fillToConstraints
            width = Dimension.fillToConstraints
        }
    }
}
