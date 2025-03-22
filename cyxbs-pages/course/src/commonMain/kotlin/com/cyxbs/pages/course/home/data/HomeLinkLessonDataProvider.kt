package com.cyxbs.pages.course.home.data

import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.sp.accountSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.course.home.item.LinkLessonItem
import com.cyxbs.pages.course.model.LessonRepository
import com.cyxbs.pages.course.model.LinkLessonRepository
import com.cyxbs.pages.course.view.data.CourseDataProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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

  private var lastData = emptyList<LessonByWeeks>()

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
    }.launchIn(appCoroutineScope)
  }

  private fun createLessonFlow(linkStuNum: String): Flow<List<LessonByWeeks>?> = flow {
    if (linkStuNum.isEmpty()) {
      emit(null)
    } else {
      val cacheLesson = LessonRepository.getLesson(linkStuNum)
      if (cacheLesson != null) {
        emit(cacheLesson.data)
      }
      LessonRepository.requestLesson(linkStuNum).onSuccess {
        emit(it)
      }
    }
  }

  private fun resetData(data: List<LessonByWeeks>?) {
    clear()
    data ?: return
    if (enableShow.value != true) return
    data.forEach { lesson ->
      // 添加进整学期
      add(LinkLessonItem(0, lesson))
      // 添加进每周
      lesson.week.forEach { week ->
        add(LinkLessonItem(week, lesson))
      }
    }
  }
}