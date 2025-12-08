package com.cyxbs.pages.map.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintSetScope
import androidx.constraintlayout.compose.Dimension

/**
 * @Desc : 底部bottomSheet的ConstraintSet
 * @Author : zzx
 * @Date : 2025/11/30 13:18
 */

enum class Element {
  ShapeTip,
  PlaceTitle,
  PlaceAttributeList,
  PlaceFavorite,
  PlaceNavigation,
  DetailText,
  DetailMoreText,
  ImageBanner,
  DetailShare,
  DetailAboutText,
  DetailAboutList
}

class PlaceDetailConstraintSet(
  val scope: ConstraintSetScope,
  val windowSize: DpSize
) {
  val shapeTip = scope.createRefFor(Element.ShapeTip)
  val placeTitle = scope.createRefFor(Element.PlaceTitle)
  val placeAttributeList = scope.createRefFor(Element.PlaceAttributeList)
  val placeFavorite = scope.createRefFor(Element.PlaceFavorite)
  val placeNavigation = scope.createRefFor(Element.PlaceNavigation)
  val detailText = scope.createRefFor(Element.DetailText)
  val detailMoreText = scope.createRefFor(Element.DetailMoreText)
  val imageBanner = scope.createRefFor(Element.ImageBanner)
  val detailShare = scope.createRefFor(Element.DetailShare)
  val detailAboutText = scope.createRefFor(Element.DetailAboutText)
  val detailAboutList = scope.createRefFor(Element.DetailAboutList)

  fun createConstrain() {
    // 后续可根据这个进行适配
    val ratio = windowSize.height / windowSize.width
    wh100vInfinity()
  }
}

private fun PlaceDetailConstraintSet.wh100vInfinity() {
  scope.constrain(shapeTip) {
    linkTo(parent.start, parent.end)
    top.linkTo(parent.top, 8.dp)
  }
  scope.constrain(placeTitle) {
    top.linkTo(shapeTip.bottom, 18.dp)
    start.linkTo(parent.start)
  }
  scope.constrain(placeAttributeList) {
    linkTo(placeNavigation.top, placeNavigation.bottom)
    linkTo(parent.start, placeNavigation.end)
    width = Dimension.fillToConstraints
  }
  scope.constrain(placeFavorite) {
    linkTo(placeTitle.top, placeTitle.bottom)
    end.linkTo(parent.end)
  }
  scope.constrain(placeNavigation) {
    top.linkTo(placeFavorite.bottom)
    end.linkTo(parent.end)
  }
  scope.constrain(detailText) {
    top.linkTo(placeNavigation.bottom, 21.dp)
    start.linkTo(parent.start)
  }
  scope.constrain(detailMoreText) {
    linkTo(detailText.top, detailText.bottom)
    end.linkTo(parent.end)
  }
  scope.constrain(imageBanner) {
    linkTo(parent.start, parent.end, 20.dp, 20.dp)
    top.linkTo(detailText.bottom, 10.dp)
  }
  scope.constrain(detailShare) {
    top.linkTo(imageBanner.bottom)
    end.linkTo(parent.end)
  }
  scope.constrain(detailAboutText) {
    top.linkTo(detailShare.bottom)
    bottom.linkTo(detailAboutList.top)
    start.linkTo(parent.start)
  }
  scope.constrain(detailAboutList) {
    linkTo(parent.start, parent.end)
    linkTo(detailAboutText.bottom, parent.bottom, 8.dp, 8.dp)
    width = Dimension.fillToConstraints
  }
}