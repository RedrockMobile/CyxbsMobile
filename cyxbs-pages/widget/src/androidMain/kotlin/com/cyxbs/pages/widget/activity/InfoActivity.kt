package com.cyxbs.pages.widget.activity

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.api.ICourseService
import com.cyxbs.pages.widget.repo.bean.AffairEntity
import com.cyxbs.pages.widget.repo.bean.AffairEntity.Companion.convertToApi
import com.cyxbs.pages.widget.repo.bean.LessonEntity
import com.cyxbs.pages.widget.repo.bean.LessonEntity.Companion.convertToApi
import com.cyxbs.pages.widget.util.CLICK_AFFAIR
import com.cyxbs.pages.widget.util.CLICK_LESSON
import com.cyxbs.pages.widget.util.gson

/**
 * description ： 桌面点击小组件查看事务和课表详情的透明activity
 * author : Watermelon02
 * email : 1446157077@qq.com
 * date : 2022/9/25 17:21
 */
class InfoActivity : AppCompatActivity() {
    private lateinit var dialog: Dialog
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = ""
        val lesson = gson.fromJson(intent.getStringExtra(CLICK_LESSON), LessonEntity::class.java)
        val affair = gson.fromJson(intent.getStringExtra(CLICK_AFFAIR), AffairEntity::class.java)
        dialog = if (lesson != null) {
            ICourseService::class.impl().openBottomSheetDialogByLesson(this, lesson.convertToApi())
        } else if (affair != null) {
            ICourseService::class.impl().openBottomSheetDialogByAffair(this, affair.convertToApi())
        }else error("???")
        dialog.setOnDismissListener {
            finish()
        }
    }
}