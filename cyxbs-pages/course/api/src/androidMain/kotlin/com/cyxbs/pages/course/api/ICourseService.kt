package com.cyxbs.pages.course.api

import android.app.Dialog
import android.content.Context
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.pages.affair.api.IAffairService

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/2/9 16:39
 */
interface ICourseService {
  
  companion object {
    /**
     * 课表能显示的最大周数
     */
    val maxWeek: Int
      get() = CourseUtils.maxWeek

    fun setMaxWeek(maxWeek: Int) {
      CourseUtils.setMaxWeek(maxWeek)
    }
  }

  fun createHomeCourseFragment(): Fragment
  
  /**
   * 设置课表头的透明度
   */
  fun setHeaderAlpha(alpha: Float)
  
  /**
   * 设置课表 Vp 页面的透明度
   */
  fun setCourseVpAlpha(alpha: Float)
  
  /**
   * 设置 BottomSheet 偏移量
   */
  fun setBottomSheetSlideOffset(offset: Float)
  
  /**
   * 通过 [lesson] 打开对应 BottomSheetDialog
   */
  fun openBottomSheetDialogByLesson(context: Context, lesson: ILessonService.Lesson): Dialog
  
  /**
   * 通过 [affair] 打开对应 BottomSheetDialog
   */
  fun openBottomSheetDialogByAffair(context: Context, affair: IAffairService.Affair): Dialog
}