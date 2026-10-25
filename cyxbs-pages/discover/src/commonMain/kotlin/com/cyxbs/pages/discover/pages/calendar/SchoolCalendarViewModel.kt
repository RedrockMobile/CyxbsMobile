package com.cyxbs.pages.discover.pages.calendar

import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.discover.home.bean.SchoolCalendarCdnBean
import com.cyxbs.pages.discover.home.network.DiscoverApiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 校历页面状态管理。
 *
 * 页面先读取上一次成功请求保存的 CDN 元数据，以便离线时继续展示 Coil 磁盘缓存；随后请求最新元数据。
 * 最新 URL 或版本变化后，UI 使用的新缓存键会强制 Coil 重新下载图片。
 */
class SchoolCalendarViewModel : BaseViewModel() {

  /** 对外仅暴露只读状态流，类内通过显式 backing field 保留可变能力。 */
  val uiState: StateFlow<SchoolCalendarUiState>
    field = MutableStateFlow<SchoolCalendarUiState>(
      readCachedCalendar()?.let {
        SchoolCalendarUiState.Content(
          calendar = it,
          isRefreshing = true,
        )
      } ?: SchoolCalendarUiState.Loading,
    )

  private var refreshJob: Job? = null

  init {
    refresh()
  }

  /**
   * 请求最新校历元数据。
   *
   * 重复调用会取消上一轮请求。请求失败时优先保留已经展示的缓存内容；没有任何缓存时才进入错误态。
   */
  fun refresh() {
    refreshJob?.cancel()
    refreshJob = viewModelScope.launch {
      val fallback = (uiState.value as? SchoolCalendarUiState.Content)?.calendar
        ?: readCachedCalendar()
      uiState.value = fallback?.let {
        SchoolCalendarUiState.Content(
          calendar = it,
          isRefreshing = true,
        )
      } ?: SchoolCalendarUiState.Loading

      runCatchingCoroutine {
        DiscoverApiService::class.impl().getSchoolCalendarCdn()
      }.mapCatching { wrapper ->
        wrapper.data.also {
          require(it.cdnUrl.isNotBlank()) { "School calendar CDN URL is blank" }
        }
      }.onSuccess { latest ->
        saveCachedCalendar(latest)
        uiState.value = SchoolCalendarUiState.Content(
          calendar = latest,
          isRefreshing = false,
        )
      }.onFailure {
        uiState.value = fallback?.let {
          SchoolCalendarUiState.Content(
            calendar = it,
            isRefreshing = false,
          )
        } ?: SchoolCalendarUiState.Error
      }
    }
  }

  /**
   * 读取设备维度的校历元数据缓存。
   *
   * URL 与版本缺少任意一个都视为无缓存，保证恢复出来的图片缓存身份字段完整。
   */
  private fun readCachedCalendar(): SchoolCalendarCdnBean? {
    val url = defaultSettings.getStringOrNull(KEY_CDN_URL)?.takeIf(String::isNotBlank)
      ?: return null
    val version = defaultSettings.getIntOrNull(KEY_PIC_VERSION) ?: return null
    return SchoolCalendarCdnBean(cdnUrl = url, picVersion = version)
  }

  /**
   * 保存最后一次成功返回的元数据。
   *
   * 仅保存用于恢复缓存身份的 URL 与版本号；图片二进制仍由 Coil 管理，避免把大图片写进简单 KV。
   */
  private fun saveCachedCalendar(calendar: SchoolCalendarCdnBean) {
    defaultSettings.putString(KEY_CDN_URL, calendar.cdnUrl)
    defaultSettings.putInt(KEY_PIC_VERSION, calendar.picVersion)
  }

  private companion object {
    const val KEY_CDN_URL = "discover_school_calendar_cdn_url"
    const val KEY_PIC_VERSION = "discover_school_calendar_pic_version"
  }
}

/**
 * 校历页面可观察状态。
 */
sealed interface SchoolCalendarUiState {

  /** 首次进入且没有可用缓存。 */
  data object Loading : SchoolCalendarUiState

  /**
   * 可展示的校历元数据。
   *
   * [isRefreshing] 为 true 时仍可显示缓存图片，同时在页面上提示后台刷新。
   */
  data class Content(
    val calendar: SchoolCalendarCdnBean,
    val isRefreshing: Boolean,
  ) : SchoolCalendarUiState

  /** 元数据请求失败，且本地也没有可用缓存。 */
  data object Error : SchoolCalendarUiState
}
