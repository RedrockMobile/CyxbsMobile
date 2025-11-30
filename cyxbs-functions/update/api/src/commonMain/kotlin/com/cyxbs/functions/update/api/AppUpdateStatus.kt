package com.cyxbs.functions.update.api

/**
 * Create By Hosigus at 2020/5/2
 */
sealed interface AppUpdateStatus {
    object Checking : AppUpdateStatus           // 检查更新中

    sealed interface Result : AppUpdateStatus {
        data class Dated(                       // 当前版本已过时，建议更新
            val newVersion: UpdateInfo
        ) : Result
        object Valid : Result                   // 当前版本有效，无需更新
        data class Error(                       // 任何时候出错
            val throwable: Throwable
        ) : Result
    }
}