package com.cyxbs.pages.noclass.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.log
import com.cyxbs.components.utils.extensions.mapCatchingCoroutine
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.noclass.bean.NoClassGroups
import com.cyxbs.pages.noclass.bean.Students
import com.cyxbs.pages.noclass.network.NoclassApiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * description ： TODO:固定分组页的ViewModel
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/5/3 13:10
 */
class SolidViewModel : BaseViewModel() {
    companion object {
        const val TAB_SOLID = 1
    }

    //网络/列表状态用 StateFlow
    private val _groupList = MutableStateFlow<List<NoClassGroups>>(emptyList())
    val groupList = _groupList.asStateFlow()

    private val _groupLoading = MutableStateFlow(false)
    val groupLoading = _groupLoading.asStateFlow()

    private val _solidSearchResult = MutableStateFlow<List<Students>?>(null)
    val solidSearchResult = _solidSearchResult.asStateFlow()

    private val _createGroupResult = MutableStateFlow<String?>(null)
    val createGroupResult = _createGroupResult.asStateFlow()

    // 单标志用 mutableStateOf，不用 by 委托
    val solidSearchText = mutableStateOf("")
    val showSolidSearchDialog = mutableStateOf(false)
    val showSolidHint = mutableStateOf(false)
    val solidHintText = mutableStateOf("试试左滑删除列表")
    val showAddToGroupDialog = mutableStateOf(false)
    val showCreateGroupDialog = mutableStateOf(false)
    val pendingStudent = mutableStateOf<Students?>(null)

    private var solidHintJob: Job? = null

    /**
     * 获取用户所有的固定分组列表
     * 内部逻辑：获取数据后会结合本地已有的顺序进行二次排序，确保置顶操作的即时视觉反馈。
     */
    fun getAllGroup() {
        viewModelScope.launch {
            _groupLoading.value = true
            val result = runCatchingCoroutine {
                NoclassApiService::class.impl().getGroupAll()
            }.mapCatchingCoroutine { it.data }
            val serverList = result.getOrNull() ?: emptyList()
            // 记住当前本地显示的 ID 顺序，用于保持“最后操作在最前”的体验
            val localOrderMap =
                _groupList.value.mapIndexed { index, group -> group.id to index }.toMap()

            _groupList.value = serverList.map { it.copy(isOpen = false) }
                .sortedWith(
                    compareByDescending<NoClassGroups> { it.isTop }
                        .thenBy { localOrderMap[it.id] ?: Int.MAX_VALUE }
                )
            _groupLoading.value = false
        }
    }

    /**
     * 创建一个新的固定分组
     * @param name 分组的名称
     * @param stuNums 初始加入的成员学号，多个学号用逗号隔开，默认为空
     */
    fun createGroup(name: String, stuNums: String = "") {
        if (_groupLoading.value) return
        viewModelScope.launch {
            _groupLoading.value = true
            val result = runCatchingCoroutine {
                NoclassApiService::class.impl().postGroup(name, stuNums)
            }
            result.onSuccess { wrapper ->
                log("NoClassDebug", "Wrapper: $wrapper")
                if (wrapper.info == "exist") {
                    _createGroupResult.value = "-2" // 标志位：名称重复
                } else {
                    getAllGroup() // 成功后刷新列表
                    _createGroupResult.value = "success" // 标志位：创建成功
                }
            }.onFailure {
                _createGroupResult.value = "-1" // 标志位：网络或未知异常
            }
            _groupLoading.value = false
        }
    }

    /**
     * 清除创建分组的结果状态
     * 用于在弹窗处理完逻辑后重置 StateFlow，防止下次进入重复触发
     */
    fun clearCreateGroupResult() {
        _createGroupResult.value = null
        showCreateGroupDialog.value = true
    }

    /**
     * 创建成功后的统一收尾操作
     * 内部逻辑：关闭创建弹窗并清除结果标志位
     */
    fun dismissAfterCreate() {
        clearCreateGroupResult()
        dismissCreateGroupDialog()
    }

    /**
     * 删除指定的固定分组
     * @param groupId 需要删除的分组唯一 ID
     */
    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            val result = runCatchingCoroutine {
                NoclassApiService::class.impl().deleteGroup(groupId)
            }
            if (result.isSuccess && result.getOrNull()?.isSuccess() == true) {
                // 成功后直接在本地列表过滤掉，无需重新请求全量接口，体验更流畅
                _groupList.value = _groupList.value.filter { it.id != groupId }
            }
        }
    }

    /**
     * 切换分组的置顶状态（置顶或取消置顶）
     * @param groupId 分组 ID
     * @param name 分组名称（接口要求必传）
     * @param isTop 当前期望的目标状态：true 为置顶，false 为取消
     */
    fun toggleTopGroup(groupId: String, name: String, isTop: Boolean) {
        viewModelScope.launch {
            val result = runCatchingCoroutine {
                NoclassApiService::class.impl().updateGroup(
                    groupId, name, if (isTop) "1" else "0"
                )
            }
            if (result.isSuccess && result.getOrNull()?.isSuccess() == true) {
                val newList = _groupList.value.toMutableList()
                val index = newList.indexOfFirst { it.id == groupId }
                if (index != -1) {
                    val updatedItem = newList.removeAt(index).copy(isTop = isTop, isOpen = false)
                    // 逻辑：置顶插到 Index 0，取消置顶插到末尾，对标后端逻辑
                    if (isTop) {
                        newList.add(0, updatedItem)
                    } else {
                        newList.add(updatedItem)
                    }
                    _groupList.value = newList
                }
            } else {
                showSolidHintText("操作失败，请检查网络")
            }
        }
    }

    /**
     * 向现有分组中批量添加成员
     * @param groupId 分组 ID
     * @param students 需要添加的学生对象列表
     */
    fun addMembers(groupId: String, students: List<Students>) {
        val nums = students.joinToString(",") { it.id }
        viewModelScope.launch {
            val result = runCatchingCoroutine {
                NoclassApiService::class.impl().addGroupMember(groupId, nums)
            }
            result.onSuccess {
                "添加成功".toast()
                getAllGroup() // 刷新列表以显示最新成员数
            }.onFailure {
                "添加失败，请检查网络".toast()
            }
        }
    }

    /**
     * 更新固定分组页搜索框的文字内容
     * @param text 输入框当前内容
     */
    fun onSolidSearchTextChange(text: String) {
        solidSearchText.value = text
    }

    /**
     * 搜索单个学生（通过学号或姓名）
     * @param query 搜索关键词
     */
    fun searchStudent(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val result = runCatchingCoroutine {
                NoclassApiService::class.impl().searchPeople(query)
            }.mapCatchingCoroutine { it.data }
            val list = result.getOrNull() ?: emptyList()
            _solidSearchResult.value = list
            showSolidSearchDialog.value = true
        }
    }

    /**
     * 关闭搜索学生的结果弹窗并清空搜索状态
     */
    fun dismissSolidSearchDialog() {
        showSolidSearchDialog.value = false
        _solidSearchResult.value = null
        solidSearchText.value = ""
    }

    /**
     * 打开“添加到分组”的对话框
     * @param student 准备被添加的学生对象
     */
    fun openAddToGroupDialog(student: Students) {
        pendingStudent.value = student
        showAddToGroupDialog.value = true
    }

    /**
     * 关闭“添加到分组”的对话框
     */
    fun dismissAddToGroupDialog() {
        showAddToGroupDialog.value = false
        pendingStudent.value = null
    }

    /**
     * 打开创建新分组的弹窗
     */
    fun openCreateGroupDialog() {
        //打开弹窗前清空上一次的结果，防止“秒关”
        clearCreateGroupResult()
        showCreateGroupDialog.value = true
    }

    /**
     * 关闭创建新分组的弹窗
     */
    fun dismissCreateGroupDialog() {
        showCreateGroupDialog.value = false
    }

    /**
     * 在页面底部显示一段自动消失的提示文字
     * @param text 提示的内容
     */
    fun showSolidHintText(text: String) {
        solidHintJob?.cancel()
        solidHintText.value = text
        showSolidHint.value = true
        solidHintJob = viewModelScope.launch {
            delay(2000)
            showSolidHint.value = false
        }
    }
}
