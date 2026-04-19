package com.cyxbs.pages.course.dialog.item.affair

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.wheel.WheelSelectBackground
import com.cyxbs.components.view.wheel.WheelSelectCompose
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.course.view.decoration.impl.CreateAffairPageDecoration.Companion.MIN_MINUTE_INTERVAL
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.collectLatest

/**
 * .
 *
 * @author 985892345
 * @date 2026/2/19
 */
@Composable
fun AffairEditTimePairCompose(
  whatTimeModelEditor: AffairWhatTimeModelEditor,
) {
  val sHourAnimatable = remember { Animatable(whatTimeModelEditor.timePair.first.hour.toFloat()) }
  val sMinuteAnimatable =
    remember { Animatable(whatTimeModelEditor.timePair.first.minute.toFloat()) }
  val eHourAnimatable = remember { Animatable(whatTimeModelEditor.timePair.second.hour.toFloat()) }
  val eMinuteAnimatable =
    remember { Animatable(whatTimeModelEditor.timePair.second.minute.toFloat()) }
  Row(
    modifier = Modifier.fillMaxSize(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    WheelHourMinute(
      hourAnimatable = sHourAnimatable,
      minuteAnimatable = sMinuteAnimatable,
    )
    Text(
      text = "-",
      fontSize = 22.sp,
      color = LocalAppColors.current.tvLv2,
      modifier = Modifier.padding(8.dp)
    )
    WheelHourMinute(
      hourAnimatable = eHourAnimatable,
      minuteAnimatable = eMinuteAnimatable,
    )
  }
  LaunchedEffect(Unit) {
    snapshotFlow {
      MinuteTime(sHourAnimatable.targetValue.toInt(), sMinuteAnimatable.targetValue.toInt())
    }.collectLatest {
      sMinuteAnimatable.updateBounds(upperBound = if (it.hour == 23) 59F - MIN_MINUTE_INTERVAL else 59F)
      val min = it.plusMinutes(MIN_MINUTE_INTERVAL)
      eHourAnimatable.updateBounds(lowerBound = min.hour.toFloat())
      snapshotFlow { eHourAnimatable.targetValue }.collect { eHour ->
        eMinuteAnimatable.updateBounds(
          lowerBound = if (eHour.toInt() == min.hour) min.minute.toFloat()
          else 0F
        )
      }
    }
  }
  LaunchedEffect(Unit) {
    snapshotFlow {
      MinuteTimePair(
        MinuteTime(
          sHourAnimatable.value.toInt(),
          sMinuteAnimatable.value.toInt(),
        ),
        MinuteTime(
          eHourAnimatable.value.toInt(),
          eMinuteAnimatable.value.toInt()
        )
      )
    }.collect {
      val error = whatTimeModelEditor.setTimePair(it)
      if (error != null) {
        toast(error)
        sHourAnimatable.snapTo(whatTimeModelEditor.timePair.first.hour.toFloat())
        sMinuteAnimatable.snapTo(whatTimeModelEditor.timePair.first.minute.toFloat())
        eHourAnimatable.snapTo(whatTimeModelEditor.timePair.second.hour.toFloat())
        eMinuteAnimatable.snapTo(whatTimeModelEditor.timePair.second.minute.toFloat())
      }
    }
  }
}

@Composable
private fun WheelHourMinute(
  hourAnimatable: Animatable<Float, AnimationVector1D>,
  minuteAnimatable: Animatable<Float, AnimationVector1D>,
) {
  val textStyle = TextStyle(fontSize = 22.sp, color = LocalAppColors.current.tvLv2)
  val width = 60.dp
  val height = 130.dp
  Row(verticalAlignment = Alignment.CenterVertically) {
    WheelSelectBackground(
      modifier = Modifier.size(width, height)
    ) {
      WheelSelectCompose(
        modifier = Modifier.fillMaxSize(),
        selectedLine = hourAnimatable,
        options = remember { List(24) { it.toString() }.toPersistentList() },
        textStyle = textStyle
      )
    }
    Text(
      text = ":",
      style = textStyle,
      modifier = Modifier.padding(horizontal = 4.dp)
    )
    WheelSelectBackground(
      modifier = Modifier.size(width, height)
    ) {
      WheelSelectCompose(
        modifier = Modifier.fillMaxSize(),
        selectedLine = minuteAnimatable,
        options = remember { List(60) { it.toString().padStart(2, '0') }.toPersistentList() },
        textStyle = textStyle
      )
    }
  }
}