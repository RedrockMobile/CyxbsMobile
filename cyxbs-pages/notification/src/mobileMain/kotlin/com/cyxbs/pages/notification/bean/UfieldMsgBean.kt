package com.cyxbs.pages.notification.bean

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UfieldMsgBean(
    @SerialName("activity_info")
    val activityInfo: ActivityInfo,
    @SerialName("activity_want_to_watch_timestamp")
    val activityWantToWatchTimestamp: Long, // 1692362925
    @SerialName("clicked")
    val clicked: Boolean, // false
    @SerialName("examine_timestamp")
    val examineTimestamp: Long, // 1692362863
    @SerialName("message_id")
    val messageId: Int, // 1
    @SerialName("message_type")
    val messageType: String, // examine_report_reject
    @SerialName("reject_reason")
    val rejectReason: String // 你太捞了
) {
    @Serializable
    data class ActivityInfo(
        @SerialName("activity_content")
        val activityContent: String, // ww我xwwww我xwwww我xwwww我xwwww我xwwww我xwwww我xwwww我xwwww我xwwww我xwwww我xwwww我xwwww我xwwww我xwwww我xwwww我xwwww我
        @SerialName("activity_id")
        val activityId: Int, // 1
        @SerialName("activity_place")
        val activityPlace: String, // 你好
        @SerialName("activity_title")
        val activityTitle: String, // 你好
        @SerialName("activity_type")
        val activityType: String, // culture
        @SerialName("created_at")
        val createdAt: Long, // 1692362843
        @SerialName("end_at")
        val endAt: Long, // 2692185999
        @SerialName("organizer")
        val organizer: String, // 团委委委委委
        @SerialName("registeration_type")
        val registerationType: String, // ticket
        @SerialName("start_at")
        val startAt: Long // 1692331355
    )
}
