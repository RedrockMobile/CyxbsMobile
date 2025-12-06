package com.cyxbs.pages.map.model

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.pages.map.model.bean.ButtonInfo
import com.cyxbs.pages.map.model.bean.FavoritePlaceSimple
import com.cyxbs.pages.map.model.bean.MapInfo
import com.cyxbs.pages.map.model.bean.PlaceDetails
import com.cyxbs.pages.map.model.bean.PlaceSearch
import com.cyxbs.pages.map.model.network.MapService
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData

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
      it
    }
  }

  suspend fun getCollect(): Result<List<String>> {
    return runCatchingCoroutine {
      service.getCollect()
    }.mapCatching {
      it.data.placeIdList
    }
  }

  suspend fun addCollect(placeId: String): Result<ApiStatus> {
    return runCatchingCoroutine {
      service.addCollect(placeId)
    }.mapCatching {
      it
    }
  }

  suspend fun deleteCollect(placeId: String): Result<ApiStatus> {
    return runCatchingCoroutine {
      service.deleteCollect(MultiPartFormDataContent(
        formData {
          append("place_id", placeId)
        }
      ))
    }.mapCatching {
      it
    }
  }

  suspend fun placeSearch(placeSearch: String): Result<String> {
    return runCatchingCoroutine {
      service.placeSearch(placeSearch)
    }.mapCatching {
      it.data.placeId
    }
  }
}