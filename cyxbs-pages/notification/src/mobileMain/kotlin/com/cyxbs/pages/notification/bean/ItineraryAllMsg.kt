package com.cyxbs.pages.notification.bean

import com.cyxbs.components.config.time.MinuteTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ...
 * @author: Black-skyline
 * @email: 2031649401@qq.com
 * @date: 2023/8/2
 * @Description:
 *
 */
data class ItineraryAllMsg(
    var sentItineraryList: List<SentItineraryMsgBean>,
    var receivedItineraryList: List<ReceivedItineraryMsgBean>
)

/**
 * 用户已发送的行程消息
 *
 * @property id             行程消息的id
 * @property title          行程消息的标题
 * @property content        行程消息的内容
 * @property type           返回"sent"，行程消息的类型
 * @property hasRead        该条行程是否已读的状态位
 * @property hasStart       行程是否已开始
 * @property hasOver        行程是否已结束
 * @property hasCancel      行程是否已取消提醒
 * @property updateTime     该行程通知的上次更新时间
 * @property publishTime    该消息通知的产生时间
 * @property peopleCount    该消息通知到的人数
 * @property dateJson       该行程消息的日期信息，可用于本地的事务接口 和 开始状态判断
 */
@Serializable
data class SentItineraryMsgBean(
    @SerialName("id")
    val id: Int,                // 每条消息的id都是独一无二的
    @SerialName("title")
    val title: String,          // 一般是“学习”、“开会”之类的
    @SerialName("content")
    val content: String,        // 行程的具体内容，格式为“你提醒XXX干啥”
    @SerialName("typ")
    val type: String,           // 该条行程的类型，有sent和received之分
    @SerialName("hasRead")
    var hasRead: Boolean,       // 该条行程是否已读
    @SerialName("hasStart")
    var hasStart: Boolean,      // 该条行程是否已开始
    @SerialName("hasEnd")
    var hasOver: Boolean,       // 该条行程是否已结束
    @SerialName("hasCancel")
    var hasCancel: Boolean,     // 该条行程是否被取消提醒
    @SerialName("updateTime")
    val updateTime: Long,       // 该条行程上次更新时的时间戳
    @SerialName("publishTime")
    val publishTime: Long,      // 该条行程产生时的时间戳
    @SerialName("peopleCount")
    val peopleCount: Int,       // 该条行程通知到的人数
    @SerialName("dateJson")
    val dateJson: ItineraryDateBean // 该条行程携带的日期信息
)

/**
 * 通知到用户的行程消息
 *
 * @property id             行程消息的id
 * @property title          行程消息的标题
 * @property content        行程消息的内容
 * @property type           返回"received"，行程消息的类型
 * @property hasAdd         该条行程是否已被加入日程（课表事务）
 * @property hasRead        该条行程是否已读的状态位
 * @property hasStart       行程是否已开始
 * @property hasOver        行程是否已结束
 * @property hasCancel      行程是否已取消提醒
 * @property updateTime     该行程通知的上次更新时间
 * @property publishTime    该消息通知的产生时间
 * @property dateJson       该行程消息的日期信息，可用于本地的事务接口 和 开始状态判断
 */
@Serializable
data class ReceivedItineraryMsgBean(
    @SerialName("id")
    val id: Int,                // 每条消息的id都是独一无二的
    @SerialName("title")
    val title: String,
    @SerialName("content")
    val content: String,        // 行程的具体内容，格式为“xxx提醒你…………”
    @SerialName("typ")
    val type: String,           // 该条行程的类型，有sent和received之分
    @SerialName("hasAdd")
    var hasAdd: Boolean,        // 该条行程是否已被加入日程（课表事务）
    @SerialName("hasRead")
    var hasRead: Boolean,       // 该条行程是否已读
    @SerialName("hasStart")
    var hasStart: Boolean,      // 该条行程是否已开始
    @SerialName("hasEnd")
    var hasOver: Boolean,       // 该条行程是否已结束
    @SerialName("hasCancel")
    var hasCancel: Boolean,     // 该条行程是否被取消提醒
    @SerialName("updateTime")
    val updateTime: Long,       // 该条行程上次更新时的时间戳
    @SerialName("publishTime")
    val publishTime: Long,      // 该条行程产生时的时间戳
    @SerialName("dateJson")
    val dateJson: ItineraryDateBean // 该条行程携带的日期信息
)

@Serializable
data class ItineraryDateBean(
    @SerialName("beginLesson")
    val beginLesson: Int,   // 开始节数，如：1、2 节课以 1 开始；3、4 节课以 3 开始，注意：中午是以 -1 开始，傍晚是以 -2 开始
    @SerialName("day")
    val day: Int,           // 星期数，这里的星期一为 1，注意与添加事务的日期bean的day不一样，那里的day是星期一为 0
    @SerialName("period")
    val period: Int,        // 长度，行程的跨度为几节课，占用了几节课的时间长度就为几
    @SerialName("week")
    val week: Int           // 第几周，整学期为0
)

/**
 * 获取当前时间是在哪节课
 */
fun getCurrentInWhatLesson(): Float {
    val now = MinuteTime.now().minuteOfDay
    return when {
        now < 8 * 60 -> 0F              // 8:00 前
        now < 8 * 60 + 45 -> 1F         // 8:00 - 8:45 第一节课
        now < 8 * 60 + 55 -> 1.5F       // 8:45 - 8:55 第一节课课间
        now < 9 * 60 + 40 -> 2F         // 8:55 - 9:40 第二节课
        now < 10 * 60 + 15 -> 2.5F      // 9:40 - 10:15 第二节课课间
        now < 11 * 60 -> 3F             // 10:15 - 11:00 第三节课
        now < 11 * 60 + 10 -> 3.5F      // 11:00 - 11:10 第三节课课间
        now < 11 * 60 + 55 -> 4F        // 11:10 - 11:55 第四节课
        now < 14 * 60 -> 4.5F           // **NOTE** 11:55 - 14:00 中午时间段
        now < 14 * 60 + 45 -> 5F        // 14:00 - 14:45 第五节课
        now < 14 * 60 + 55 -> 5.5F      // 14:45 - 14:55 第五节课课间
        now < 15 * 60 + 40 -> 6F        // 14:55 - 15:40 第六节课
        now < 16 * 60 + 15 -> 6.5F      // 15:40 - 16:15 第六节课课间
        now < 17 * 60 -> 7F             // 16:15 - 17:00 第七节课
        now < 17 * 60 + 10 -> 7.5F      // 17:00 - 17:10 第七节课课间
        now < 17 * 60 + 55 -> 8F        // 17:10 - 17:55 第八节课
        now < 19 * 60 -> 8.5F           // **NOTE** 17:55 - 19:00 傍晚时间段
        now < 19 * 60 + 45 -> 9F        // 19:00 - 19:45 第九节课
        now < 19 * 60 + 55 -> 9.5F      // 19:45 - 19:55 第九节课课间
        now < 20 * 60 + 40 -> 10F       // 19:55 - 20:40 第十节课
        now < 20 * 60 + 50 -> 10.5F     // 20:40 - 20:50 第十节课课间
        now < 21 * 60 + 35 -> 11F       // 20:50 - 21:35 第十一节课
        now < 21 * 60 + 45 -> 11.5F     // 21:35 - 21:45 第十一节课课间
        now < 22 * 60 + 30 -> 12F       // 21:45 - 22:30 第十二节课
        now < 24 * 60 -> 12.5F          // 22:30 - 24:00 晚上最后一节课下课到凌晨
        else -> {                       // 意外的错误时间
            Float.MIN_VALUE
        }
    }
}

/**
 * 判断当天的当前行程是否已结束, 此方法思路来自课表CourseNowTimeHelper类,
 * 目前该方法用不到, 因为后端给出了hasEnd字段，判断hasEnd(行程是否已结束)的逻辑在后端,
 * 目前此方法暂且保留
 */
fun ItineraryDateBean.judgeCurrentIsOver(): Boolean {
    val nowLesson = getCurrentInWhatLesson()
    if (nowLesson == 0F) return false
    if (beginLesson < 0) { // 单独处理 中午时间段、傍晚时间段开始的行程
        if (beginLesson == -1) {
            return nowLesson > (4.5F + period - 1)
        }
        if (beginLesson == -2) {
            return nowLesson > (8.5F + period - 1)
        }
        // 该行程的开始时间有问题
        return false
    } else { // 处理正常课程时间段开始的行程
        return nowLesson > (beginLesson + period - 1)
    }
}

/**
 * 转 Json的dataBean，转为Json后作为添加事务的dateJson参数使用
 *
 * @property beginLesson
 * @property day
 * @property period
 * @property week
 */
@Serializable
data class AffairDateBean(
    @SerialName("begin_lesson")
    val beginLesson: Int,       // 开始节数，如：1、2 节课以 1 开始；2、3 节课以 2 开始，注意：中午是以 -1 开始，傍晚是以 -2 开始
    @SerialName("day")
    val day: Int,               // 星期数，这里的星期一为 0，星期日为 6
    @SerialName("period")
    val period: Int,            // 长度，事务的跨度为几节课，占用了几节课的时间长度就为几
    @SerialName("week")
    val week: List<Int>         // 第几周，整学期为0
)

fun List<ItineraryDateBean>.toAffairDateBean(): List<AffairDateBean> {
    return map {
        AffairDateBean(
            it.beginLesson,
            (it.day - 1) % 7,
            it.period,
            listOf(it.week)
        )
    }
}