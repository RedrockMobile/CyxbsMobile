package com.cyxbs.pages.map.model

import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.map.model.bean.ButtonInfo
import com.cyxbs.pages.map.model.bean.MapInfo
import com.cyxbs.pages.map.model.bean.PlaceDetails
import com.cyxbs.pages.map.model.bean.PlaceItem

/**
 * @Desc : 地图的本地数据存储仓库层
 * @Author : zzx
 * @Date : 2025/11/24 18:51
 */

object MapDataRepository {

  private val mapSettings = MapSettings()

  private const val SETTING_KEY_MAP_INFO = "map_info" // 地图基本信息
  private const val SETTING_KEY_MAP_BUTTON_INFO = "map_button_info" // 地图按钮信息
  private const val SETTING_KEY_MAP_VERSION = "map_version" // 地图版本信息
  private const val SETTING_KEY_MAP_PLACE_DETAIL = "map_place_detail" // 地点详细信息
  private const val SETTING_KEY_MAP_COLLECT_LIST = "map_collect_list" // 收藏列表
  private const val SETTING_KEY_MAP_SEARCH_HISTORY = "map_search_history" // 搜索历史

  /**
   * 保存地图信息
   */
  fun saveMapInfo(mapInfo: MapInfo) {
    mapSettings.putString(SETTING_KEY_MAP_INFO, defaultJson.encodeToString<MapInfo>(mapInfo))
  }

  /**
   * 拿取地图信息
   */
  fun getMapInfo(): MapInfo? {
    return mapSettings.getStringOrNull(SETTING_KEY_MAP_INFO)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<MapInfo>(json)
      }.onFailure {
        mapSettings.remove(SETTING_KEY_MAP_INFO)
        if (isDebug()) toast("地图信息转换异常, ${it.message}")
      }.getOrNull()
    }
  }

  /**
   * 保存按钮信息
   */
  fun saveButtonInfo(buttonInfo: ButtonInfo) {
    mapSettings.putString(SETTING_KEY_MAP_BUTTON_INFO, defaultJson.encodeToString<ButtonInfo>(buttonInfo))
  }

  /**
   * 拿取按钮信息
   */
  fun getButtonInfo(): ButtonInfo? {
    return mapSettings.getStringOrNull(SETTING_KEY_MAP_BUTTON_INFO)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<ButtonInfo>(json)
      }.onFailure {
        mapSettings.remove(SETTING_KEY_MAP_BUTTON_INFO)
      }.getOrNull()
    }
  }

  /**
   * 保存地图版本号
   */
  fun saveMapVersion(version: Long) {
    mapSettings.putLong(SETTING_KEY_MAP_VERSION, version)
  }

  /**
   * 拿取地图版本号
   */
  fun getMapVersion(): Long? {
    return mapSettings.getLongOrNull(SETTING_KEY_MAP_VERSION)
  }

  /**
   * 保存地点详细信息
   */
  fun savePlaceDetails(placeId: String, placeDetails: PlaceDetails) {
    mapSettings.putString(SETTING_KEY_MAP_PLACE_DETAIL + placeId, defaultJson.encodeToString<PlaceDetails>(placeDetails))
  }

  /**
   * 拿取地点详细信息
   */
  fun getPlaceDetails(placeId: String): PlaceDetails? {
    return mapSettings.getStringOrNull(SETTING_KEY_MAP_PLACE_DETAIL + placeId)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<PlaceDetails>(json)
      }.onFailure {
        mapSettings.remove(SETTING_KEY_MAP_PLACE_DETAIL + placeId)
      }.getOrNull()
    }
  }

  /**
   * 保存收藏信息
   */
  fun saveCollectList(list: List<String>) {
    mapSettings.putString(SETTING_KEY_MAP_COLLECT_LIST, defaultJson.encodeToString<List<String>>(list))
  }

  /**
   * 拿取收藏信息
   */
  fun getCollectList(): List<String>? {
    return mapSettings.getStringOrNull(SETTING_KEY_MAP_COLLECT_LIST)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<List<String>>(json)
      }.onFailure {
        mapSettings.remove(SETTING_KEY_MAP_COLLECT_LIST)
      }.getOrNull()
    }
  }

  /**
   * 保存搜索记录
   */
  fun saveSearchHistory(list: List<PlaceItem>) {
    mapSettings.putString(SETTING_KEY_MAP_SEARCH_HISTORY, defaultJson.encodeToString<List<PlaceItem>>(list))
  }

  /**
   * 拿取搜索记录
   */
  fun getSearchHistory(): List<PlaceItem>? {
    return mapSettings.getStringOrNull(SETTING_KEY_MAP_SEARCH_HISTORY)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<List<PlaceItem>>(json)
      }.onFailure {
        mapSettings.remove(SETTING_KEY_MAP_SEARCH_HISTORY)
      }.getOrNull()
    }
  }

}