package com.cyxbs.pages.sport.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintSetScope
import androidx.constraintlayout.compose.Dimension

enum class SportElement {
    TopBar,
    DetailTotalTitle,
    DetailTotal,
    SportImage,
    SportDetailRun,
    SportRecord
}

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
    fun createConstrain() {
        //预留后续根据比例适配
        val ratio = windowSize.height / windowSize.width
        wh100vInfinity()
    }
}

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