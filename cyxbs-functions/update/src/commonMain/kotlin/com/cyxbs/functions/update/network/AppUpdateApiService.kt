package com.cyxbs.functions.update.network

import com.cyxbs.functions.update.api.UpdateInfo
import com.cyxbs.functions.update.bean.GithubUpdateInfo
import de.jensklingenberg.ktorfit.http.GET

/**
 * Create By Hosigus at 2019/5/11
 */
interface AppUpdateApiService {
    @GET("https://app.redrock.team/cyxbsAppUpdate.json")
    suspend fun getUpdateInfo(): UpdateInfo

    //在官网查询更新失败时查询github的release更新
    @GET("https://api.github.com/repos/RedrockMobile/CyxbsMobile/releases/latest")
    suspend fun getUpdateInfoByGithub(): GithubUpdateInfo
}