package com.cyxbs.pages.notification.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Author by OkAndGreat
 * Date on 2022/5/2 16:46.
 *
 */
object Date {
    /**
     * 统一日期格式，如：2021.5.5/2021-5-5 12:00
     */
    private val sdf1 = SimpleDateFormat("yyyy.M.d", Locale.CHINA)
    private val sdf2 = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val sdf3 = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val sdf4 = SimpleDateFormat("yyyy/M/d  H:mm", Locale.CHINA)
    private val sdf5 = SimpleDateFormat("M/d  H:mm", Locale.CHINA)

    /**
     * 根据时间戳获取日期, 形式为 yyyy.M.d
     *
     * **NOTE:** 时间单位是毫秒
     */
    fun getTime(time: Long): String {
        val date = Date(time * 1000)
        return sdf1.format(date)
    }

    fun getTime(date: Date): String {
        return sdf1.format(date)
    }

    /**
     * 根据时间戳获取日期, 形式为 yyyy-MM-dd HH:mm
     *
     * **NOTE:** 时间单位是毫秒
     */
    fun getExactTime(time: Long): String {
        val date = Date(time * 1000)
        return sdf2.format(date)
    }

    /**
     * 根据时间戳获取日期, 形式为 yyyy-MM-dd
     *
     * **NOTE:** 时间单位是毫秒
     */
    fun getUnExactTime(time: Long): String {
        val date = Date(time * 1000)
        return sdf3.format(date)
    }

    /**
     * 给每一条行程消息获取格式化后的日期字符串
     * @param time 行程消息的时间戳, 单位是秒
     */
    fun getItineraryUpdateTime(time: Long): String {
        val date = Date(time * 1000)
        return if (isCurrentYear(time = (time * 1000)))
            sdf5.format(date)
        else
            sdf4.format(date)
    }

    /**
     * 检测传入的时间戳是否是当年
     * @param time 传入的时间戳，单位是毫秒
     */
    private fun isCurrentYear(time: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time
        val year1 = calendar.get(Calendar.YEAR)
        calendar.timeInMillis = System.currentTimeMillis()
        val year2 = calendar.get(Calendar.YEAR)
        return year1 == year2
    }
}