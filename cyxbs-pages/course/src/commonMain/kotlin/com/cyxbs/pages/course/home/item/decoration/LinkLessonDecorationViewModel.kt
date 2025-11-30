package com.cyxbs.pages.course.home.item.decoration

import androidx.compose.runtime.Composable
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.sp.accountSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.item.LinkLessonItem
import com.cyxbs.pages.course.home.item.LinkLessonItemFactory
import com.cyxbs.pages.course.model.LessonRepository
import com.cyxbs.pages.course.model.LinkLessonRepository
import com.cyxbs.pages.course.view.decoration.CoursePageDecoration
import com.cyxbs.pages.course.view.item.CourseItemViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * .
 *
 * @author 985892345
 * @date 2025/10/18
 */
class LinkLessonDecorationViewModel(
  val hierarchy: CourseItemViewModel.ItemHierarchy<LinkLessonItem>,
) : BaseViewModel(), CoursePageDecoration {

  companion object {
    val Comparable = compareBy<LinkLessonItem> { -it.page } // page 越小越在上
      .thenBy { -it.lesson.dayOfWeek.ordinal } // dayOfWeek 越小越在上
      .thenBy { it.lesson.beginTime } // beginTime 越大越在上
      .thenBy { it.lesson.finalTime } // finalTime 越大越在上
  }


  private val SETTING_KEY_ENABLE_SHOW_LINK_COURSE = "enable_show_link_course"

  val enableShow: StateFlow<Boolean?> get() = _enableShow
  private val _enableShow = MutableStateFlow<Boolean?>(null)

  // 保存最后一次数据，用于改变关联人课表可见性后再展示
  private var lastData = emptyList<LessonByWeeks>()

  private val itemFactory = LinkLessonItemFactory.get()

  fun changeVisible() {
    if (LinkLessonRepository.state.value.isNull()) return
    val nowEnableShow = enableShow.value ?: return
    val newEnableShow = !nowEnableShow
    accountSettings.putBoolean(SETTING_KEY_ENABLE_SHOW_LINK_COURSE, newEnableShow)
    _enableShow.value = newEnableShow
    resetData(if (newEnableShow) lastData else emptyList())
  }

  init {
    LinkLessonRepository.state.map {
      it.linkNum
    }.onEach {
      _enableShow.value = if (it.isEmpty()) null else {
        AccountSettings.get(it).getBoolean(SETTING_KEY_ENABLE_SHOW_LINK_COURSE, true)
      }
    }.flatMapLatest {
      createLessonFlow(it)
    }.onEach {
      lastData = it ?: emptyList()
      resetData(it)
    }.catch {

    }.launchIn(appCoroutineScope)
  }

  private fun createLessonFlow(linkStuNum: String): Flow<List<LessonByWeeks>?> {
    return if (linkStuNum.isEmpty()) flowOf(null)
    else LessonRepository.observeLesson(
      stuNum = linkStuNum,
      needOldData = true,
    )
  }

  private fun resetData(data: List<LessonByWeeks>?) {
//    clear() // 对于课程来说每一次更新可以使用全量更新
    data ?: return
    if (enableShow.value != true) return
    data.forEach { lesson ->
      // 添加进整学期
//      add(itemFactory.createLinkLessonItem(0, lesson))
      // 添加进每周
      lesson.week.forEach { week ->
//        add(itemFactory.createLinkLessonItem(week, lesson))
      }
    }
  }

  @Composable
  override fun CoursePage(nextContent: @Composable (() -> Unit)) {

  }
}