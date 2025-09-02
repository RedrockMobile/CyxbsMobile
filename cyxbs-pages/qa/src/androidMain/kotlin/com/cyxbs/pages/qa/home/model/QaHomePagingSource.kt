package com.cyxbs.pages.qa.home.model

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.cyxbs.pages.qa.home.model.bean.Item
import com.cyxbs.pages.qa.home.model.bean.QaRequestData
import com.cyxbs.pages.qa.home.model.network.QaHomeApiService

/**
 * description ： Qa首页的pagingsource
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/15 20:07
 */

class QaHomePagingSource(
    private val qaService: QaHomeApiService,
    private val requestData: QaRequestData,
) : PagingSource<Int, Item>() {

    /*
     导致paging重复的原因
    params.loadSize 默认可能不是你接口真正的 page_size，Paging3 会动态调整 loadSize（例如第一次预取可能是 3*pageSize）。
    你的接口分页参数是 page 和 page_size，如果不严格按照接口要求返回的数据长度和起始位置，会导致重复。
     */
    companion object {
        private const val PAGE_SIZE = 10 // 固定 page_size
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Item> {
        return try {
            val page = params.key ?: 1
            val request = requestData.copy(page = page, page_size = PAGE_SIZE)

            val response = qaService.getQacontentData(request)
            val items = response.data.items ?: emptyList()

            // 为了避免还是出问题这个加一个查重
            val distinctItems = items.distinctBy { it.ID }

            LoadResult.Page(
                data = distinctItems,
                prevKey = if (page > 1) page - 1 else null,
                nextKey = if (distinctItems.size == PAGE_SIZE) page + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Item>): Int? {
        // 获取当前刷新时应该使用的键值
        return state.anchorPosition?.let { anchorPosition ->
            val page = state.closestPageToPosition(anchorPosition)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }
    }
}
