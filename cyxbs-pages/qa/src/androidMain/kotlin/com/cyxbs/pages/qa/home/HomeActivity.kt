package com.cyxbs.pages.qa.home

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.QA_ENTRY
import com.cyxbs.pages.qa.R
import com.cyxbs.pages.qa.publish.ui.activity.PublishActivity
import com.g985892345.provider.api.annotation.KClassProvider

/**
 * description ： QA的首页
 * author : summer
 * date : 2025/8/11 23:07
 */
@KClassProvider(clazz = Activity::class, name = QA_ENTRY)
class HomeActivity : BaseActivity() {
    private val qaHomeBtnSearch by R.id.qa_home_btn_search.view<Button>()
    private val qaHomeBtnPublish by R.id.qa_home_btn_publish.view<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.qa_activity_home)
        initClick()
    }

    fun initClick() {

        //前往搜索页
        qaHomeBtnSearch.setOnClickListener {

        }

        //前往发布问题
        qaHomeBtnPublish.setOnClickListener {
            PublishActivity.startActivity(this)
        }
    }
}