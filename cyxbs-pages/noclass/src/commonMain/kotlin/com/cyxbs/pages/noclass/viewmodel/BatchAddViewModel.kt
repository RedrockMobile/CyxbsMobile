package com.cyxbs.pages.noclass.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.mapCatchingCoroutine
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.noclass.bean.NoClassBatchResponseInfo
import com.cyxbs.pages.noclass.network.NoclassApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CheckState {
    data object Idle : CheckState()
    data object Checking : CheckState()
    data class HasRepeat(val repeatList: List<NoClassBatchResponseInfo.BatchStudent>) : CheckState()
    data class Error(val errList: List<String>) : CheckState()
    data class Ready(val students: List<Pair<String, String>>) : CheckState()
    data object NoResult : CheckState()
}

class BatchAddViewModel : BaseViewModel() {

    val inputText = mutableStateOf("")

    private val _checkState = MutableStateFlow<CheckState>(CheckState.Idle)
    val checkState = _checkState.asStateFlow()

    private val _preparedStudents = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val preparedStudents = _preparedStudents.asStateFlow()

    private var tempNormalList = mutableListOf<Pair<String, String>>()
    private var tempStuNumList = mutableListOf<String>()

    fun checkUpload(contentList: List<String>) {
        viewModelScope.launch {
            _checkState.value = CheckState.Checking
            val result = runCatchingCoroutine {
                NoclassApiService::class.impl().checkUploadInfo(contentList)
            }.mapCatchingCoroutine { it.data }
            val data = result.getOrNull()
            if (data == null) {
                _checkState.value = CheckState.NoResult
                return@launch
            }
            if (data.isWrong && data.errList.isNotEmpty()) {
                _checkState.value = CheckState.Error(data.errList)
                return@launch
            }
            tempNormalList.clear()
            tempStuNumList.clear()
            data.normal?.forEach {
                tempNormalList.add(it.id to it.name)
                tempStuNumList.add(it.id)
            }
            if (!data.repeat.isNullOrEmpty()) {
                _checkState.value = CheckState.HasRepeat(data.repeat)
            } else if (tempNormalList.isNotEmpty()) {
                _checkState.value = CheckState.Ready(tempNormalList.toList())
            } else {
                _checkState.value = CheckState.NoResult
            }
        }
    }

    fun selectRepeatStudents(selected: List<NoClassBatchResponseInfo.BatchStudent>) {
        tempNormalList.addAll(selected.map { it.id to it.name })
        selected.forEach { tempStuNumList.add(it.id) }
        _preparedStudents.value = tempNormalList.toList()
    }

    fun clearState() {
        _checkState.value = CheckState.Idle
        tempNormalList.clear()
        tempStuNumList.clear()
        inputText.value = ""
    }
}
