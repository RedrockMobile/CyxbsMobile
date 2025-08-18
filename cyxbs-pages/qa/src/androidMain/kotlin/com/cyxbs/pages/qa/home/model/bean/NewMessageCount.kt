package com.cyxbs.pages.qa.home.model.bean

import android.content.Context
import com.cyxbs.components.utils.extensions.getSp
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * description ： 更新消息总数
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/17 19:54
 */
data class NewMessageCount(
    val total: Int = 0,
    val updatedCount: Int = 0,
    val newStudentCount: Int = 0,
    val learningCount: Int = 0,
    val lifeCount: Int = 0,
    val otherCount: Int = 0
)

class NewMessageAnalyzer(private val context: Context?) {
    fun analyze(items: List<Item>?): NewMessageCount {
        if (items.isNullOrEmpty()) return NewMessageCount()

        val filteredList = items.filter { it.status == 2 }
        val sp = context?.getSp("qa_content")
        val savedTime = sp?.getString("time", null)  // null 表示没保存过


        var updatedCount = 0
        var newStudentCount = 0
        var learningCount = 0
        var lifeCount = 0
        var otherCount = 0

        // 过滤出时间更新的消息
        val newFilteredList = filteredList.filter { item ->
            isNewer(item.a_time, savedTime)
        }

        // 计数
        newFilteredList.forEach { item ->
            updatedCount++
            item.tags.trim().split("\\s+".toRegex()).forEach { tag ->
                when (tag) {
                    "新生" -> newStudentCount++
                    "学习类" -> learningCount++
                    "生活类" -> lifeCount++
                    "其他" -> otherCount++
                }
            }
        }

        //  只保存一次：取最新的 a_time
        newFilteredList.maxByOrNull { parseTimestamp(it.a_time) }?.a_time?.let { latestTime ->
            saveLastItemTime(latestTime)
        }

        return NewMessageCount(
            total = newFilteredList.size,
            updatedCount = updatedCount,
            newStudentCount = newStudentCount,
            learningCount = learningCount,
            lifeCount = lifeCount,
            otherCount = otherCount
        )
    }
    private fun isNewer(itemTime: String, savedTime: String?): Boolean {
        if (savedTime.isNullOrBlank()) {
            // 第一次进入，没有保存过时间 → 不提示任何新消息
            return false
        }
        val itemTimestamp = parseTimestamp(itemTime)
        val savedTimestamp = parseTimestamp(savedTime)
        return itemTimestamp > savedTimestamp
    }


    // 将字符串时间转换为时间戳
    private fun parseTimestamp(time: String): Long {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        return try {
            dateFormat.parse(time)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // 保存最新时间戳到 SharedPreferences
    private fun saveLastItemTime(time: String) {
        val sp = context?.getSp("qa_content")
        sp?.edit()?.apply {
            putString("time", time)  // 存储时间戳
            apply()  // 异步提交
        }
    }
}




