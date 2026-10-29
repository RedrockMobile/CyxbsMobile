package com.cyxbs.pages.noclass.util

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.affair.api.DateJson
import com.cyxbs.pages.affair.api.IAffairService
import com.cyxbs.pages.affair.api.NoClassBean

actual fun noClassArrangePlan(
    week: Int,
    day: Int,
    beginLesson: Int,
    lessonLength: Int,
    spareIds: List<String>,
    idToNameMap: Map<String, String>,
) {
    val currentStuNum = IAccountService::class.impl().stuNum
    val stuList = idToNameMap
        .filterKeys { it != currentStuNum }
        .map { (stuNum, _) -> stuNum to (stuNum in spareIds) }
    val dateJson = DateJson(
        beginLesson = beginLesson,
        day = day,
        period = lessonLength,
        week = week,
    )
    IAffairService::class.impl().startActivityForNoClass(
        NoClassBean(mStuList = stuList, dateJson = dateJson)
    )
}
