package com.cyxbs.pages.course.view.decoration.impl

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.zIndex
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.add
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.extensions.toastLong
import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.decoration.impl.CreateItemPageDecoration.Companion.MIN_MINUTE_INTERVAL
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.course.view.item.impl.CourseCreateItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseCreateItemFactory
import com.cyxbs.pages.course.view.item.modifier.BeginFinalTimeShowModifier
import com.cyxbs.pages.course.view.item.modifier.LayoutItemModifier
import com.cyxbs.pages.course.view.item.touch.LongPressCreateItem
import com.cyxbs.pages.course.view.item.touch.LongPressCreateItemCompose
import com.cyxbs.pages.course.view.page.LocalCoursePageContext
import com.cyxbs.pages.course.view.timeline.data.MutableTimelineData
import com.cyxbs.pages.schedule.api.ScheduleOccurrenceTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.minutes

/**
 * 长按滑动创建 item
 *
 * @author 985892345
 * @date 2025/5/17
 */
@Stable
class CreateItemPageDecoration(
  val courseFrame: AbstractCourseFrame,
  // 根据不同平台对 item 进行定制化操作
  val platformItemFactory: PlatformCourseCreateItemFactory,
) : CoursePageDecoration<CourseCreateItem>() {

  companion object {
    const val MIN_MINUTE_INTERVAL = 30 // 最小分钟间隔
  }

  @Composable
  override fun CoursePageContent() {
    super.CoursePageContent()
    LongPressCreateCoursePageWrapper(this)
  }

  /** 删除当前页所有尚未保存的长按创建草稿；调用方应使用 Compose 生命周期内的协程。 */
  suspend fun cancelAllTouchedItem() {
    supervisorScope {
      itemHierarchy.getAllWhatTime().forEach {
        launch { (it as CreateScheduleTouchItemWhatTime).cancel() }
      }
    }
    itemHierarchy.reset(emptyList())
  }

  /** 页面重建时直接丢弃内存草稿，不触发仓库或网络操作。 */
  fun resetTouchedItem() {
    itemHierarchy.reset(emptyList())
  }

  /**
   * 将课表页码和星期换算为 Schedule 的本地时间段草稿。
   *
   * 返回 null 表示学期起点尚未就绪；该方法只做日期换算，不创建 Affair 或 Schedule 记录。
   */
  internal fun createInitialTiming(
    whatTime: CreateScheduleTouchItemWhatTime,
  ): ScheduleOccurrenceTiming.Timed? {
    val weekNum = courseFrame.getWeekNumByPage(whatTime.now.value.page) ?: return null
    val date = courseFrame.beginDate.value
      ?.plusWeeks(weekNum - 1)
      ?.weekBeginDate
      ?.plusDays(whatTime.now.value.dayOfWeek.ordinal)
      ?: return null
    return ScheduleOccurrenceTiming.Timed(
      start = MinuteTimeDate(date, whatTime.beginTime.hour, whatTime.beginTime.minute),
      durationMinutes = (whatTime.finalTime - whatTime.beginTime).inWholeMinutes.toInt(),
      timeZoneId = TimeZone.currentSystemDefault().id,
    )
  }
}

@Composable
private fun LongPressCreateCoursePageWrapper(decoration: CreateItemPageDecoration) {
  val coursePage = decoration.coursePage
  val courseFrame = AbstractCourseFrame.current
  courseFrame.beginDate.collectAsState().value ?: return
  courseFrame.getWeekNumByPage(coursePage.page) ?: return // 仅在有周数的页面才允许创建事务
  val coroutineScope = rememberCoroutineScope()
  LongPressCreateItemCompose(
    modifier = Modifier.fillMaxSize().zIndex(-999F), // 在最底层接收触摸事件
    onCreate = { beginPosition, size ->
      // 倒计时结束，添加 item 展示
      var initTime = coursePage.timeline.calculateMinuteTime(coursePage, beginPosition.y)
      if (initTime.minute % 10 != 0) {
        // 落点取整 10 分钟
        initTime = initTime.plusMinutes((initTime.minute % 10).let { if (it < 5) -it else 10 - it })
      }
      val touchingItem = TouchingItem(
        viewModel = decoration,
        page = coursePage.page,
        dayOfWeek = coursePage.timeline.beginDayOfWeek.add((beginPosition.x / (size.width / 7)).toInt()),
        initMinuteTime = initTime,
        coursePage = coursePage,
        initPosition = beginPosition,
      )
      decoration.itemHierarchy.add(
        CreateScheduleTouchItemWhatTime(
          viewModel = decoration,
          item = touchingItem
        )
      )
      touchingItem
    },
    onTap = { position, size ->
      if (!decoration.itemHierarchy.isEmpty()) {
        // 手指轻击时清理已有的 item
        coroutineScope.launch {
          decoration.cancelAllTouchedItem()
        }
      } else {
        // 手指轻击时如果不存在已有的 item 时，则添加一个
        // 倒计时结束，添加 item 展示
        var initTime = coursePage.timeline.calculateMinuteTime(coursePage, position.y)
        initTime = initTime.minusMinutes(initTime.minute) // 取整点
        val touchPosition = Offset(0F, coursePage.timeline.calculateWeightRatio(initTime.plusHours(1)) * size.height)
        val touchingItem = TouchingItem(
          viewModel = decoration,
          page = coursePage.page,
          dayOfWeek = coursePage.timeline.beginDayOfWeek.add((position.x / (size.width / 7)).toInt()),
          initMinuteTime = initTime,
          coursePage = coursePage,
          initPosition = position,
        )
        decoration.itemHierarchy.add(
          CreateScheduleTouchItemWhatTime(
            viewModel = decoration,
            item = touchingItem
          )
        )
        touchingItem.touchPosition = touchPosition
        touchingItem.onMoveEnd() // 手动 mock 调用 onMoveEnd
        // 轻击时只会生成长度为 1 小时的事务，弹个 toast 提示用户需要长按拖动生成更长的事务
        val count = defaultSettings.getInt("轻击生成事务的次数", 0)
        defaultSettings.putInt("轻击生成事务的次数", count + 1)
        if (count <= 4 && count % 2 == 0 || count % 8 == 0) {
          toastLong("长按空白处再上下拖动也可添加事务哦~")
        }
      }
    }
  )
}

internal data class CreateScheduleTouchItemWhatTime(
  val viewModel: CreateItemPageDecoration,
  val item: TouchItem,
) : ItemHierarchyWhatTime<CourseCreateItem>() {

  override val now: MutableStateFlow<CourseItemWhatTime.Fixed>
    get() = item.now

  override var itemState: CourseItemState? = null
    set(value) {
      field = value
      if (value != null) {
        item.initCourseItemState(value)
      }
    }

  override fun createItem(coroutineScope: CoroutineScope): CourseCreateItem {
    return CourseCreateItem(
      whatTime = this,
      coroutineScope = coroutineScope,
      decoration = viewModel,
      platformItemFactory = viewModel.platformItemFactory,
    )
  }

  suspend fun cancel() {
    val itemState = itemState ?: return
    try {
      animate(
        initialValue = 1F,
        targetValue = 0F,
        animationSpec = tween(durationMillis = 200),
      ) { value, _ ->
        itemState.alphaState.value = value
      }
    } finally {
      itemState.alphaState.value = 0F
      viewModel.itemHierarchy.remove(this@CreateScheduleTouchItemWhatTime)
    }
  }
}

// 创建的 item。分为两个阶段，一个是触摸阶段，另一个是手指抬起后的等待添加的阶段
internal interface TouchItem {
  val now: MutableStateFlow<CourseItemWhatTime.Fixed>

  fun initCourseItemState(itemState: CourseItemState)
}

internal class TouchingItem(
  val viewModel: CreateItemPageDecoration,
  val page: Int,
  val dayOfWeek: DayOfWeek,
  val initMinuteTime: MinuteTime,
  val coursePage: LocalCoursePageContext,
  override val initPosition: Offset,
) : TouchItem, LongPressCreateItem {

  override val now: MutableStateFlow<CourseItemWhatTime.Fixed> = MutableStateFlow(
    CourseItemWhatTime.Fixed(
      page = page,
      dayOfWeek = dayOfWeek,
      beginTime = initMinuteTime,
      finalTime = coursePage.timeline.calculateMinuteTime(coursePage, initPosition.y),
    )
  )

  private var itemState: CourseItemState? = null
  private var layoutAnimUnlock: Runnable? = null

  private var itemStateIsNullWhenOnMoveEnd = false

  override fun initCourseItemState(itemState: CourseItemState) {
    this.itemState = itemState
    BeginFinalTimeShowModifier.showLock.get(itemState).lock() // 默认显示开始结束时间
    layoutAnimUnlock = LayoutItemModifier.animLock.get(itemState).lock()
    if (itemStateIsNullWhenOnMoveEnd) {
      // 在 onMoveEnd 回调时 itemState 为 null，所以这里再回调一次
      onMoveEnd()
    }
  }

  override var touchPosition: Offset = initPosition
    set(value) {
      field = value
      val touchMinuteTime = coursePage.timeline.calculateMinuteTime(coursePage, value.y)
      now.value = now.value.copy(
        beginTime = minOf(initMinuteTime, touchMinuteTime),
        finalTime = maxOf(initMinuteTime, touchMinuteTime),
      )
      tryExpandTimeline()
    }

  private val clickLock = mutableListOf<MutableTimelineData.ClickLock>()

  override fun onMoveEnd() {
    val itemState = itemState
    if (itemState == null) {
      itemStateIsNullWhenOnMoveEnd = true
      return
    }
    clickLock.forEach { it.unlock() }
    clickLock.clear()
    layoutAnimUnlock?.run()
    val coroutineScope = itemState.item.coroutineScope
    val whatTime = itemState.item.whatTime as CreateScheduleTouchItemWhatTime
    if (now.value.finalTime - now.value.beginTime < MIN_MINUTE_INTERVAL.minutes) {
      // 暂定小于 MIN_MINUTE_INTERVAL 分钟的事务不支持
      toast("不支持创建小于 $MIN_MINUTE_INTERVAL 分钟的事务")
      coroutineScope.launch {
        whatTime.cancel()
      }
    } else {
      val initialTiming = viewModel.createInitialTiming(whatTime)
      if (initialTiming == null) {
        toast("事务时间初始化失败")
        coroutineScope.launch { whatTime.cancel() }
      } else {
        // 保存前只把时间草稿交给临时 Item，真正的 Schedule 由点击后的编辑弹窗创建。
        (itemState.item as CourseCreateItem).setInitialTiming(initialTiming)
      }
    }
  }

  // 移动过程中判断是否需要展开时间轴折叠部分
  private fun tryExpandTimeline() {
    coursePage.timeline.data.asSequence()
      .filterIsInstance<MutableTimelineData>()
      .filter { it.state.value == MutableTimelineData.State.Collapse }
      .mapNotNull { time ->
        coursePage.scrollContext.timelineCoordinatesMap[time]?.let { coor ->
          val a1 = coor.positionInParent().y
          val a2 = a1 + coor.size.height
          val b1 = minOf(initPosition.y, touchPosition.y)
          val b2 = maxOf(initPosition.y, touchPosition.y)
          // 中间的折叠时间存在相交区域即可展开
          a1 < b2 && a2 > b1
        }?.let { if (it) time else null }
      }.forEach {
        it.click()
        clickLock.add(it.lockClick()) // 展开后就给点击上锁，直到结束解锁后才允许点击
      }
  }
}
