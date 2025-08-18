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
class QaHomePagingSource (
    private val qaService : QaHomeApiService,
    private val requsetData : QaRequestData,
): PagingSource<Int, Item>(){

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Item> {

        return try{
            val page = params.key ?:1
            val pageSize = params.loadSize

            val updatedRequestData = requsetData.copy(page = page , page_size = pageSize)

            // 发起 API 请求，获取数据
            val response = qaService.getQacontentData(updatedRequestData)


            val items = response.data.items ?: emptyList()


            LoadResult.Page(
                data = items, // 当前页的数据
                prevKey = null, // 如果是第一页，prevKey 为 null
                nextKey = if (items.isNotEmpty()) page + 1 else null // 如果有数据，则下一页是当前页+1，否则没有下一页
            )
        } catch (e:Exception){
            LoadResult.Error(e)
        }

    }

    override fun getRefreshKey(state: PagingState<Int, Item>): Int? {
        // 获取当前刷新时应该使用的键值
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1) ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }

    }



}