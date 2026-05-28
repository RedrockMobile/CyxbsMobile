package com.cyxbs.pages.course.find.viewmodel

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.api.ILinkService2
import com.cyxbs.pages.course.find.bean.FindStuBean
import com.cyxbs.pages.course.find.bean.FindStuHistoryEntity
import com.cyxbs.pages.course.find.model.FindHistoryRepository
import com.cyxbs.pages.course.find.network.FindApiService
import com.cyxbs.pages.course.model.LinkLessonRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

/**
 * 查找他人课表 ViewModel
 *
 * 搜索为单条响应式 flow：[query] → debounce → distinct → flatMapLatest 触发请求 →
 * 直接 stateIn 发布为 [searchState]。flatMapLatest 保证旧请求在新关键字到达时自动取消，
 * 不会卡在 Loading。
 *
 * @author 985892345
 * @date 2026/5/27
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class FindCourseViewModel : BaseViewModel() {

  /** 当前搜索关键字（受 UI 文本框双向绑定） */
  val queryTextFieldState = TextFieldState()

  /** 历史记录 */
  val history: StateFlow<List<FindStuHistoryEntity>> = FindHistoryRepository.state

  /** 关联人状态 */
  val linkState: StateFlow<ILinkService2.LinkStu> = LinkLessonRepository.state

  /** 搜索结果状态：由 query 一路 flow 而来，网络请求通过 onStart/map/catch 整合在 flow 中 */
  val searchState: StateFlow<SearchState> = snapshotFlow { queryTextFieldState.text }
    .debounce { if (it.isEmpty()) 0.milliseconds else 300.milliseconds }
    .map { it.trim() }
    .distinctUntilChanged()
    .flatMapLatest { q -> searchFlow(q.toString()) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, SearchState.Idle)

  private fun searchFlow(q: String): Flow<SearchState> {
    if (q.isEmpty()) return flowOf(SearchState.Idle)
    return flow { emit(FindApiService::class.impl().getStudents(q)) }
      .map { wrapper ->
        wrapper.throwApiExceptionIfFail()
        wrapper.data
      }
      .map { list ->
        if (list.isEmpty()) SearchState.Empty else SearchState.Success(list)
      }
      .onStart { emit(SearchState.Loading) }
      .catch { e -> emit(SearchState.Error(e.message ?: "网络似乎开小差了")) }
  }

  /** 选中一个搜索结果时调用，写入历史 */
  fun rememberSelection(bean: FindStuBean) {
    FindHistoryRepository.add(FindStuHistoryEntity(name = bean.name, stuNum = bean.stuNum))
  }

  fun deleteHistory(stuNum: String) {
    FindHistoryRepository.delete(stuNum)
  }

  fun changeLink(stuNum: String, toast: String? = null) {
    LinkLessonRepository.changeLinkStu(stuNum, toast)
  }

  fun deleteLink() {
    LinkLessonRepository.deleteLink()
  }

  fun setQuery(text: String) {
    queryTextFieldState.setTextAndPlaceCursorAtEnd(text)
  }

  @Stable
  sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data object Empty : SearchState
    data class Success(val list: List<FindStuBean>) : SearchState
    data class Error(val message: String) : SearchState
  }
}
