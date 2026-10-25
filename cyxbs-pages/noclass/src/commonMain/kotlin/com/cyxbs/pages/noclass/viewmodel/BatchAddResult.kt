package com.cyxbs.pages.noclass.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cyxbs.pages.noclass.bean.Students

/**
 * description ： 批量添加页 → 没课约主页 的数据交接容器
 *
 * 批量添加页校验成功后把学生放到这里，再出栈回到 NoClassPage；
 * NoClassPage 观察到后合并进临时分组并触发查询（弹出与主页"查询"相同的课表面板）。
 * author : whj
 * date : 2026/10/25
 */
object BatchAddResult {

    /** 待合并的学生，null 表示没有待处理数据 */
    var pendingStudents by mutableStateOf<List<Students>?>(null)
        private set

    fun publish(students: List<Students>) {
        pendingStudents = students
    }

    fun consume(): List<Students>? {
        val data = pendingStudents
        pendingStudents = null
        return data
    }
}
