package com.cyxbs.pages.course.view.item.impl

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.plusDsl
import com.cyxbs.pages.course.view.item.CourseItem
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.course.view.item.createCourseDefaultModifierList
import com.cyxbs.pages.course.view.item.extension.IMovableItemExtension
import cyxbsmobile.cyxbs_pages.course.view.generated.resources.Res
import cyxbsmobile.cyxbs_pages.course.view.generated.resources.view_ic_touch_affair
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * .
 *
 * @author 985892345
 * @date 2026/3/7
 */
class CourseCreateAffairItem(
  val itemWhatTime: ItemHierarchyWhatTime<CourseCreateAffairItem>,
  coroutineScope: CoroutineScope,
  platformItemFactory: PlatformCourseCreateAffairItemFactory,
) : CourseItem(itemWhatTime, coroutineScope) {

  init {
    extensions.add(CourseCreateAffairMovableItemExtension())
  }

  // 下层到每个平台的课程配置
  private val platform = platformItemFactory.create(this)

  @Composable
  override fun CourseItemContent() {
    platform.CourseItemContentWrapper {
      Content(onClick = it)
    }
  }
}

@Composable
private fun CourseCreateAffairItem.Content(
  onClick: () -> Unit,
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
    }.background(0xFFE9EDF2.dark(0xFF202223))
      .clickable(onClick = onClick)
      .zIndex(1F), // 默认就比其他布局高
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painterResource(Res.drawable.view_ic_touch_affair),
      contentDescription = "点击添加事务"
    )
  }
}


// 事务支持长按移动
private class CourseCreateAffairMovableItemExtension : IMovableItemExtension {
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
    val itemWidth = size.width
    // 一小段距离都会被算成同一分钟，为了让最后修改时间后不会因此而抖动
    // 需要计算出最终 newBeginTime 真正的高度，这才是最终展示的位置
    val newBeginTimeWeightRatio = itemState.coursePage.timeline.calculateWeightRatio(newBeginTime)
    val newBeginTimeHeight = newBeginTimeWeightRatio * itemState.coursePage.layoutCoordinates.size.height
    val newBeginTimeScreenTop = itemState.coursePage.layoutCoordinates.localToScreen(Offset(0F, newBeginTimeHeight))
    return transition.value.copy(
      x = ((transition.value.x / itemWidth).roundToInt() * itemWidth).toFloat(),
      y = transition.value.y + (newBeginTimeScreenTop.y - screenTopLeft.y)
    )
  }
}

// 下层到每个平台的事务配置
interface PlatformCourseCreateAffairItemFactory {
  fun create(item: CourseCreateAffairItem): PlatformCourseCreateAffairItem
}

interface PlatformCourseCreateAffairItem {
  @Composable
  fun CourseItemContentWrapper(content: @Composable (onClick: () -> Unit) -> Unit)
}

