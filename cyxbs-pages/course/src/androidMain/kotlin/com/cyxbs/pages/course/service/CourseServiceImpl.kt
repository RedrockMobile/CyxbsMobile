package com.cyxbs.pages.course.service

import android.app.Dialog
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.pages.affair.api.IAffairService
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.course.api.ILessonService
import com.cyxbs.pages.course.page.course.data.AffairData
import com.cyxbs.pages.course.page.course.data.LessonData
import com.cyxbs.pages.course.page.course.data.StuLessonData
import com.cyxbs.pages.course.page.course.ui.dialog.CourseBottomDialog
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/4 15:31
 */
@ImplProvider
object CourseServiceImpl : ICourseService {

  override fun setHeaderAlpha(alpha: Float) {
    _headerAlphaState.value = alpha
  }
  
  override fun setCourseVpAlpha(alpha: Float) {
    _courseAlphaState.value = alpha
  }
  
  override fun setBottomSheetSlideOffset(offset: Float) {
    _bottomSheetSlideOffset.value = offset
  }
  
  override fun openBottomSheetDialogByLesson(context: Context, lesson: ILessonService.Lesson): Dialog {
    return CourseBottomDialog(
      context,
      listOf(
        StuLessonData(
          lesson.stuNum,
          lesson.week,
          LessonData.Course(
            lesson.course,
            lesson.classroom,
            lesson.courseNum,
            lesson.hashDay,
            lesson.beginLesson,
            lesson.period,
            lesson.teacher,
            lesson.rawWeek,
            lesson.type
          )
        )
      ), true
    ).apply { show() }
  }
  
  override fun openBottomSheetDialogByAffair(context: Context, affair: IAffairService.Affair): Dialog {
    return CourseBottomDialog(
      context,
      listOf(
        AffairData(
          affair.stuNum,
          affair.week,
          affair.day,
          affair.beginLesson,
          affair.period,
          affair.onlyId,
          affair.time,
          affair.title,
          affair.content
        )
      ), true
    ).apply { show() }
  }
  
  private val _headerAlphaState = MutableLiveData<Float>()
  val headerAlphaState: LiveData<Float> get() = _headerAlphaState
  
  private val _courseAlphaState = MutableLiveData<Float>()
  val courseVpAlphaState: LiveData<Float> get() = _courseAlphaState
  
  private val _bottomSheetSlideOffset = MutableLiveData<Float>()
  val bottomSheetSlideOffset: LiveData<Float> get() = _bottomSheetSlideOffset
}
