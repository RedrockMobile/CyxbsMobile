package com.cyxbs.pages.sport.model

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.sport.model.network.SportApiService

object SportRepository {

    private val service = SportApiService::class.impl()

    suspend fun getSportDetailData(): Result<SportDetailBean> {
        return runCatchingCoroutine {
            service.getSportDetail()
        }.mapCatching {
            it.data
        }
    }

    suspend fun getSportNoticeData(): Result<List<NoticeItem>> {
        return runCatchingCoroutine {
            service.getSportNotice()
        }.mapCatching {
            it.data
        }
    }

}
