package com.cyxbs.pages.noclass.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.mapCatchingCoroutine
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
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
 * description ： TODO:分组详情页 ViewModel
 * @author summer_palace2
 * @date 2026/5/3
 */
class GroupDetailViewModel(
    private val groupId: String,
    private val groupName: String,
) : BaseViewModel() {

    private val _currentGroup = MutableStateFlow(
        NoClassGroups(id = groupId, name = groupName, members = emptyList())
    )
    val currentGroup = _currentGroup.asStateFlow()

    private val _searchResult = MutableStateFlow<NoClassTemporarySearchs?>(null)
    val searchResult = _searchResult.asStateFlow()

    val searchText = mutableStateOf("")
    val showSearchDialog = mutableStateOf(false)
    val showHint = mutableStateOf(false)
    val hintText = mutableStateOf("试试左滑删除列表")

    private var hintJob: Job? = null

    init {
        fetchGroupMembers()
    }

    /** 从服务器获取最新的成员列表 */
    private fun fetchGroupMembers() {
        viewModelScope.launch {
            val result = runCatchingCoroutine {
                NoclassApiService::class.impl().getGroupAll()
            }.mapCatchingCoroutine { it.data }
            val serverGroup = result.getOrNull()?.find { it.id == groupId }
            if (serverGroup != null) {
                _currentGroup.value = serverGroup.copy(isOpen = false)
            }
        }
    }

    /* 搜索 */

    fun searchAll(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val result = runCatchingCoroutine {
                NoclassApiService::class.impl().searchAll(query)
            }.mapCatchingCoroutine { it.data }
            _searchResult.value = result.getOrNull()
            showSearchDialog.value = true
        }
    }

    fun dismissSearchDialog() {
        showSearchDialog.value = false
        _searchResult.value = null
        searchText.value = ""
    }

    /* 成员管理 */

    fun addMember(student: Students) {
        val current = _currentGroup.value
        val members = current.members?.toMutableList() ?: mutableListOf()
        if (members.none { it.id == student.id }) {
            members.add(student)
            _currentGroup.value = current.copy(members = members)
        }
        // 同步到服务器
        viewModelScope.launch {
            runCatchingCoroutine {
                NoclassApiService::class.impl().addGroupMember(groupId, student.id)
            }
        }
    }

    fun addClassMembers(students: List<Students>) {
        val current = _currentGroup.value
        val members = current.members?.toMutableList() ?: mutableListOf()
        students.forEach { student ->
            if (members.none { it.id == student.id }) members.add(student)
        }
        _currentGroup.value = current.copy(members = members)
        // 同步到服务器
        val nums = students.joinToString(",") { it.id }
        viewModelScope.launch {
            runCatchingCoroutine {
                NoclassApiService::class.impl().addGroupMember(groupId, nums)
            }
        }
    }

    fun deleteMember(student: Students) {
        viewModelScope.launch {
            val result = runCatchingCoroutine {
                NoclassApiService::class.impl().deleteGroupMember(groupId, student.id)
            }
            if (result.isSuccess && result.getOrNull()?.isSuccess() == true) {
                val current = _currentGroup.value
                _currentGroup.value = current.copy(
                    members = current.members?.filter { it.id != student.id }
                )
            }
        }
    }

    /* 提示 */

    fun showHintText(text: String) {
        hintJob?.cancel()
        hintText.value = text
        showHint.value = true
        hintJob = viewModelScope.launch {
            delay(2000)
            showHint.value = false
        }
    }
}
