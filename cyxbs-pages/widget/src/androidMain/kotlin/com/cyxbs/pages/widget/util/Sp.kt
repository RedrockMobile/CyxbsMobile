package com.cyxbs.pages.widget.util

import android.content.Context
import android.content.SharedPreferences
import com.cyxbs.components.init.appContext

/**
 * description ： 本模块通用的sp
 * author : Watermelon02
 * email : 1446157077@qq.com
 * date : 2022/9/23 09:11
 */
val defaultSp: SharedPreferences
    get() = appContext.getSharedPreferences("module_widget", Context.MODE_PRIVATE)