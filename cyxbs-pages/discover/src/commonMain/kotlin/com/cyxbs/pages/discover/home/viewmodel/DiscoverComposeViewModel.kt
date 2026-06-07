package com.cyxbs.pages.discover.home.viewmodel

import androidx.lifecycle.viewModelScope
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.defaultSettings
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.discover.home.bean.JwNewsItemBean
import com.cyxbs.pages.discover.home.bean.RollerBannerBean
import com.cyxbs.pages.discover.home.network.DiscoverApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 发现首页 ViewModel（commonMain）
 *
 * - banner 列表来自 [DiscoverApiService.getBanner]
 * - 教务在线新闻来自 [DiscoverApiService.getJwNews]
 * - 功能按钮 pin 顺序的本地持久化也放在这里
 */
class DiscoverComposeViewModel : BaseViewModel() {

  private val _banner = MutableStateFlow<List<RollerBannerBean>>(emptyList())
  val banner: StateFlow<List<RollerBannerBean>> = _banner.asStateFlow()

  private val _jwNews = MutableStateFlow<List<JwNewsItemBean>>(emptyList())
  val jwNews: StateFlow<List<JwNewsItemBean>> = _jwNews.asStateFlow()

  init {
    refreshBanner()
    refreshJwNews()
  }

  private fun refreshBanner() {
    viewModelScope.launch {
      runCatchingCoroutine {
        DiscoverApiService::class.impl().getBanner()
      }.mapCatching { it.data }.onSuccess {
        _banner.value = it
      }
    }
  }

  private fun refreshJwNews() {
    viewModelScope.launch {
      runCatchingCoroutine {
        DiscoverApiService::class.impl().getJwNews(1)
      }.mapCatching { it.data }.onSuccess {
        _jwNews.value = it
      }
    }
  }

  // ---------------------- 顺序持久化 ----------------------

  /**
   * 读取持久化的 id 顺序。返回 null 表示从未保存过，由调用方使用默认顺序。
   */
  fun loadSavedOrder(key: String): List<String>? {
    val raw = defaultSettings.getStringOrNull(key) ?: return null
    if (raw.isEmpty()) return emptyList()
    return raw.split(SEPARATOR)
  }

  /**
   * 保存 id 顺序。一旦保存，初始顺序配置就被覆盖；后续新增的 id 会按
   * 「在新增加入时的初始位置」插入到已保存顺序中。
   */
  fun saveOrder(key: String, ids: List<String>) {
    defaultSettings.putString(key, ids.joinToString(SEPARATOR))
  }

  companion object {
    /** 功能按钮 pin 顺序持久化 key（双击置顶） */
    const val KEY_FUNCTION_PINS = "discover_function_pins"

    /** id 之间使用罕见字符分隔，避免与业务 id 冲突 */
    private const val SEPARATOR = ""
  }
}
