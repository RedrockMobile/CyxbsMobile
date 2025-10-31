package com.cyxbs.functions.update

import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.asFlow
import com.cyxbs.components.utils.network.ApiGenerator
import com.cyxbs.components.utils.utils.get.getAppVersionCode
import com.cyxbs.components.utils.utils.get.getAppVersionName
import com.cyxbs.functions.update.api.AppUpdateStatus
import com.cyxbs.functions.update.bean.UpdateInfo
import com.cyxbs.functions.update.network.AppUpdateApiService
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Create By Hosigus at 2020/5/2
 */
object AppUpdateModel {

    val APP_VERSION_CODE: Long = getAppVersionCode()

    // 用于 mock 当前处于过期状态，检测能否正常触发更新弹窗
    var mockDated = false

    val status: MutableStateFlow<AppUpdateStatus> = MutableStateFlow(AppUpdateStatus.Checking)

    init {
        appCoroutineScope.launch {
            checkUpdateInternal()
        }
    }

    var updateInfo: UpdateInfo? = null

    suspend fun checkUpdate(): AppUpdateStatus.Result {
        if (status.value == AppUpdateStatus.Checking) {
            // 当前状态处于 CHECKING 状态，则等待请求结果
            return status.filterIsInstance<AppUpdateStatus.Result>().first()
        }
        return checkUpdateInternal()
    }

    private suspend fun checkUpdateInternal(): AppUpdateStatus.Result {
        status.value = AppUpdateStatus.Checking
        return ApiGenerator.getCommonApiService(AppUpdateApiService::class)
            .getUpdateInfo()
            .onErrorResumeNext { firstError ->
                // 当官网更新查询失败时会调用github的release更新查询
                // 请注意github发布release时tag请按v+versionName+'-'+versionCode这样的格式，
                // 例如versionName为6.8.0,versionCode为84,那么发布release的tag必须为v6.8.0-84,github查询更新才能更新成功,否则会更新失败
                ApiGenerator.getCommonApiService(AppUpdateApiService::class)
                    .getUpdateInfoByGithub().map {
                        it.run {
                            if (tag.matches("v\\d+\\.\\d+\\.\\d+-\\d+".toRegex())){
                                val strings = tag.split("-")
                                val versionName = strings[0].removeRange(0,1)
                                val versionCode = strings[1].toLong()
                                UpdateInfo(assets[0].downloadUrl, body, versionCode, versionName)
                                //github更新失败，这里抛出的异常将走到doOnError
                            } else throw RuntimeException("release的tag格式不正确，请修改重新上传。")
                        }
                    }.onErrorResumeNext {
                        // github 更新是兜底方案，这里抛出的异常仅展示网校自身的更新失败即可
                        // 就不单独展示 github 更新失败的异常了
                        Single.error(firstError)
                    }
            }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess {
                updateInfo = it
            }
            .map {
                when {
                    mockDated -> AppUpdateStatus.Result.Dated
                    it.versionCode == APP_VERSION_CODE -> {
                        val name = getAppVersionName()
                        if (name != it.versionName) {
                            // 名字不相等，说明安装的版本有问题，可能是测试版
                            AppUpdateStatus.Result.Dated
                        } else AppUpdateStatus.Result.Valid
                    }
                    it.versionCode < APP_VERSION_CODE -> {
                        AppUpdateStatus.Result.Valid
                    }
                    else -> AppUpdateStatus.Result.Dated
                }
            }
            .onErrorReturn {
                AppUpdateStatus.Result.Error(it)
            }
            .doOnSuccess {
                status.value = it
            }
            .asFlow()
            .first()
    }
}