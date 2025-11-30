package com.cyxbs.pages.course.view.item.modifier

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.LocalAppColors

/**
 * item 圆角 + 阴影
 *
 * @author 985892345
 * @date 2025/11/16
 */
object RoundedShadowItemModifier : CourseItemModifier {
  @Composable
  override fun createModifier(): Modifier {
    return Modifier.padding(1.dp)
      .background(LocalAppColors.current.topBg, RoundedCornerShape(8.dp)) // 这里是为了有一层底色，在长按拖动重叠后能显示一圈细微的白色边框
      .padding(0.6.dp)
      .shadow(elevation = 0.5.dp, shape = RoundedCornerShape(8.dp))
      .background(LocalAppColors.current.topBg) // 遮挡 shadow 阴影
  }
}