package com.cyxbs.pages.affair.api

import com.cyxbs.components.config.time.SchoolCalendar
import java.io.Serializable


/**
 * 前人写好了通过服务来模块间通信(降低对ARouter的依赖)
 *服务中传递自定义对象找不到太好的做法
 * 1：像这样把数据类直接建立在api模块下
 * 2：将对象转换成为json然后传递再解析
 * 期待后人有更好的做法
 */

/**
 * 发送通知的时候需要的学号的集合：前面是学号，后面是是否空闲，true代表空闲
 */

data class NoClassBean(
    val mStuList : List<Pair<String,Boolean>>,
    //存储点击的时间信息
    val dateJson : DateJson
) : Serializable

data class NotificationBean(
    val stuNumList : List<String>,
    //存储点击的时间信息
    val dateJson : DateJson,
    val title : String,
    val location : String,
) : Serializable

/**
 * 新课标字段：
 * - date: “2026-3-20”
 * - start: 480 (分钟数，为 hour * 60 + minute)
 * - end: 540
 *
 * 老课表字段：
 * - beginLesson: 开始节数，如：1、2 节课以 1 开始；3、4 节课以 3 开始，注意：中午是以 4 开始，傍晚是以 9 开始
 * - day: 星期数，星期一为 0
 * - period: 长度
 * - week: 在哪周，特别注意：整学期的 week 为 0
 */
data class DateJson(
    val beginLesson : Int,
    val day : Int,
    val period : Int,
    val week : Int
) : Serializable
