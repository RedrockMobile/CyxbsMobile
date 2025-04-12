package com.cyxbs.pages.course.home.header

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
import cyxbsmobile.cyxbs_pages.course.generated.resources.Res
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_course_header_landmark
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_course_header_time
import org.jetbrains.compose.resources.painterResource

/**
 * 提供给主页课表展示外层的 Header，应由 CourseItem 实现类进行实现，参考 SelfLessonItem
 *
 * @author 985892345
 * @date 2025/3/16
 */
@Stable
interface BottomSheetItemHeader {

  @Composable
  fun BottomSheetHeaderContent(modifier: Modifier)
}

// 只显示文本的 BottomSheet Header
class HintBottomSheetItemHeader(
  val hint: String,
  val onLongClick: (() -> Unit)? = null,
  val onClick: (() -> Unit)? = null,
) : BottomSheetItemHeader {

  @Composable
  override fun BottomSheetHeaderContent(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
      Text(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 6.dp)
          .combinedClickable(
            interactionSource = null,
            indication = null,
            onLongClick = { onLongClick?.invoke() },
            onClick = { onClick?.invoke() },
          ),
        text = hint,
        color = LocalAppColors.current.tvLv4,
        fontSize = 14.sp,
      )
    }
  }
}

@Composable
fun CourseItemBottomSheetHeader(
  modifier: Modifier,
  state: State<String>,
  title: String,
  content: String,
  beginTime: MinuteTime,
  finalTime: MinuteTime,
  enableShowLandmark: Boolean = false,
  onClickTitle: (() -> Unit)? = null,
  onClickContent: (() -> Unit)? = null,
) {
  Row(modifier = modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
    Column(
      modifier = Modifier.weight(1F).padding(start = 16.dp, end = 8.dp, bottom = 2.dp)
    ) {
      Text(
        text = state.value,
        color = LocalAppColors.current.tvLv4,
        fontSize = 8.sp
      )
      Text(
        modifier = Modifier.basicMarquee(
          iterations = Int.MAX_VALUE,
        ).clickable(
          interactionSource = null,
          indication = null,
          onClick = { onClickTitle?.invoke() }
        ),
        text = title,
        color = LocalAppColors.current.tvLv2,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
      )
    }
    Row(modifier = Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Image(
        painter = painterResource(Res.drawable.course_ic_course_header_time),
        contentDescription = "${beginTime.hour}点${beginTime.minute}分至${finalTime.hour}点${finalTime.minute}分",
        modifier = Modifier.padding(end = 5.dp)
      )
      Text(
        modifier = Modifier,
        text = "${beginTime.hour}:${beginTime.minute.toString().padStart(2, '0')}-" +
            "${finalTime.hour}:${finalTime.minute.toString().padStart(2, '0')}",
        fontSize = 14.sp,
        color = LocalAppColors.current.tvDefault,
      )
    }
    Box(
      modifier = Modifier.weight(1F)
      .padding(start = 8.dp, end = 16.dp, bottom = 6.dp)
    ) {
      Row(
        modifier = Modifier.align(Alignment.CenterEnd).clickable(
          interactionSource = null,
          indication = null,
          onClick = { onClickContent?.invoke() }),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (enableShowLandmark) {
          Image(
            painter = painterResource(Res.drawable.course_ic_course_header_landmark),
            contentDescription = content,
            modifier = Modifier.padding(end = 5.dp)
          )
        }
        Text(
          modifier = Modifier,
          text = content,
          overflow = TextOverflow.Ellipsis,
          fontSize = 14.sp,
          color = LocalAppColors.current.tvDefault,
        )
      }
    }
  }
}
