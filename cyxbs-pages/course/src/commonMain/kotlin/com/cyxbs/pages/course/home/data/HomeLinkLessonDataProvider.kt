package com.cyxbs.pages.course.home.data

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.sp.accountSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.item.LinkLessonItemFactory
import com.cyxbs.pages.course.home.item.LinkLessonItemModel
import com.cyxbs.pages.course.model.LessonRepository
import com.cyxbs.pages.course.model.LinkLessonRepository
import com.cyxbs.pages.course.view.data.CourseDataProvider
import com.cyxbs.pages.course.view.item.CourseItemModel
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
 * @date 2025/3/10
 */
object HomeLinkLessonDataProvider : CourseDataProvider() {

  private const val SETTING_KEY_ENABLE_SHOW_LINK_COURSE = "enable_show_link_course"

  val enableShow: StateFlow<Boolean?> get() = _enableShow
  private val _enableShow = MutableStateFlow<Boolean?>(null)

  // 保存最后一次数据，用于改变关联人课表可见性后再展示
  private var lastData = emptyList<LessonByWeeks>()

  private val itemFactory = LinkLessonItemFactory::class.impl()

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

  private fun createLessonFlow(linkStuNum: String): Flow<List<LessonByWeeks>?> =
    if (linkStuNum.isEmpty()) flowOf(null) else LessonRepository.getAndRequestLesson(linkStuNum)

  private fun resetData(data: List<LessonByWeeks>?) {
    clear()
    data ?: return
    if (enableShow.value != true) return
    data.forEach { lesson ->
      // 添加进整学期
      add(itemFactory.createLinkLessonItemModel(0, lesson))
      // 添加进每周
      lesson.week.forEach { week ->
        add(itemFactory.createLinkLessonItemModel(week, lesson))
      }
    }
  }

  override fun compare(a: CourseItemModel, b: CourseItemModel): Int {
    a as LinkLessonItemModel
    b as LinkLessonItemModel
    if (a.page == 0 && b.page == 0) {
      val weekDiff = a.lesson.week.first() - b.lesson.week.first()
      return if (weekDiff != 0) -weekDiff else super.compare(a, b)
    }
    return super.compare(a, b)
  }
}