package com.cyxbs.pages.course.page.course.ui.dialog.adapter.viewholder

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.route.COURSE_POS_TO_MAP
import com.cyxbs.components.config.route.DISCOVER_MAP
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.pages.course.R
import com.cyxbs.pages.course.page.course.data.StuLessonData
import com.cyxbs.pages.course.page.find.ui.find.activity.FindLessonActivity

/**
 *
 * @param isShowLink 是否是主页课表
 *
 * @author 985892345
 * @date 2022/9/17 18:00
 */
class StuLessonVH(
  parent: ViewGroup,
  private val isShowLink: Boolean
) : CourseViewHolder<StuLessonData>(parent, R.layout.course_dialog_bottom_lesson_stu) {
  
  private var mData: StuLessonData? = null
  
  private val mTvTitle = findViewById<TextView>(R.id.course_tv_dialog_stu_course)
  private val mTvClassroom = findViewById<TextView>(R.id.course_tv_dialog_stu_classroom)
  private val mTvTeacher = findViewById<TextView>(R.id.course_tv_dialog_stu_teacher)
  private val mTvRawWeek = findViewById<TextView>(R.id.course_tv_dialog_stu_rawWeek)
  private val mTvTime = findViewById<TextView>(R.id.course_tv_dialog_stu_time)
  private val mTvType = findViewById<TextView>(R.id.course_tv_dialog_stu_type)
  private val mIvLink = findViewById<ImageView>(R.id.course_iv_dialog_stu_link)
  
  @SuppressLint("SetTextI18n")
  override fun onBindViewHolder(data: StuLessonData) {
    mData = data
    mTvTitle.text = data.course.course
    mTvClassroom.text = data.course.classroom
    mTvTeacher.text = data.course.teacher
    mTvRawWeek.text = data.course.rawWeek
    mTvTime.text = "${data.weekdayStr} ${data.durationStr}"
    mTvType.text = data.course.type
    if (isShowLink) {
      if (data.stuNum != IAccountService::class.impl().stuNum) {
        mIvLink.visible()
      } else {
        mIvLink.gone()
      }
    }
  }
  
  init {
    mTvClassroom.setOnSingleClickListener {
      val data = mData ?: return@setOnSingleClickListener
      // 跳转至地图界面
      startActivity(DISCOVER_MAP) {
        putExtra(COURSE_POS_TO_MAP, data.course.classroom)
      }
    }
  
    mIvLink.setOnSingleClickListener {
      val data = mData ?: return@setOnSingleClickListener
      // 跳到查找关联人课表的界面
      FindLessonActivity.startByStuNum(it.context, data.stuNum)
    }
  }
}