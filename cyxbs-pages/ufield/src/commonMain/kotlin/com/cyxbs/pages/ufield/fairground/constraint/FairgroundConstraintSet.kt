package com.cyxbs.pages.ufield.fairground.constraint

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintSetScope

/**
 * .
 *
 * @author 985892345
 * @date 2026/5/31
 */
internal enum class Element {
  Info,
  Food,
  QaSquare,
  Activity,
}

class FairgroundConstraintSet(
  val scope: ConstraintSetScope,
  val windowSize: DpSize,
) {
  val info = scope.createRefFor(Element.Info)
  val food = scope.createRefFor(Element.Food)
  val qaSquare = scope.createRefFor(Element.QaSquare)
  val activity = scope.createRefFor(Element.Activity)

  fun createConstrain() {
    if (windowSize.width > 600.dp) {
      wh100vInfinity()
    } else {
      wh100v150()
    }
  }
}

private fun FairgroundConstraintSet.wh100v150() {
  scope.constrain(info) {
    top.linkTo(parent.top)
    bottom.linkTo(parent.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    verticalBias = 0.08f
  }
  scope.constrain(food) {
    top.linkTo(info.bottom)
    bottom.linkTo(parent.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    verticalBias = 0.1f
    horizontalBias = 0.112f
  }
  scope.constrain(qaSquare) {
    top.linkTo(info.bottom)
    bottom.linkTo(parent.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    verticalBias = 0.3f
    horizontalBias = 0.9f
  }
  scope.constrain(activity) {
    top.linkTo(food.bottom)
    bottom.linkTo(parent.bottom)
    start.linkTo(food.start)
    end.linkTo(qaSquare.start)
    verticalBias = 0.5f
    horizontalBias = 0.7f
  }
}

private fun FairgroundConstraintSet.wh100vInfinity() {
  scope.constrain(info) {
    top.linkTo(parent.top)
    bottom.linkTo(parent.bottom)
    start.linkTo(parent.start)
    end.linkTo(parent.end)
    verticalBias = 0.08f
  }
  scope.constrain(food) {
    top.linkTo(info.bottom)
    bottom.linkTo(parent.bottom)
    start.linkTo(parent.start)
    end.linkTo(activity.start)
    verticalBias = 0.4f
  }
  scope.constrain(activity) {
    bottom.linkTo(food.bottom)
    start.linkTo(food.end)
    end.linkTo(qaSquare.start)
  }
  scope.constrain(qaSquare) {
    bottom.linkTo(food.bottom)
    start.linkTo(activity.end)
    end.linkTo(parent.end)
  }
}