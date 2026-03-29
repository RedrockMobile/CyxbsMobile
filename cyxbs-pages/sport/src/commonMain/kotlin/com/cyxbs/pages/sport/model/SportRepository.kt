package com.cyxbs.pages.sport.model

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.sport.bean.SportDetailBean
import com.cyxbs.pages.sport.network.SportDetailApiService

object SportRepository {

    private val service = SportDetailApiService::class.impl()

    suspend fun getSportDetailData() : Result<SportDetailBean> {
        return runCatchingCoroutine {
            service.getSportDetailData()
        }.mapCatching {
            it.data
        }
    }

}