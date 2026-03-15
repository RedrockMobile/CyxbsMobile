package com.cyxbs.pages.course.view.frame.decoration

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInParent
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.config.time.add
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.IAffairService2
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.frame.AbstractCourseFrame
import com.cyxbs.pages.course.view.frame.decoration.CreateAffairDecorationViewModel.Companion.MIN_MINUTE_INTERVAL
import com.cyxbs.pages.course.view.item.CourseItemHierarchy
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
import kotlinx.coroutines.delay
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
class CreateAffairDecorationViewModel(
  val courseFrame: AbstractCourseFrame,
  val touchingHierarchy: CourseItemHierarchy<CourseCreateAffairItem>,
  val touchedHierarchy: CourseItemHierarchy<CourseCreateAffairItem>,
  val platformItemFactory: PlatformCourseCreateAffairItemFactory,
) : BaseViewModel(), CoursePageDecoration {

  companion object {
    const val MIN_MINUTE_INTERVAL = 30 // 最小分钟间隔
  }

  private var mockIdModel = MutableStateFlow<AffairIdModel?>(null)

  @Composable
  override fun CoursePageContent() {
    touchingHierarchy.CoursePageItemListContent()
    touchedHierarchy.CoursePageItemListContent()
    LongPressCreateCoursePageWrapper(this)
  }

  // 删除所有触摸后的 item
  // 注意该协程需要使用 Compose 函数内的作用域
  suspend fun cancelAllTouchedItem() {
    mockIdModel.value = null
    supervisorScope {
      touchedHierarchy.getAllWhatTime().forEach {
        launch { (it as CreateAffairTouchedItemWhatTime).cancel() }
      }
    }
    touchedHierarchy.reset(emptyList())
  }

  fun resetTouchedItem() {
    mockIdModel.value = null
    touchedHierarchy.reset(emptyList())
  }

  suspend fun addTouchedItem(
    whatTimeFixed: CourseItemWhatTime.Fixed,
    page: Int,
  ): Boolean {
    val timePair = MinuteTimePair(whatTimeFixed.beginTime, whatTimeFixed.finalTime)
    val idModel = mockIdModel.value ?: run {
      IAffairService2::class.impl().observeAffairGroupModel().value!!.createAffairIdModel(
        title = "",
        content = "",
      ).also { mockIdModel.value = it }
    }
    val idModelEditor = idModel.createEditorSuspend()
    var timeModelEditor = idModelEditor.whatTimeDate.keys.firstOrNull { it.timePair == timePair }
    if (timeModelEditor == null) {
      timeModelEditor = idModelEditor.add(timePair) ?: return false
    }
    val weekNum = courseFrame.getWeekNumByPage(page) ?: return false
    val date = courseFrame.beginDate.value
      ?.plusWeeks(weekNum - 1)
      ?.weekBeginDate
      ?.plusDays(whatTimeFixed.dayOfWeek.ordinal)
      ?: return false
    timeModelEditor.add(date) ?: return false
    idModelEditor.commit(needUpload = false, needAdd = false)
    return true
  }

  init {
    viewModelScope.launch {
      mockIdModel.collectLatest { idModel ->
        if (idModel == null) return@collectLatest
        val whatTimeByDateModel = hashMapOf<AffairDateModel, CreateAffairTouchedItemWhatTime>()
        idModel.whatTimeDate.value.forEach { (whatTimeModel, dateModels) ->
          dateModels.forEach { dateModel ->
            val whatTime = CreateAffairTouchedItemWhatTime(
              viewModel = this@CreateAffairDecorationViewModel,
              dateModel = dateModel
            )
            whatTimeByDateModel[dateModel] = whatTime
            touchedHierarchy.add(whatTime)
          }
        }
        supervisorScope {
          launch {
            idModel.addedDateModel.collect { dateModel ->
              val whatTime = CreateAffairTouchedItemWhatTime(
                viewModel = this@CreateAffairDecorationViewModel,
                dateModel = dateModel
              )
              whatTimeByDateModel[dateModel] = whatTime
              touchedHierarchy.add(whatTime)
            }
          }
          launch {
            idModel.removedDateModel.collect { dateModel ->
              whatTimeByDateModel[dateModel]?.let {
                touchedHierarchy.remove(it)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LongPressCreateCoursePageWrapper(viewModel: CreateAffairDecorationViewModel) {
  val coursePage = viewModel.coursePage
  val courseFrame = AbstractCourseFrame.current
  courseFrame.beginDate.collectAsState().value ?: return
  courseFrame.getWeekNumByPage(coursePage.page) ?: return // 仅在有周数的页面才允许创建事务
  val coroutineScope = rememberCoroutineScope()
  LongPressCreateItemCompose(
    onCreate = { beginPosition, size ->
      // 倒计时结束，添加 item 展示
      var initTime = coursePage.timeline.calculateMinuteTime(coursePage, beginPosition.y)
      var initPosition = beginPosition
      if (initTime.minute % 10 != 0) {
        // 落点取整 10 分钟
        initTime = initTime.plusMinutes((initTime.minute % 10).let { if (it < 5) -it else 10 - it })
        initPosition = initPosition.copy(y = coursePage.timeline.calculateWeightRatio(initTime) * size.height)
      }
      CreateAffairTouchingItemWhatTime(
        viewModel = viewModel,
        page = coursePage.page,
        dayOfWeek = coursePage.timeline.beginDayOfWeek.add((initPosition.x / (size.width / 7)).toInt()),
        initMinuteTime = initTime,
        coursePage = coursePage,
        initPosition = beginPosition,
      ).also {
        viewModel.touchingHierarchy.add(it)
      }
    },
    onTap = {
      // 手指轻击时清理已有的 item
      coroutineScope.launch {
        viewModel.cancelAllTouchedItem()
      }
    }
  )
}

private data class CreateAffairTouchingItemWhatTime(
  val viewModel: CreateAffairDecorationViewModel,
  val page: Int,
  val dayOfWeek: DayOfWeek,
  val initMinuteTime: MinuteTime,
  val coursePage: LocalCoursePageContext,
  override val initPosition: Offset,
) : ItemHierarchyWhatTime<CourseCreateAffairItem>(), LongPressCreateItem {

  override val now: MutableStateFlow<CourseItemWhatTime.Fixed> = MutableStateFlow(
    CourseItemWhatTime.Fixed(
      page = page,
      dayOfWeek = dayOfWeek,
      beginTime = initMinuteTime,
      finalTime = coursePage.timeline.calculateMinuteTime(coursePage, initPosition.y),
    )
  )

  private var layoutAnimUnlock: Runnable? = null

  override var itemState: CourseItemState? = null
    set(value) {
      field = value
      if (value != null) {
        // itemState 初始化
        BeginFinalTimeShowModifier.showLock.get(value).lock() // 默认显示开始结束时间
        layoutAnimUnlock = LayoutItemModifier.animLock.get(value).lock()
      }
    }

  override fun createItem(coroutineScope: CoroutineScope): CourseCreateAffairItem {
    return CourseCreateAffairItem(
      whatTime = this,
      coroutineScope = coroutineScope,
      viewModel = viewModel,
      dateModel = null,
      platformItemFactory = viewModel.platformItemFactory,
    )
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
    if (now.value.finalTime - now.value.beginTime < MIN_MINUTE_INTERVAL.minutes) {
      // 暂定小于 MIN_MINUTE_INTERVAL 分钟的事务不支持
      toast("不支持创建小于 $MIN_MINUTE_INTERVAL 分钟的事务")
      coroutineScope.launch {
        cancel()
      }
    } else {
      coroutineScope.launch {
        if (!viewModel.addTouchedItem(now.value, page)) {
          toast("AffairDateModel 创建失败")
          cancel()
        } else {
          // 创建成功则从 touchingHierarchy 中移除 item，后续会展示在 touchedHierarchy 中
          viewModel.touchingHierarchy.remove(this@CreateAffairTouchingItemWhatTime)
        }
      }
    }
  }

  private suspend fun cancel() {
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
      viewModel.touchingHierarchy.remove(this@CreateAffairTouchingItemWhatTime)
    }
  }

  // 减少调用频率
  private var isWaitExpandTimeline = false

  // 移动过程中判断是否需要展开时间轴折叠部分
  private fun tryExpandTimeline() {
    if (isWaitExpandTimeline) return
    isWaitExpandTimeline = true
    viewModel.viewModelScope.launch {
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
      delay(300)
      isWaitExpandTimeline = false
    }
  }
}

private data class CreateAffairTouchedItemWhatTime(
  val viewModel: CreateAffairDecorationViewModel,
  val dateModel: AffairDateModel,
) : ItemHierarchyWhatTime<CourseCreateAffairItem>() {

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

  override var itemState: CourseItemState? = null
    set(value) {
      field = value
      if (value != null) {
        // itemState 初始化
        BeginFinalTimeShowModifier.showLock.get(value).lock() // 默认显示开始结束时间
      }
    }

  override fun createItem(coroutineScope: CoroutineScope): CourseCreateAffairItem {
    return CourseCreateAffairItem(
      whatTime = this,
      coroutineScope = coroutineScope,
      viewModel = viewModel,
      dateModel = dateModel,
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
      viewModel.touchedHierarchy.remove(this@CreateAffairTouchedItemWhatTime)
    }
  }
}

