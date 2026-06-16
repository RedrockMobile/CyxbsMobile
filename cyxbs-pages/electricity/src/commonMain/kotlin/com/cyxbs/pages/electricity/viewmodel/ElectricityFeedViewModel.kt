package com.cyxbs.pages.electricity.viewmodel

import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.electricity.bean.ElecInf
import com.cyxbs.pages.electricity.config.BUILDING_NAMES
import com.cyxbs.pages.electricity.config.BUILDING_NAMES_HEADER
import com.cyxbs.pages.electricity.config.SP_BUILDING_FOOT_KEY
import com.cyxbs.pages.electricity.config.SP_BUILDING_HEAD_KEY
import com.cyxbs.pages.electricity.config.SP_ROOM_KEY
import com.cyxbs.pages.electricity.config.parseBuildingId
import com.cyxbs.pages.electricity.network.ElectricityApiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 电费 feed 卡片的展示状态。
 *
 * 把原先散落在 UI 里的「查询中 / 未绑定 / 无数据 / 有数据」收敛成单一状态，由
 * [ElectricityFeedViewModel] 唯一持有，避免多处 LaunchedEffect 各自改 hint 造成的
 * 残留文案与竞态。
 */
sealed interface ElectricityFeedUiState {
  /** 请求在途，对应「查询中...」 */
  data object Loading : ElectricityFeedUiState

  /** 没有可用的寝室绑定（默认拉取失败 / 后端无记忆），对应「先选择寝室吧~」 */
  data object Unbind : ElectricityFeedUiState

  /** 指定宿舍查不到数据（含请求失败），对应「暂无该宿舍电费数据」 */
  data object NoData : ElectricityFeedUiState

  /** 拿到有效数据 */
  data class Data(val elecInf: ElecInf) : ElectricityFeedUiState
}

/**
 * 电费查询 ViewModel（commonMain 版）
 *
 * 对外只暴露一个 [uiState]，UI 直接按状态渲染，无需自己拼装「加载 / 失败 / 空数据」逻辑。
 */
class ElectricityFeedViewModel : BaseViewModel() {

  private val _uiState = MutableStateFlow<ElectricityFeedUiState>(ElectricityFeedUiState.Loading)
  val uiState: StateFlow<ElectricityFeedUiState> = _uiState.asStateFlow()

  private val service by lazy { ElectricityApiService::class.impl() }

  // 当前在途的拉取任务。自动拉取(refresh/preGetCharge)在有任务时跳过，避免登录态重复
  // 发射导致并发重复请求；用户主动选寝室(getCharge)则取消旧任务，以最新一次为准。
  private var loadingJob: Job? = null

  /**
   * 登录后按本地缓存刷新：
   * - 已有数据则跳过，避免每次切 tab 都重查
   * - 没有本地缓存 / 解析失败 -> [preGetCharge]
   * - 本地缓存可解析出楼栋 + 寝室 -> [getCharge]
   */
  fun refresh() {
    if (uiState.value is ElectricityFeedUiState.Data) return
    val head = AccountSettings.now.getInt(SP_BUILDING_HEAD_KEY, -1)
    if (head == -1) {
      preGetCharge()
      return
    }
    val header = BUILDING_NAMES_HEADER.getOrNull(head)
    val foot = AccountSettings.now.getInt(SP_BUILDING_FOOT_KEY, -1)
    val label = header?.let { BUILDING_NAMES[it]?.getOrNull(foot) }
    val id = label?.let(::parseBuildingId).orEmpty()
    val room = AccountSettings.now.getString(SP_ROOM_KEY, "")
    if (id.isEmpty() || room.isEmpty()) {
      preGetCharge()
    } else {
      getCharge(id, room)
    }
  }

  /** 用户在弹窗里选定楼栋 + 寝室号后查询（用户主动行为，取消上一笔在途请求并复位加载态） */
  fun getCharge(building: String, room: String) {
    loadingJob?.cancel()
    loadingJob = launchByViewModelScope {
      _uiState.value = ElectricityFeedUiState.Loading
      runCatchingCoroutine {
        service.getElectricityInfo(building, room)
      }.onSuccess { info ->
        val elec = info.elecInf
        // 用户已指定宿舍，查不到（elec_inf 缺省或抄表数据为空）就是「暂无该宿舍电费数据」
        _uiState.value = if (elec != null && !elec.isEmpty()) {
          ElectricityFeedUiState.Data(elec)
        } else {
          ElectricityFeedUiState.NoData
        }
      }.onFailure {
        _uiState.value = ElectricityFeedUiState.NoData
      }
    }
  }

  /**
   * 首次进入页面 / 没有本地缓存时的默认拉取（依赖后端记忆的上次绑定）。
   * 自动触发，已有在途请求时直接跳过。
   */
  fun preGetCharge() {
    if (loadingJob?.isActive == true) return
    loadingJob = launchByViewModelScope {
      _uiState.value = ElectricityFeedUiState.Loading
      runCatchingCoroutine {
        service.getLastElectricityInfo()
      }.onSuccess { info ->
        val elec = info.elecInf
        // 后端没有可用绑定 -> 引导用户先选寝室
        _uiState.value = if (elec != null && !elec.isEmpty()) {
          ElectricityFeedUiState.Data(elec)
        } else {
          ElectricityFeedUiState.Unbind
        }
      }.onFailure {
        _uiState.value = ElectricityFeedUiState.Unbind
      }
    }
  }

  /** 切账号 / 退出登录时清空，回到「查询中...」初始态 */
  fun clear() {
    loadingJob?.cancel()
    loadingJob = null
    _uiState.value = ElectricityFeedUiState.Loading
  }
}
