package com.cyxbs.pages.emptyroom.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.cyxbs.pages.emptyroom.bean.EmptyRoomBean
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import com.cyxbs.pages.emptyroom.network.EmptyRoomApiService
import kotlin.time.Clock

/**
 * description ： TODO:空教室页的ViewModel
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/3/3 16:10
 */

@OptIn(FlowPreview::class)
class EmptyRoomComposeViewModel : BaseViewModel() {

    //访问日历
    //几周
    var week = SchoolCalendar.getWeekOfTerm() ?: 0

    //星期几
    private fun getInitialDayOfWeek(): Int {
        val now = Clock.System.now()
        val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        return localDateTime.dayOfWeek.isoDayNumber
    }

    //管理状态
    var selectedWeek by mutableStateOf<Int?>(week)
    var selectedWeekDayNum by mutableStateOf<Int?>(getInitialDayOfWeek())
    var selectedBuildNum by mutableStateOf<Int?>(2)
    val selectedSections = mutableStateListOf<Int>().apply { add(1) }//节次是多选
    //返回数据
    var roomResult by mutableStateOf<List<String>?>(null)
    //判断加载状态
    var isLoading by mutableStateOf(false)

    init {
        viewModelScope.launch {
            //snapshotFlow是冷流，当QueryParam内的参数发生变化，才会发射流
            snapshotFlow {
                QueryParam(
                    week = selectedWeek,
                    day = selectedWeekDayNum,
                    build = selectedBuildNum,
                    //sections.toList() 是为了给MutableStateList拍一张快照。因为List内容变了但引用没变,必须转成不可变的List,下游的比较逻辑才有效
                    sections = selectedSections.toList()
                )
            }
                //只有当“周、周几、大课节次、教学楼”这四个参数全部不为空时，才允许进入下一步。这保证了不会发送无效请求。
                .filter { it.isReady() }
                //比较逻辑:只有当参数真正改变时才触发
                .distinctUntilChanged()
                //防抖:300ms后才发请求，防止快速滑动、
                .debounce(300)
                //如果新请求来了，旧请求还在跑，这里直接取消旧的
                .collectLatest { param ->
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
        isLoading = true
        val result = getEmptyRoomData(bean)

        isLoading = false
        //如果成功，取回 List；如果失败（网络错误等），赋值为空列表
        roomResult = result.getOrNull() ?: emptyList()

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