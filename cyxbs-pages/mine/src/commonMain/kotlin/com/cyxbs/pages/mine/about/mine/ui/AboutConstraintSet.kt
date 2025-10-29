package com.cyxbs.pages.mine.about.mine.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintSetScope
import androidx.constraintlayout.compose.Dimension

/**
 * @Desc : 关于我们页面的Constraint约束
 * @Author : zzx
 * @Date : 2025/10/28 16:00
 */

enum class Element {
    Topbar,
    Logo,
    AppInfo,
    BackgroundIv,
    VersionUpdate,
    VersionInfo,
    ProductWebsite,
    Share,
    BottomInfo
}

class AboutConstraintSet(
    val scope: ConstraintSetScope,
    val windowSize: DpSize
) {
    val topBar = scope.createRefFor(Element.Topbar)
    val logo = scope.createRefFor(Element.Logo)
    val appInfo = scope.createRefFor(Element.AppInfo)
    val backgroundIv = scope.createRefFor(Element.BackgroundIv)
    val versionUpdate = scope.createRefFor(Element.VersionUpdate)
    val versionInfo = scope.createRefFor(Element.VersionInfo)
    val productWebsite = scope.createRefFor(Element.ProductWebsite)
    val share = scope.createRefFor(Element.Share)
    val bottomInfo = scope.createRefFor(Element.BottomInfo)

    fun createConstrain() {
        // 后续可根据这个进行适配
        val ratio = windowSize.height / windowSize.width
        wh100vInfinity()
    }
}

private fun AboutConstraintSet.wh100vInfinity() {
    scope.constrain(topBar) {
        top.linkTo(parent.top)
        linkTo(parent.start, parent.end)
        width = Dimension.fillToConstraints
    }
    scope.constrain(logo) {
        linkTo(topBar.bottom, parent.bottom)
        linkTo(parent.start, parent.end)
        verticalBias = 0.08f
    }
    scope.constrain(appInfo) {
        top.linkTo(logo.bottom, 14.dp)
        linkTo(parent.start, parent.end)
    }
    scope.constrain(backgroundIv) {
        top.linkTo(appInfo.bottom, 40.dp)
        bottom.linkTo(parent.bottom)
        linkTo(parent.start, parent.end)
        width = Dimension.fillToConstraints
        height = Dimension.fillToConstraints
    }
    scope.constrain(versionUpdate) {
        linkTo(backgroundIv.top, versionInfo.top)
        linkTo(parent.start, parent.end)
        width = Dimension.fillToConstraints
    }
    scope.constrain(versionInfo) {
        linkTo(versionUpdate.bottom, productWebsite.top)
        linkTo(parent.start, parent.end)
        width = Dimension.fillToConstraints
    }
    scope.constrain(productWebsite) {
        linkTo(versionInfo.bottom, share.top)
        linkTo(parent.start, parent.end)
        width = Dimension.fillToConstraints
    }
    scope.constrain(share) {
        linkTo(productWebsite.bottom, bottomInfo.top, 0.dp, 50.dp)
        linkTo(parent.start, parent.end)
        width = Dimension.fillToConstraints
    }
    scope.constrain(bottomInfo) {
        bottom.linkTo(parent.bottom, 40.dp)
        linkTo(parent.start, parent.end)
    }
}