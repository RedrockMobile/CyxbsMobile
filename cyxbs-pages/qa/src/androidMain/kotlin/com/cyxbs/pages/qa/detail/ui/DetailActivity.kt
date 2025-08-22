package com.cyxbs.pages.qa.detail.ui

import android.content.Context
import android.content.Intent
import com.cyxbs.components.base.ui.BaseActivity
/**
 * description ： TODO:类的作用
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/22 13:59
 */
class DetailActivity : BaseActivity() {
    companion object {
        private const val EXTRA_QUESTION_ID = "id"

        fun startActivity(context: Context, id: Long) {
            context.startActivity(Intent(context, DetailActivity::class.java).apply {
                putExtra(EXTRA_QUESTION_ID, id)
            })
        }
    }
}