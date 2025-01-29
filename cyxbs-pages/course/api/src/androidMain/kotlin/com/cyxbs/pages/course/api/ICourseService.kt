package com.cyxbs.pages.course.api

import android.app.Dialog
import android.content.Context
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.getSp
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
    var maxWeek: Int = appContext.getSp("课表").getInt("课表最大周数", 21)
      private set

    fun setMaxWeek(maxWeek: Int) {
      appContext.getSp("课表").edit { putInt("课表最大周数", maxWeek) }
      this.maxWeek = maxWeek
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