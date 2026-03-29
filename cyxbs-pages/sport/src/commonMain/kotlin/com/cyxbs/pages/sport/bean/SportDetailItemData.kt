package com.cyxbs.pages.sport.bean

interface SportDetailItemData {
    /**
     * 打卡的日期
     */
    val date: String

    /**
     * 打卡的 `起始` 时间，如： 19:20:22
     */
    val time: String

    /**
     * 打卡的地点: 太极运动场,风华运动场,风雨操场,灯光篮球场  以上四个地点之一
     */
    var spot: String

    /**
     * 打卡的类型： "跑步"、"其他"类型
     */
    val type: String

    /**
     * 是否计入奖励
     */
    val isAward: Boolean

    /**
     * 有效（true）或无效（false）
     */
    val valid: Boolean

    override fun equals(other: Any?): Boolean
}