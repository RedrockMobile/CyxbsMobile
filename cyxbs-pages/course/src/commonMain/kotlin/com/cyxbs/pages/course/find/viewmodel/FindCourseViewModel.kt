package com.cyxbs.pages.course.find.viewmodel

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.network.ApiException
import com.cyxbs.pages.course.api.ILinkService2
import com.cyxbs.pages.course.find.bean.FindStuBean
import com.cyxbs.pages.course.find.bean.FindStuHistoryEntity
import com.cyxbs.pages.course.find.model.FindHistoryRepository
import com.cyxbs.pages.course.find.network.FindApiService
import com.cyxbs.pages.course.model.LinkLessonRepository
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.milliseconds

private const val SEARCH_REQUEST_TOO_FREQUENT_STATUS = 40003
private const val SEARCH_RETRY_INITIAL_DELAY_MS = 500L
private const val SEARCH_RETRY_MAX_DELAY_MS = 4_000L

/**
 * 查找他人课表 ViewModel
 *
 * 搜索为单条响应式 flow：[queryTextFieldState] → debounce → distinct → flatMapLatest 查询缓存或触发请求 →
 * 直接 stateIn 发布为 [searchState]。flatMapLatest 保证旧请求在新关键字到达时自动取消；普通异常通过
 * [toastEvent] 通知页面并恢复请求前内容，限流响应则在请求层自动退避重试，上层始终保持 Loading。
 *
 * @author 985892345
 * @date 2026/5/27
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class FindCourseViewModel : BaseViewModel() {

  /** 按标准化搜索词缓存成功或空结果，避免重复请求后端 */
  private val searchCache = mutableMapOf<String, SearchState>()

  /** 最近一次稳定展示的内容，用于普通请求异常时恢复页面 */
  private var retainedSearchState: SearchState = SearchState.Idle

  private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)

  /** 普通搜索异常的一次性提示事件；限流异常不会发送该事件 */
  val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

  /** 当前搜索关键字（受 UI 文本框双向绑定） */
  val queryTextFieldState = TextFieldState()

  /** 历史记录 */
  val history: StateFlow<List<FindStuHistoryEntity>> = FindHistoryRepository.state

  /** 关联人状态 */
  val linkState: StateFlow<ILinkService2.LinkStu> = LinkLessonRepository.state

  /** 搜索结果状态：由 query 一路 flow 而来，缓存命中时不会进入网络请求 */
  val searchState: StateFlow<SearchState> = snapshotFlow { queryTextFieldState.text }
    .debounce { if (it.isEmpty()) 0.milliseconds else 500.milliseconds }
    .map { it.trim() }
    .distinctUntilChanged()
    .flatMapLatest { q -> searchFlow(q.toString()) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, SearchState.Idle)

  /**
   * 查询单个标准化搜索词。
   *
   * 缓存只记录后端已确认的成功/空结果；普通异常恢复本次请求前的页面内容并发出 toast，业务状态
   * 40003 表示请求被后端限流，此时不提示错误，并在当前请求中自动退避重试。重试期间不产生新的页面状态，
   * 上层会保持转圈；搜索词变化时 [flatMapLatest] 会取消等待和旧请求。
   */
  private fun searchFlow(q: String): Flow<SearchState> {
    if (q.isEmpty()) {
      retainedSearchState = SearchState.Idle
      return flowOf(SearchState.Idle)
    }
    searchCache[q]?.let { cachedState ->
      retainedSearchState = cachedState
      return flowOf(cachedState)
    }
    val stateBeforeRequest = retainedSearchState
    return flow { emit(FindApiService::class.impl().getStudents(q)) }
      .map { wrapper ->
        wrapper.throwApiExceptionIfFail()
        wrapper.data
      }
      .retryWhen { throwable, attempt ->
        if (throwable.isRequestTooFrequent()) {
          delay(searchRetryDelayMillis(attempt).milliseconds)
          true
        } else {
          false
        }
      }
      .map { list ->
        if (list.isEmpty()) SearchState.Empty else SearchState.Success(list)
      }
      .map { state ->
        searchCache[q] = state
        retainedSearchState = state
        state
      }
      .onStart { emit(SearchState.Loading) }
      .catch { throwable ->
        _toastEvent.emit(throwable.toSearchToastMessage())
        emit(stateBeforeRequest)
      }
  }

  /**
   * 计算限流重试间隔：500ms、1s、2s、4s，之后保持 4s，避免持续限流时频繁请求后端。
   */
  private fun searchRetryDelayMillis(attempt: Long): Long {
    val multiplier = 1L shl attempt.coerceAtMost(3).toInt()
    return (SEARCH_RETRY_INITIAL_DELAY_MS * multiplier).coerceAtMost(SEARCH_RETRY_MAX_DELAY_MS)
  }

  /**
   * 从业务异常或 HTTP 异常响应体中识别后端限流状态。
   *
   * HTTP 状态码不是判断依据；即使传输层返回 429，也只认响应 JSON 中的业务 `status=40003`。
   */
  private suspend fun Throwable.isRequestTooFrequent(): Boolean = when (this) {
    is ApiException -> status == SEARCH_REQUEST_TOO_FREQUENT_STATUS
    is ResponseException -> responseBusinessStatus() == SEARCH_REQUEST_TOO_FREQUENT_STATUS
    else -> false
  }

  /** 读取 Ktor 异常携带的响应体并提取业务状态，解析失败时按普通异常处理。 */
  private suspend fun ResponseException.responseBusinessStatus(): Int? {
    val body = runCatching { response.bodyAsText() }.getOrNull()
      ?.takeIf { it.isNotBlank() }
      ?: message.orEmpty().substringAfterLast(". Text: ", missingDelimiterValue = "")
    return runCatching {
      defaultJson.parseToJsonElement(body).jsonObject["status"]?.jsonPrimitive?.int
    }.getOrNull()
  }

  /** 优先展示后端业务提示，其余异常沿用原异常信息。 */
  private fun Throwable.toSearchToastMessage(): String = when (this) {
    is ApiException -> info
    else -> message ?: "网络似乎开小差了"
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
  }
}
