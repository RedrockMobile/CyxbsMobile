package com.cyxbs.pages.qa.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.pages.qa.R

/**
 * description ： QA详情页
 * author : wyf
 * date : 2025/8/11 23:23
 */
class DetailActivity : BaseActivity() {
    companion object {
        fun startActivity(context: Context) {
            context.startActivity(Intent(context, DetailActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.qa_activity_detail)
    }
}