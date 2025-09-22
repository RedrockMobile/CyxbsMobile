package com.cyxbs.pages.course.page.course.ui.dialog.adapter.viewholder

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.cyxbs.components.base.utils.safeSubscribeBy
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.affair.api.IAffairService
import com.cyxbs.pages.course.R
import com.cyxbs.pages.course.page.course.data.AffairData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers

/**
 *
 *
 * @author 985892345
 * @date 2022/9/17 18:03
 */
class AffairVH(
  parent: ViewGroup,
  val dialog: DialogInterface
) : CourseViewHolder<AffairData>(parent, R.layout.course_dialog_bottom_affair) {
  
  private var mData: AffairData? = null
  
  private val mTvTitle = findViewById<TextView>(R.id.course_tv_dialog_affair_title)
  private val mTvContent = findViewById<TextView>(R.id.course_tv_dialog_affair_content)
  private val mTvDuration = findViewById<TextView>(R.id.course_tv_dialog_affair_duration)
  private val mBtnDelete = findViewById<Button>(R.id.course_btn_dialog_affair_delete)
  private val mBtnChange = findViewById<Button>(R.id.course_btn_dialog_affair_change)
  
  @SuppressLint("SetTextI18n")
  override fun onBindViewHolder(data: AffairData) {
    mData = data
    mTvTitle.text = data.title
    mTvContent.text = data.content
    mTvDuration.text = "${data.weekStr} ${data.weekdayStr}   ${data.durationStr}"
  }
  
  init {
    mBtnDelete.setOnSingleClickListener {
      val data = mData ?: return@setOnSingleClickListener
      IAffairService::class.impl()
        .deleteAffair(data.onlyId)
        .observeOn(AndroidSchedulers.mainThread())
        .safeSubscribeBy(mBtnDelete) {
          toast("删除成功")
          dialog.dismiss()
        }
    }
    mBtnChange.setOnSingleClickListener {
      val data = mData ?: return@setOnSingleClickListener
      IAffairService::class.impl()
        .startActivityForEditActivity(data.onlyId)
      dialog.dismiss() // 因为修改过后数据会发生变动，所以这里直接取消 dialog 显示
    }
  }
}