package com.cyxbs.pages.sport.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintSetScope

enum class SportElement {
    TopBar,
    DetailTotalTitle,
    DetailTotalDone,
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
    }
}