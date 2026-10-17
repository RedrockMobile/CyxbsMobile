package com.cyxbs.pages.mine.util

import com.cyxbs.components.log.CyxbsLog
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by zia on 2018/9/29.
 */
object TimeUtil {

    /**
     * 格式化时间
     * 必要的参数格式：yyyy-MM-dd HH:mm:ss，格式不对返回 "null"
     * 传入date，返回 刚刚 || 08:30 || 2018-03-03
     */
    fun wrapTime(time: String): String {
        try {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(time)
            return wrapTime(date)
        }catch (e: ParseException){
            CyxbsLog.e("TimeUtil", e) { "failed to parse time" }
            return "null"
        }
    }

    fun wrapTime(date: Date): String{
        //获取5分钟前的date
        val before5Min = Calendar.getInstance()
        before5Min.set(Calendar.MINUTE, before5Min.get(Calendar.MINUTE) - 1)
        //获取一天前的date
        val before1Day = Calendar.getInstance()
        before1Day.set(Calendar.DAY_OF_YEAR, before1Day.get(Calendar.DAY_OF_YEAR) - 1)

        return when {
            date > before5Min.time -> "刚刚"
            date > before1Day.time -> {
                //今天
                val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                format.format(date)
            }
            else -> {
                //昨天以前
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                format.format(date)
            }
        }
    }
}