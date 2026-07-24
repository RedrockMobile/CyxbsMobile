package com.cyxbs.pages.sport.model

import com.cyxbs.components.config.service.impl
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.sport.network.SportDetailApiService
import com.cyxbs.pages.sport.network.SportNoticeApiService

object SportRepository {

    private val detailService = SportDetailApiService::class.impl()

    private val noticeService = SportNoticeApiService::class.impl()

    suspend fun getSportDetailData(): Result<SportDetailBean> {
        return runCatchingCoroutine {
            detailService.getSportDetailData()
        }.mapCatching {
            it.data
        }
    }

    suspend fun getSportNoticeData(): Result<List<NoticeItem>> {
        return runCatchingCoroutine {
            noticeService.getSportNoticeData()
        }.mapCatching {
            it.data
        }
    }

}