package com.cyxbs.pages.noclass.util

/** 打开安排行程页面，Android 上跳转到事务模块，其他平台无操作 */
expect fun noClassArrangePlan(
    week: Int,
    day: Int,
    beginLesson: Int,
    lessonLength: Int,
    spareIds: List<String>,
    idToNameMap: Map<String, String>,
)
