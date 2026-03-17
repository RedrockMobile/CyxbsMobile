package com.cyxbs.pages.emptyroom.viewmodel

import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.emptyroom.bean.EmptyRoomBean
import com.cyxbs.pages.emptyroom.network.EmptyRoomApiService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * description ： TODO:空教室页的ViewModel
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/3/3 16:10
 */

@OptIn(FlowPreview::class)
class EmptyRoomComposeViewModel : BaseViewModel() {

    //星期几
    private fun getInitialDayOfWeek(): Int {
        val now = Clock.System.now()
        val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        return localDateTime.dayOfWeek.isoDayNumber
    }

    //私有MutableStateFlow,用于内部修改(通过调用函数)
    private val _selectedWeek = MutableStateFlow<Int?>(SchoolCalendar.getWeekOfTerm())
    private val _selectedWeekDayNum = MutableStateFlow<Int?>(getInitialDayOfWeek())
    private val _selectedBuildNum = MutableStateFlow<Int?>(null)
    private val _selectedSections = MutableStateFlow<List<Int>>(emptyList())//节次是多选


    //周次
    val selectedWeekSet = _selectedWeek.map { setOfNotNull(it) }

    //星期
    val selectedWeekDaySet = _selectedWeekDayNum.map { setOfNotNull(it) }

    //教学楼
    val selectedBuildNumSet = _selectedBuildNum.map { setOfNotNull(it) }

    //节次(多选)
    val selectedSectionsSet = _selectedSections.map { it.toSet() }

    //返回数据
    private val _roomResult = MutableStateFlow<List<String>?>(null)
    val roomResult = _roomResult.asStateFlow()

    //判断加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
//外部改值
    fun onWeekChange(week: Int) {
        _selectedWeek.value = week
    }

    fun onWeekDayChange(day: Int) {
        _selectedWeekDayNum.value = day
    }

    fun onBuildChange(build: Int) {
        _selectedBuildNum.value = build
    }

    fun toggleSection(section: Int) {
        val current = _selectedSections.value
        _selectedSections.value = if (current.contains(section)) {
            if (current.size > 1) current - section else current
        } else {
            current + section
        }
    }

    init {
        viewModelScope.launch {
            //直接合并MutableStateFlow
            combine(
                _selectedWeek,
                _selectedWeekDayNum,
                _selectedBuildNum,
                _selectedSections
            ) { week, day, build, sections ->
                QueryParam(week, day, build, sections)
            }
                .filter { it.isReady() }
                //只有参数真的变了才往下走
                .distinctUntilChanged()
                //防抖
                .debounce(300)
                .collectLatest { param ->
                    //转换并请求
                    val bean = EmptyRoomBean(
                        week = param.week.toString(),
                        weekday = param.day.toString(),
                        buildNum = param.build.toString(),
                        section = param.sections
                            .map { it - 1 }
                            .sorted()
                            .joinToString(",")
                    )
                    performFetch(bean)
                }
        }
    }

    /**
    网络请求相关函数
     */
    private suspend fun performFetch(bean: EmptyRoomBean) {
        _isLoading.value = true
        val bean = EmptyRoomBean(
            week = bean.week,
            weekday = bean.weekday,
            buildNum = bean.buildNum,
            section = bean.section.map { it - 1 }.sorted().joinToString(",")
        )
        val result = getEmptyRoomData(bean)
        _isLoading.value = false
        _roomResult.value = result.getOrNull() ?: emptyList()
    }

    /**
    网络请求函数
     */
    private suspend fun getEmptyRoomData(bean: EmptyRoomBean): Result<List<String>> {
        return runCatchingCoroutine {
            EmptyRoomApiService::class.impl().getEmpyRooms(
                weekday = bean.weekday,
                section = bean.section,
                buildNum = bean.buildNum,
                week = bean.week
            )
        }.mapCatching { it.data }
    }
}

/**
 * 用于接受数据compose函数改变的数据，并且做数据判断
 */
data class QueryParam(
    val week: Int?,
    val day: Int?,
    val build: Int?,
    val sections: List<Int>
) {
    fun isReady() = week != null && day != null && build != null && sections.isNotEmpty()
}