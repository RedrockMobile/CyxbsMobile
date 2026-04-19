package com.cyxbs.pages.course.view.decoration.impl

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.zIndex
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.config.time.add
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.IAffairService2
import com.cyxbs.pages.course.view.AbstractCourseFrame
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.decoration.impl.CreateAffairPageDecoration.Companion.MIN_MINUTE_INTERVAL
import com.cyxbs.pages.course.view.item.CourseItemState
import com.cyxbs.pages.course.view.item.CourseItemWhatTime
import com.cyxbs.pages.course.view.item.ItemHierarchyWhatTime
import com.cyxbs.pages.course.view.item.impl.CourseCreateAffairItem
import com.cyxbs.pages.course.view.item.impl.PlatformCourseCreateAffairItemFactory
import com.cyxbs.pages.course.view.item.modifier.BeginFinalTimeShowModifier
import com.cyxbs.pages.course.view.item.modifier.LayoutItemModifier
import com.cyxbs.pages.course.view.item.touch.LongPressCreateItem
import com.cyxbs.pages.course.view.item.touch.LongPressCreateItemCompose
import com.cyxbs.pages.course.view.page.LocalCoursePageContext
import com.cyxbs.pages.course.view.timeline.data.MutableTimelineData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.DayOfWeek
import kotlin.time.Duration.Companion.minutes

/**
 * 长按滑动创建 item
 *
 * @author 985892345
 * @date 2025/5/17
 */
@Stable
class CreateAffairPageDecoration(
  val courseFrame: AbstractCourseFrame,
  // 根据不同平台对 item 进行定制化操作
  val platformItemFactory: PlatformCourseCreateAffairItemFactory,
) : CoursePageDecoration<CourseCreateAffairItem>() {

  companion object {
    const val MIN_MINUTE_INTERVAL = 30 // 最小分钟间隔
  }

  private var mockIdModel = MutableStateFlow<AffairIdModel?>(null)
  private val whatTimeByDateModel = hashMapOf<AffairDateModel, CreateAffairTouchItemWhatTime>()

  @Composable
  override fun CoursePageContent() {
    super.CoursePageContent()
    LongPressCreateCoursePageWrapper(this)
    LaunchedEffect(Unit) {
      observeTouchItem()
    }
  }

  // 删除所有触摸后的 item
  // 注意该协程需要使用 Compose 函数内的作用域
  suspend fun cancelAllTouchedItem() {
    mockIdModel.value = null
    whatTimeByDateModel.clear()
    supervisorScope {
      itemHierarchy.getAllWhatTime().forEach {
        launch { (it as CreateAffairTouchItemWhatTime).cancel() }
      }
    }
    itemHierarchy.reset(emptyList())
  }

  fun resetTouchedItem() {
    mockIdModel.value = null
    whatTimeByDateModel.clear()
    itemHierarchy.reset(emptyList())
  }

  internal suspend fun addTouchedItem(
    whatTime: CreateAffairTouchItemWhatTime,
  ): AffairDateModelEditor? {
    val timePair = MinuteTimePair(whatTime.beginTime, whatTime.finalTime)
    val idModel = mockIdModel.value ?: run {
      IAffairService2::class.impl().observeAffairGroupModel().value!!.createAffairIdModel(
        title = "",
        content = "",
      ).also { mockIdModel.value = it }
    }
    val idModelEditor = idModel.createEditorSuspend()
    var timeModelEditor = idModelEditor.whatTimeDate.keys.firstOrNull { it.timePair == timePair }
    if (timeModelEditor == null) {
      timeModelEditor = idModelEditor.add(timePair) ?: return null
    }
    val weekNum = courseFrame.getWeekNumByPage(whatTime.now.value.page) ?: return null
    val date = courseFrame.beginDate.value
      ?.plusWeeks(weekNum - 1)
      ?.weekBeginDate
      ?.plusDays(whatTime.now.value.dayOfWeek.ordinal)
      ?: return null
    val dateModelEditor = timeModelEditor.add(date) ?: return null
    whatTimeByDateModel[dateModelEditor.dateModel] = whatTime // 保存到已添加列表中，防止 observeTouchItem() 回调重复添加
    idModelEditor.commit(needUpload = false, needAdd = false)
    return dateModelEditor
  }

  private suspend fun observeTouchItem() {
    mockIdModel.collectLatest { idModel ->
      if (idModel == null) return@collectLatest
      idModel.whatTimeDate.value.forEach { (whatTimeModel, dateModels) ->
        dateModels.forEach { dateModel ->
          if (whatTimeByDateModel.containsKey(dateModel)) return@forEach
          val touchedItem = TouchedItem(
            viewModel = this@CreateAffairPageDecoration,
            dateModel = dateModel,
          )
          val whatTime = CreateAffairTouchItemWhatTime(
            viewModel = this@CreateAffairPageDecoration,
            item = touchedItem
          )
          whatTimeByDateModel[dateModel] = whatTime
          itemHierarchy.add(whatTime)
        }
      }
      supervisorScope {
        launch {
          idModel.addedDateModel.collect { dateModel ->
            if (whatTimeByDateModel.containsKey(dateModel)) return@collect
            val touchedItem = TouchedItem(
              viewModel = this@CreateAffairPageDecoration,
              dateModel = dateModel,
            )
            val whatTime = CreateAffairTouchItemWhatTime(
              viewModel = this@CreateAffairPageDecoration,
              item = touchedItem
            )
            whatTimeByDateModel[dateModel] = whatTime
            itemHierarchy.add(whatTime)
          }
        }
        launch {
          idModel.removedDateModel.collect { dateModel ->
            whatTimeByDateModel[dateModel]?.let {
              itemHierarchy.remove(it)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LongPressCreateCoursePageWrapper(viewModel: CreateAffairPageDecoration) {
  val coursePage = viewModel.coursePage
  val courseFrame = AbstractCourseFrame.current
  courseFrame.beginDate.collectAsState().value ?: return
  courseFrame.getWeekNumByPage(coursePage.page) ?: return // 仅在有周数的页面才允许创建事务
  val coroutineScope = rememberCoroutineScope()
  LongPressCreateItemCompose(
    modifier = Modifier.fillMaxSize().zIndex(-999F), // 在最底层接收触摸事件
    onCreate = { beginPosition, size ->
      // 倒计时结束，添加 item 展示
      var initTime = coursePage.timeline.calculateMinuteTime(coursePage, beginPosition.y)
      var initPosition = beginPosition
      if (initTime.minute % 10 != 0) {
        // 落点取整 10 分钟
        initTime = initTime.plusMinutes((initTime.minute % 10).let { if (it < 5) -it else 10 - it })
        initPosition =
          initPosition.copy(y = coursePage.timeline.calculateWeightRatio(initTime) * size.height)
      }
      val touchingItem = TouchingItem(
        viewModel = viewModel,
        page = coursePage.page,
        dayOfWeek = coursePage.timeline.beginDayOfWeek.add((initPosition.x / (size.width / 7)).toInt()),
        initMinuteTime = initTime,
        coursePage = coursePage,
        initPosition = beginPosition,
      )
      viewModel.itemHierarchy.add(
        CreateAffairTouchItemWhatTime(
          viewModel = viewModel,
          item = touchingItem
        )
      )
      touchingItem
    },
    onTap = {
      // 手指轻击时清理已有的 item
      coroutineScope.launch {
        viewModel.cancelAllTouchedItem()
      }
    }
  )
}

internal data class CreateAffairTouchItemWhatTime(
  val viewModel: CreateAffairPageDecoration,
  val item: TouchItem,
) : ItemHierarchyWhatTime<CourseCreateAffairItem>() {

  override val now: MutableStateFlow<CourseItemWhatTime.Fixed>
    get() = item.now

  override var itemState: CourseItemState? = null
    set(value) {
      field = value
      if (value != null) {
        item.initCourseItemState(value)
      }
    }

  override fun createItem(coroutineScope: CoroutineScope): CourseCreateAffairItem {
    return CourseCreateAffairItem(
      whatTime = this,
      coroutineScope = coroutineScope,
      viewModel = viewModel,
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
      viewModel.itemHierarchy.remove(this@CreateAffairTouchItemWhatTime)
    }
  }
}

// 创建的 item。分为两个阶段，一个是触摸阶段，另一个是手指抬起后的等待添加的阶段
internal interface TouchItem {
  val now: MutableStateFlow<CourseItemWhatTime.Fixed>

  fun initCourseItemState(itemState: CourseItemState)
}

internal class TouchingItem(
  val viewModel: CreateAffairPageDecoration,
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

  override fun initCourseItemState(itemState: CourseItemState) {
    this.itemState = itemState
    BeginFinalTimeShowModifier.showLock.get(itemState).lock() // 默认显示开始结束时间
    layoutAnimUnlock = LayoutItemModifier.animLock.get(itemState).lock()
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

  override fun onMoveEnd(coroutineScope: CoroutineScope) {
    clickLock.forEach { it.unlock() }
    clickLock.clear()
    layoutAnimUnlock?.run()
    val whatTime = itemState?.item?.whatTime as CreateAffairTouchItemWhatTime
    if (now.value.finalTime - now.value.beginTime < MIN_MINUTE_INTERVAL.minutes) {
      // 暂定小于 MIN_MINUTE_INTERVAL 分钟的事务不支持
      toast("不支持创建小于 $MIN_MINUTE_INTERVAL 分钟的事务")
      coroutineScope.launch {
        whatTime.cancel()
      }
    } else {
      coroutineScope.launch {
        val dateModelEditor = viewModel.addTouchedItem(whatTime)
        if (dateModelEditor == null) {
          toast("AffairDateModel 创建失败")
          whatTime.cancel()
        } else {
          // 创建成功后则设置 dateModel
          (itemState?.item as CourseCreateAffairItem).setDateModel(dateModelEditor.dateModel)
        }
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

internal class TouchedItem(
  val viewModel: CreateAffairPageDecoration,
  val dateModel: AffairDateModel,
) : TouchItem {

  override val now: MutableStateFlow<CourseItemWhatTime.Fixed> = MutableStateFlow(
    CourseItemWhatTime.Fixed(
      page = viewModel.courseFrame.beginDate.value.let {
        if (it == null) -1
        else viewModel.courseFrame.getPage(dateModel.date.value) ?: -1
      },
      dayOfWeek = dateModel.date.value.dayOfWeek,
      beginTime = dateModel.whatTime.value.timePair.value.first,
      finalTime = dateModel.whatTime.value.timePair.value.second,
    )
  )

  override fun initCourseItemState(itemState: CourseItemState) {
    BeginFinalTimeShowModifier.showLock.get(itemState).lock() // 默认显示开始结束时间
    (itemState.item as CourseCreateAffairItem).setDateModel(dateModel) // 设置 dateModel
  }
}
