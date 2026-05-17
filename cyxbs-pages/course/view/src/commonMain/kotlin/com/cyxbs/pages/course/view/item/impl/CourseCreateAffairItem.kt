package com.cyxbs.pages.course.view.item.impl

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.course.view.decoration.impl.CreateAffairPageDecoration
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemTopBottomText
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.CourseShowRange
import com.cyxbs.pages.course.view.item.createCourseDefaultModifierList
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/7
 */
class CourseCreateAffairItem(
  whatTime: CourseItemWhatTime,
  coroutineScope: CoroutineScope,
  val viewModel: CreateAffairPageDecoration,
  // 根据不同平台对 item 进行定制化操作
  platformItemFactory: PlatformCourseCreateAffairItemFactory,
) : CourseItem(whatTime, coroutineScope) {

  // 下层到每个平台的课程配置
  private val platform = platformItemFactory.create(this)

  val dateModelFlow = MutableStateFlow<AffairDateModel?>(null)

  fun setDateModel(dateModel: AffairDateModel) {
    require(this.dateModelFlow.value == null) { "dateModel 不能重复设置"}
    this.dateModelFlow.value = dateModel
    extensions.add(CourseCreateAffairMovableItemExtension(this))
    combine(
      viewModel.courseFrame.beginDate.filterNotNull(),
      dateModel.whatTime.mergeFlow.flatMapLatest { it.timePair.mergeFlow },
      dateModel.date.mergeFlow
    ) { beginDate, timePair, date ->
      whatTime.now.value = CourseItemWhatTime.Fixed(
        page = viewModel.courseFrame.getPage(date) ?: -1,
        dayOfWeek = date.dayOfWeek,
        beginTime = timePair.first,
        finalTime = timePair.second,
      )
    }.launchIn(coroutineScope)
  }

  @Composable
  override fun CourseItemContent() {
    platform.CourseItemContentWrapper {
      Content(onClick = it)
    }
  }
}

@Composable
private fun CourseCreateAffairItem.Content(
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
        val idModel = dateModelFlow.collectAsState().value?.idModel
        val title = idModel?.title?.mergeFlow?.collectAsState("")?.value
        val content = idModel?.content?.mergeFlow?.collectAsState("")?.value
        if (title.isNullOrEmpty() && content.isNullOrEmpty()) {
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
        } else {
          CourseItemTopBottomText(
            modifier = it.clickableNoIndicator {
              onClick.invoke(range)
            },
            topText = title ?: "",
            bottomText = content ?: "",
            textColor = textColor,
          )
        }
      }
    }
  }
}


// 事务支持长按移动
private class CourseCreateAffairMovableItemExtension(
  val item: CourseCreateAffairItem,
) : IMovableItemExtension {
  override fun enableExpandTimelineWhenMove(itemState: CourseItemState): Boolean {
    return true
  }

  override fun getMoveDestinationOffset(
    upOrCancel: Boolean,
    itemState: CourseItemState,
    transition: MutableState<Offset>,
    screenTopLeft: Offset,
    size: IntSize,
    newBeginTime: MinuteTime
  ): Offset {
    if (item.dateModelFlow.value == null) return Offset.Zero
    val itemWidth = size.width
    // 一小段距离都会被算成同一分钟，为了让最后修改时间后不会因此而抖动
    // 需要计算出最终 newBeginTime 真正的高度，这才是最终展示的位置
    val newBeginTimeWeightRatio = itemState.coursePage.timeline.calculateWeightRatio(newBeginTime)
    val newBeginTimeHeight =
      newBeginTimeWeightRatio * itemState.coursePage.layoutCoordinates.size.height
    val newBeginTimeScreenTop =
      itemState.coursePage.layoutCoordinates.localToScreen(Offset(0F, newBeginTimeHeight))
    return transition.value.copy(
      x = ((transition.value.x / itemWidth).roundToInt() * itemWidth).toFloat(),
      y = transition.value.y + (newBeginTimeScreenTop.y - screenTopLeft.y)
    )
  }

  override suspend fun changeWhatTime(
    itemState: CourseItemState,
    newBeginTime: MinuteTime,
    newDayOfWeek: DayOfWeek
  ) {
    val dateModel = item.dateModelFlow.value ?: return
    // 修改 dateModelEditor 来触发 whatTime 的更新
    val dateModelEditor = dateModel.idModel.createEditorSuspend().findDateModelEditor(dateModel)!!
    dateModelEditor.setDate(dateModelEditor.date.weekBeginDate.plusDays(newDayOfWeek.ordinal))
    dateModelEditor.whatTimeEditor?.setTimePair(
      MinuteTimePair(
        first = newBeginTime,
        second = newBeginTime + (item.whatTime.finalTime - item.whatTime.beginTime)
      )
    )
    dateModelEditor.idModelEditor.commit(needUpload = false, needAdd = false)
  }
}

// 下层到每个平台的事务配置
interface PlatformCourseCreateAffairItemFactory {
  fun create(item: CourseCreateAffairItem): PlatformCourseCreateAffairItem
}

interface PlatformCourseCreateAffairItem {
  @Composable
  fun CourseItemContentWrapper(content: @Composable (onClick: (MinuteTimePair) -> Unit) -> Unit)
}

