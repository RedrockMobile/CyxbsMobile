package com.cyxbs.pages.qa.publish

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.pages.qa.R

/**
 * description ： 发布问题的Activity
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/8/11 23:20
 */
class PublishActivity : BaseActivity(){
    companion object{
        fun startActivity(context: Context){
            context.startActivity(Intent(context, PublishActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.qa_activity_publish)
    }
}