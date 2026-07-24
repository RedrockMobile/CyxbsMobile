package com.cyxbs.pages.sport.viewModel

import androidx.lifecycle.viewModelScope
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.pages.sport.model.NoticeItem
import com.cyxbs.pages.sport.model.SportDetailBean
import com.cyxbs.pages.sport.model.SportRepository
import com.cyxbs.pages.sport.widget.SportDetailUiState
import com.cyxbs.pages.sport.widget.toDetailUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SportViewModel : BaseViewModel() {

    val noticeData: SharedFlow<Result<List<NoticeItem>>?> get() = _noticeData
    private val _noticeData = MutableSharedFlow<Result<List<NoticeItem>>?>(replay = 1)

    val sportData: SharedFlow<Result<SportDetailBean>?> get() = _sportData
    private val _sportData = MutableSharedFlow<Result<SportDetailBean>?>(replay = 1)

    val uiState: StateFlow<SportDetailUiState> get() = _uiState
    private val _uiState = MutableStateFlow<SportDetailUiState>(SportDetailUiState.Loading)

    val _isRefreshing: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> get() = _isRefreshing.asStateFlow()

    fun refresh(isFirstLoading: Boolean = false): Boolean {
        if (_isRefreshing.value) return false

        _isRefreshing.value = true

        val week = SchoolCalendar.getWeekOfTerm() ?: 22
        viewModelScope.launch {
            if (isFirstLoading) {
                _uiState.value = SportDetailUiState.Loading
            }
            SportRepository.getSportDetailData()
                .onSuccess { bean ->
                    _uiState.value = bean.toDetailUiState()
                }
                .onFailure {
                    _uiState.value = SportDetailUiState.Error
                    logg("${it.stackTraceToString()}")
                }
            _isRefreshing.value = false
        }

        return true
    }

    init {
        IAccountService::class.impl().state
            .onEach {
                when (it) {
                    is AccountState.Login -> refresh(isFirstLoading = true)
                    is AccountState.Logout -> _sportData.emit(null)
                    else -> Unit
                }
            }.launchIn(viewModelScope)
    }

    init {
        getNoticeInfo()
    }

    fun getNoticeInfo() {
        viewModelScope.launch {
            SportRepository.getSportNoticeData()
                .onSuccess { _noticeData.emit(Result.success(it)) }
                .onFailure { _noticeData.emit(Result.failure(it)) }
        }
    }
}
