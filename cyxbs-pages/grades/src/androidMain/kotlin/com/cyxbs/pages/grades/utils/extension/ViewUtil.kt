package com.cyxbs.pages.grades.utils.extension

import com.cyxbs.components.init.appContext

/**
 * Created by roger on 2020/2/12
 */
fun dp2px(value: Int): Int {
    val v = com.cyxbs.components.init.appContext.resources.displayMetrics.density
    return (v * value + 0.5f).toInt()
}