
package com.cyxbs.pages.noclass.viewmodel

import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.api.ILessonService2
import com.cyxbs.pages.course.api.LessonByWeeks
import com.cyxbs.pages.noclass.bean.NoClassSpareTime
import com.cyxbs.pages.noclass.bean.Students
import com.cyxbs.pages.noclass.bean.toSpareTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * description ： TODO:没课约课表查询 ViewModel
 * author : 我不抽火哪儿来的烟
 * email : 3114795332qq.com
 * date : 2026/5/19 17:00
 */
class CourseQueryViewModel : BaseViewModel() {

    private val _noclassData = MutableStateFlow<HashMap<Int, NoClassSpareTime>>(hashMapOf())
    val noclassData = _noclassData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun queryLessons(stuNumList: List<String>, students: List<Students>) {
        if (stuNumList.isEmpty()) return
        val stuNameById = students.associate { it.id to it.name }
        if (stuNumList.size != stuNameById.size) return
        queryLessons(stuNumList, stuNameById)
    }

    fun queryLessonsFromPairs(stuNumList: List<String>, pairs: List<Pair<String, String>>) {
        if (stuNumList.isEmpty()) return
        val stuNameById = pairs.toMap()
        if (stuNumList.size != stuNameById.size) return
        queryLessons(stuNumList, stuNameById)
    }

    private fun queryLessons(stuNumList: List<String>, stuNameById: Map<String, String>) {
        if (stuNumList.isEmpty() || stuNameById.isEmpty() || stuNumList.size != stuNameById.size) return
        viewModelScope.launch {
            _isLoading.value = true
            val lessonService = ILessonService2::class.impl()
            val lessonMap = mutableMapOf<String, List<LessonByWeeks>>()
            stuNumList.forEach { stuNum ->
                lessonService.requestLesson(stuNum).fold(
                    onSuccess = { lessons ->
                        lessonMap[stuNum] = lessons
                    },
                    onFailure = { /* skip failed requests */ }
                )
            }
            if (lessonMap.isEmpty()) {
                _isLoading.value = false
                return@launch
            }
            val spareTime = lessonMap.toSpareTime().onEach {
                it.value.mIdToNameMap = HashMap(stuNameById)
            }
            _noclassData.value = spareTime
            _isLoading.value = false
        }
    }
}
