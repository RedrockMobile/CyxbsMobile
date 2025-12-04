package com.cyxbs.pages.map.model

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.pages.map.model.bean.ButtonInfo
import com.cyxbs.pages.map.model.bean.MapInfo
import com.cyxbs.pages.map.model.bean.PlaceDetails
import com.cyxbs.pages.map.model.network.MapService

/**
 * @Desc : Map的仓库层
 * @Author : zzx
 * @Date : 2025/11/17 21:08
 */

object MapRepository {

  val service = MapService::class.impl()

  suspend fun getMapInfo(): Result<MapInfo> {
    return runCatchingCoroutine {
      service.getMapInfo()
    }.mapCatching {
      it.data
    }
  }

  suspend fun getPlaceDetails(placeId: String): Result<PlaceDetails> {
    return runCatchingCoroutine {
      service.getPlaceDetails(placeId)
    }.mapCatching {
      it.data
    }
  }

  suspend fun getButtonInfo(): Result<ButtonInfo> {
    return runCatchingCoroutine {
      service.getButtonInfo()
    }.mapCatching {
      it.data
    }
  }

  suspend fun addHot(placeId: String): Result<ApiStatus> {
    return runCatchingCoroutine {
      service.addHot(placeId)
    }.mapCatching {
      it.data
    }
  }
}