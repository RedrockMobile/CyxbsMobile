package com.cyxbs.pages.schedule.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.config.time.TodayNoEffect
import com.cyxbs.components.config.time.toChinese
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.utils.get.Num2CN
import org.jetbrains.compose.resources.painterResource

/**
 * todo 主页第一层 Header —— 参考课表 `CourseFrameHeader` 设计。
 *
 * 展示「第X周」（教学周，基于 [SchoolCalendar] 与当前选中日期 [clickDate]）与「回到今天」按钮，
 * 不展示课表里的「整学期」「关联人」。该 Header 固定在顶部，不参与日历的嵌套滚动。
 *
 * @param clickDate 日历当前选中的日期（随翻页变化）。
 * @param onBack 返回上一页。
 * @param onBackToday 跳回今天（清除日历翻页）。
 * @param onSettings 打开邮子清单设置页。
 */
@Composable
fun ScheduleWeekHeader(
  clickDate: Date,
  onBack: () -> Unit,
  onBackToday: () -> Unit,
  onSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalAppColors.current
  val today = TodayNoEffect
  val firstMonDay = remember { SchoolCalendar.getFirstMonDay() }

  val weekText = remember(clickDate, firstMonDay) {
    val week = firstMonDay?.let {
      val diff = it.daysUntil(clickDate)
      if (diff >= 0) diff / 7 + 1 else diff / 7
    }
    if (week != null && week in 1..25) {
      "第${Num2CN.number2ChineseNumber(week)}周"
    } else {
      // 未拉过课表 / 假期等，退化成月日
      "${clickDate.monthNumber}月${clickDate.dayOfMonth}日"
    }
  }
  val isCurrentWeek = clickDate.weekBeginDate == today.weekBeginDate
  val subtitle = if (isCurrentWeek) {
    "本周"
  } else {
    "${clickDate.dayOfWeek.toChinese()} ${clickDate.monthNumber}月${clickDate.dayOfMonth}日"
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(colors.bottomBg)
      .statusBarsPadding()
      .height(64.dp)
      .padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onBack) {
      Icon(
        painter = painterResource(ConfigRes.configIcBack()),
        contentDescription = "返回",
        tint = colors.tvLv1,
      )
    }
    Column(
      modifier = Modifier.padding(start = 4.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = weekText,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = colors.tvLv1,
      )
      Text(
        text = subtitle,
        fontSize = 12.sp,
        color = colors.tvLv2.copy(alpha = 0.6f),
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    // 系统日历导出继续隔离在专用适配层；Header 不直接解释 Schedule v2 的四态时间。

    AnimatedVisibility(
      visible = !isCurrentWeek,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      Box(
        modifier = Modifier
          .padding(end = 4.dp)
          .clip(RoundedCornerShape(50))
          .background(
            Brush.horizontalGradient(
              listOf(colors.positive, MaterialTheme.colors.secondary),
            ),
          )
          .clickableNoIndicator(onClick = onBackToday)
          .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "回到今天",
          fontSize = 13.sp,
          color = MaterialTheme.colors.onPrimary,
        )
      }
    }
    IconButton(onClick = onSettings) {
      Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = "设置",
        tint = colors.tvLv1,
      )
    }
  }
}
