package com.cyxbs.pages.course.home.dialog.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator

/**
 * .
 *
 * @author 985892345
 * @date 2025/5/25
 */
@Composable
fun AffairBottomSheetDialog(/*model: AffairIdModelEditorUnsafe, */enableEdit: Boolean) {
//  TitleWithBtn()
}


@Composable
private fun TitleWithBtn(title: String, enableEdit: Boolean) {
  Layout(
    modifier = Modifier.fillMaxWidth(),
    content = {
      Text(
        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
        text = title,
        fontSize = 22.sp,
        color = LocalAppColors.current.tvLv2,
        fontWeight = FontWeight.Bold,
      )
      if (enableEdit) {
        Image(
          contentDescription = "编辑事务",
          contentScale = ContentScale.Inside,
          painter = rememberVectorPainter(Icons.Default.Settings),
          modifier = Modifier.clickableNoIndicator {
            // 编辑面板
          },
        )
      }
    },
    measurePolicy = { measurables, constraints ->
      val icon = measurables.getOrNull(1)?.measure(
        Constraints(
          maxWidth = constraints.maxWidth,
          maxHeight = constraints.maxHeight,
        )
      )
      val textTitle = measurables[0].measure(
        Constraints(
          maxWidth = constraints.maxWidth - (icon?.width?.plus(16.dp.roundToPx()) ?: 0),
          maxHeight = constraints.maxHeight,
        )
      )
      layout(constraints.maxWidth, textTitle.height) {
        textTitle.placeRelative(0, 0)
        icon?.placeRelative(
          constraints.maxWidth - icon.width,
          (textTitle.height - icon.height) / 2
        )
      }
    }
  )
}