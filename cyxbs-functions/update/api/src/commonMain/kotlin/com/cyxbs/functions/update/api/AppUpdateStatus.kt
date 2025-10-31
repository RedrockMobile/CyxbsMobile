package com.cyxbs.functions.update.api

/**
 * Create By Hosigus at 2020/5/2
 */
sealed interface AppUpdateStatus {
    object Checking : AppUpdateStatus           // 检查更新中

    sealed interface Result : AppUpdateStatus {
        object Dated : Result                   // 当前版本已过时，建议更新
        object Valid : Result                   // 当前版本有效，无需更新
        data class Error(                       // 任何时候出错
            val throwable: Throwable
        ) : Result
    }
}