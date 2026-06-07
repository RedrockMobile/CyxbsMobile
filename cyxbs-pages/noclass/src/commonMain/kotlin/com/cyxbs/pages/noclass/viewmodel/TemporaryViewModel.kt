package com.cyxbs.pages.noclass.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.mapCatchingCoroutine
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.noclass.bean.Clss
import com.cyxbs.pages.noclass.bean.NoClassGroups
import com.cyxbs.pages.noclass.bean.NoClassTemporarySearchs
import com.cyxbs.pages.noclass.bean.Students
import com.cyxbs.pages.noclass.network.NoclassApiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * description ： TODO:临时分组页的ViewModel
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/3/3 16:10
 */

class TemporaryViewModel : BaseViewModel() {

    //学生列表（可直接被 LazyColumn 使用）
    val tempStudentList = mutableStateListOf<Students>()

    //简单标志用 mutableStateOf，不用 by 委托
    val tempSearchText = mutableStateOf("")
    val showTempSearchDialog = mutableStateOf(false)
    val showTempHint = mutableStateOf(true)
    val tempHintText = mutableStateOf("试试左滑删除列表")

    private var hintJob: Job? = null

    //网络相关状态用 StateFlow
    private val _tempSearchResult = MutableStateFlow<NoClassTemporarySearchs?>(null)
    val tempSearchResult = _tempSearchResult.asStateFlow()

    private val _tempIsSearching = MutableStateFlow(false)
    val tempIsSearching = _tempIsSearching.asStateFlow()

    private val _isSheetExpanded = MutableStateFlow(false)
    val isSheetExpanded = _isSheetExpanded.asStateFlow()

    init {
        val user = IAccountService::class.impl().userInfo
        if (user != null) {
            tempStudentList.add(Students(stunum1 = user.stuNum, name1 = user.username))
        }
    }

    /**
     * 更新临时分组页搜索框的文字内容
     * @param text 输入框当前内容
     */
    fun onTempSearchTextChange(text: String) {
        tempSearchText.value = text
    }

    /**
     * 混合搜索：同时搜索学生、班级和自定义分组
     * @param query 关键词（学号、姓名、班级号等）
     */
    fun searchAll(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _tempIsSearching.value = true
            val result = runCatchingCoroutine {
                NoclassApiService::class.impl().searchAll(query)
            }.mapCatchingCoroutine { it.data }
            _tempIsSearching.value = false
            _tempSearchResult.value = result.getOrNull()
            showTempSearchDialog.value = true
        }
    }

    /**
     * 清空当前搜索框的文字
     */
    fun clearSearchText() {
        tempSearchText.value = ""
    }

    /**
     * 关闭混合搜索结果弹窗并清空状态
     */
    fun dismissTempSearchDialog() {
        showTempSearchDialog.value = false
        _tempSearchResult.value = null
        tempSearchText.value = ""
    }

    /**
     * 将单个学生添加到临时查询列表中（会自动去重）
     * @param student 学生对象
     */
    fun addTempStudent(student: Students) {
        if (tempStudentList.none { it.id == student.id }) tempStudentList.add(student)
    }

    /**
     * 将整个班级的所有成员添加到临时查询列表中（自动去重）
     * @param clss 班级对象，包含成员列表
     */
    fun addTempClass(clss: Clss) {
        val members = clss.members ?: return
        val currentIds = tempStudentList.map { it.id }.toSet()
        members.forEach { student ->
            if (student.id !in currentIds) {
                tempStudentList.add(student)
            }
        }
    }

    /**
     * 将某个固定分组的所有成员添加到临时查询列表中（自动去重）
     * @param group 固定分组对象，包含成员列表
     */
    fun addTempGroup(group: NoClassGroups) {
        val members = group.members ?: return
        val currentIds = tempStudentList.map { it.id }.toSet()
        members.forEach { student ->
            if (student.id !in currentIds) {
                tempStudentList.add(student)
            }
        }
    }

    /**
     * 从当前临时查询列表中移除某个学生
     * @param id 学生的唯一学号
     */
    fun removeTempStudent(id: String) {
        tempStudentList.removeAll { it.id == id }
    }

    /**
     * 在页面底部显示一段自动消失的提示文字（针对临时分组页）
     * @param text 提示内容
     */
    fun showTempHintText(text: String) {
        hintJob?.cancel()
        tempHintText.value = text
        showTempHint.value = true
        hintJob = viewModelScope.launch {
            delay(2000)
            showTempHint.value = false
        }
    }

    /**
     * 执行查询空闲课表的操作
     * 逻辑：如果列表不为空，则展开底部的 BottomSheet 展示查询结果
     */
    fun queryCourse() {
        if (tempStudentList.isEmpty()) return
        _isSheetExpanded.value = true
    }

    /**
     * 当课表 BottomSheet 被收起时调用（用户下滑/返回键），重置展开状态
     * 确保下次点击"查询"按钮能再次触发 LaunchedEffect
     */
    fun dismissQuerySheet() {
        _isSheetExpanded.value = false
    }
}


