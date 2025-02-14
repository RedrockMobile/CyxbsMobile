package com.cyxbs.pages.course.view.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstrainedLayoutReference
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintLayoutScope
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/29
 */

@Stable
interface CourseHeaderController {
  val title: String
  val subtitle: String

  /**
   * 副标题缩放比例
   */
  val subtitleScale: Float

  /**
   * 回到本周按钮偏移量，0: 完整显示；1: 完全隐藏
   */
  val backBtnOffsetRatio: Float

  @Composable
  fun ConstraintLayoutScope.ExtraContent(
    title: ConstrainedLayoutReference,
    subtitle: ConstrainedLayoutReference,
    back: ConstrainedLayoutReference
  ) {

  }

  fun onClickTitle()
  fun onClickSubtitle()
  fun onClickBack()
}

@Composable
fun CourseHeaderCompose(
  controller: CourseHeaderController,
  modifier: Modifier = Modifier,
) {
  CourseHeaderTipCompose(modifier = modifier) {
    CourseHeaderContentCompose(
      modifier = Modifier,
      controller = controller,
    )
  }
}

/**
 * 课程头部 BottomSheet 背景
 */
@Composable
fun CourseHeaderTipCompose(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(
    modifier = modifier.fillMaxWidth().height(70.dp)
  ) {
    // 阴影
    Spacer(
      modifier = Modifier.fillMaxSize().background(
        brush = Brush.verticalGradient(
          colors = listOf(Color(0x00365789), Color(0x3D365789))
        )
      )
    )
    Box(
      modifier = Modifier.padding(top = 15.dp)
        .fillMaxSize()
        .background(color = LocalAppColors.current.topBg, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
      Spacer(
        modifier = Modifier.align(Alignment.TopCenter)
          .padding(top = 10.dp)
          .size(38.dp, 5.dp)
          .background(color = 0xFFE2EDFB.dark(Color.Black), shape = RoundedCornerShape(6.dp))
      )
    }
    content()
  }
}

@Composable
fun CourseHeaderContentCompose(
  modifier: Modifier = Modifier,
  controller: CourseHeaderController,
) {
  ConstraintLayout(modifier = modifier.fillMaxSize()) {
    val (title, subtitle, back) = createRefs()
    Text(
      text = controller.title,
      modifier = Modifier.constrainAs(title) {
        start.linkTo(parent.start, 16.dp)
        bottom.linkTo(parent.bottom, 4.dp)
      }.clickableNoIndicator { controller.onClickTitle() },
      color = LocalAppColors.current.tvLv2,
      fontWeight = FontWeight.Bold,
      fontSize = 22.sp
    )
    Text(
      text = controller.subtitle,
      modifier = Modifier.constrainAs(subtitle) {
        start.linkTo(title.end, 13.dp)
        baseline.linkTo(title.baseline)
      }.graphicsLayer {
        alpha = controller.subtitleScale
        scaleX = controller.subtitleScale
        scaleY = controller.subtitleScale
      }.clickableNoIndicator { controller.onClickSubtitle() },
      fontSize = 15.sp,
      color = LocalAppColors.current.tvLv2,
    )
    Text(
      text = "回到本周",
      modifier = modifier.constrainAs(back) {
        end.linkTo(parent.end, 16.dp)
        top.linkTo(title.top)
        bottom.linkTo(title.bottom)
      }.graphicsLayer {
        alpha = 1 - controller.backBtnOffsetRatio
        translationX = controller.backBtnOffsetRatio * size.width
      }.clip(CircleShape).background(
        brush = Brush.horizontalGradient(
          colors = listOf(Color.Blue, Color(0xFF8686FF)),
        )
      ).padding(vertical = 6.dp, horizontal = 14.dp)
        .clickable { controller.onClickBack() },
      color = Color.White,
      fontSize = 13.sp,
    )
    controller.apply { ExtraContent(title, subtitle, back) }
  }
}