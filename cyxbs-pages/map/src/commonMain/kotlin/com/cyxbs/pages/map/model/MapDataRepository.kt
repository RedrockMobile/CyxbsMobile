package com.cyxbs.pages.map.model

import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.map.model.bean.ButtonInfo
import com.cyxbs.pages.map.model.bean.MapInfo

/**
 * @Desc : 地图的本地数据存储仓库层
 * @Author : zzx
 * @Date : 2025/11/24 18:51
 */

object MapDataRepository {

  val mapSettings = MapSettings()

  private const val SETTING_KEY_MAP_INFO = "map_info" // 地图基本信息
  private const val SETTING_KEY_MAP_BUTTON_INFO = "map_button_info" // 地图按钮信息

  /**
   * 保存地图信息
   */
  fun saveMapInfo(mapInfo: MapInfo) {
    mapSettings.putString(SETTING_KEY_MAP_INFO, defaultJson.encodeToString<MapInfo>(mapInfo))
  }

  /**
   * 拿取地图信息
   */
  fun getMapInfo() : MapInfo? {
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
  fun getButtonInfo() : ButtonInfo? {
    return mapSettings.getStringOrNull(SETTING_KEY_MAP_BUTTON_INFO)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<ButtonInfo>(json)
      }.onFailure {
        mapSettings.remove(SETTING_KEY_MAP_BUTTON_INFO)
      }.getOrNull()
    }
  }

}