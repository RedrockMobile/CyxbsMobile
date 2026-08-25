package com.cyxbs.pages.course.view.item.impl

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.pages.course.view.decoration.impl.CreateScheduleTouchItemWhatTime
import com.cyxbs.pages.course.view.decoration.impl.CreateItemPageDecoration
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.CourseShowRange
import com.cyxbs.pages.course.view.item.createCourseDefaultModifierList
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * 长按创建 Schedule 前的内存占位 Item。
 *
 * 手指抬起后只保存初始时间段；标题等业务字段由 Schedule 编辑弹窗持有，确认保存前不写数据库。
 */
class CourseCreateItem(
  whatTime: CourseItemWhatTime,
  coroutineScope: CoroutineScope,
  val decoration: CreateItemPageDecoration,
  // 根据不同平台对 item 进行定制化操作
  platformItemFactory: PlatformCourseCreateItemFactory,
) : CourseItem(whatTime, coroutineScope) {

  // 下沉到各平台决定点击后使用何种弹窗宿主。
  private val platform = platformItemFactory.create(this)

  private val mutableInitialTiming = MutableStateFlow<ScheduleOccurrenceTiming.Timed?>(null)
  val initialTimingFlow = mutableInitialTiming.asStateFlow()

  /** 设置长按计算出的初始时间；同一个占位 Item 只允许初始化一次。 */
  fun setInitialTiming(timing: ScheduleOccurrenceTiming.Timed) {
    require(mutableInitialTiming.value == null) { "initialTiming 不能重复设置" }
    mutableInitialTiming.value = timing
  }

  /** Schedule 本地创建成功后移除占位 Item；真实日程随后由 Schedule Decoration 观察并展示。 */
  fun removeDraft() {
    coroutineScope.launch {
      (whatTime as? CreateScheduleTouchItemWhatTime)?.cancel()
    }
  }

  @Composable
  override fun CourseItemContent() {
    platform.CourseItemContentWrapper {
      Content(onClick = it)
    }
  }
}

@Composable
private fun CourseCreateItem.Content(
  onClick: (MinuteTimePair) -> Unit,
) {
  if (itemState.realShowRange.isEmpty()) return
  val modifierList = remember {
    createCourseDefaultModifierList()
  }
  Box(
    modifier = Modifier.plusDsl {
      modifierList.forEach {
        then(it.createModifier())
      }
    }.background(0xFFE9EDF2.dark(0xFF202223)).zIndex(1F), // 默认就比其他布局高
  ) {
    val textColor = LocalAppColors.current.tvLv2
    val itemRange = MinuteTimePair(
      itemState.item.whatTime.now.collectAsState().value.beginTime,
      itemState.item.whatTime.now.collectAsState().value.finalTime
    )
    itemState.realShowRange.fastForEach { range ->
      CourseShowRange(
        range = range,
        itemRange = itemRange,
        timeline = itemState.item.coursePage.timeline,
        coverTipColor = if (itemState.overlap?.coveredItemList?.isNotEmpty() == true) textColor else Color.Transparent,
        enableAnim = false,
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = it.clickableNoIndicator {
            onClick.invoke(range)
          },
        ) {
          Image(
            painter = painterResource(ConfigRes.configIcCircleAdd()),
            contentDescription = "点击添加事务"
          )
        }
      }
    }
  }
}


/** 下沉到具体平台的长按创建 Item 配置。 */
interface PlatformCourseCreateItemFactory {
  fun create(item: CourseCreateItem): PlatformCourseCreateItem
}

interface PlatformCourseCreateItem {
  @Composable
  fun CourseItemContentWrapper(content: @Composable (onClick: (MinuteTimePair) -> Unit) -> Unit)
}
