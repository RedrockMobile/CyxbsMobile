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
 *
 * 用来处理新消息的条数，这里分多种消息根据tablayout上的名字来确定
 *
 * 机制:本地保存该次网络请求返回的最新时间，与下一次返回数据相比，下一次数据时间更新的item数就是新消息
 */
data class NewMessageCountData(
    val total: Int = 0,
    val updatedCount: Int = 0,
    val newStudentCount: Int = 0,
    val learningCount: Int = 0,
    val lifeCount: Int = 0,
    val otherCount: Int = 0
)

class NewMessageAnalyzer(private val context: Context?) {

    fun analyze(items: List<Item>?): NewMessageCountData {
        if (items.isNullOrEmpty()) return NewMessageCountData()

        val filteredList = items.filter { it.status == 2 }
        val sp = context?.getSp("qa_content")
        val savedTime = sp?.getString("time", null)

        // 第一次访问：没有保存过时间直接返回0 更符合新消息的理念
        if (savedTime.isNullOrBlank()) {
            // 保存最新的 a_time（最新一条消息）
            filteredList.maxByOrNull { parseTimestamp(it.a_time) }?.a_time?.let { latestTime ->
                saveLastItemTime(latestTime)
            }
            // 返回全 0
            return NewMessageCountData()
        }

        var updatedCount = 0
        var newStudentCount = 0
        var learningCount = 0
        var lifeCount = 0
        var otherCount = 0

        // 过滤出时间更新的消息
        val newFilteredList = filteredList.filter { item ->
            isNewer(item.a_time, savedTime)
        }

        // 计数（单标签）
        newFilteredList.forEach { item ->
            updatedCount++
            when (item.tags.trim()) {
                "新生" -> newStudentCount++
                "学习" -> learningCount++
                "生活" -> lifeCount++
                "其他" -> otherCount++
            }
        }

        // 保存最新时间戳
        newFilteredList.maxByOrNull { parseTimestamp(it.a_time) }?.a_time?.let { latestTime ->
            saveLastItemTime(latestTime)
        }

        return NewMessageCountData(
            total = newFilteredList.size,
            updatedCount = updatedCount,
            newStudentCount = newStudentCount,
            learningCount = learningCount,
            lifeCount = lifeCount,
            otherCount = otherCount
        )
    }

    private fun isNewer(itemTime: String, savedTime: String): Boolean {
        val itemTimestamp = parseTimestamp(itemTime)
        val savedTimestamp = parseTimestamp(savedTime)
        return itemTimestamp > savedTimestamp
    }

    private fun parseTimestamp(time: String): Long {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        return try {
            dateFormat.parse(time)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun saveLastItemTime(time: String) {
        val sp = context?.getSp("qa_content")
        sp?.edit()?.apply {
            putString("time", time)
            apply()
        }
    }
}





