package com.cyxbs.pages.schoolcar.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.cyxbs.pages.schoolcar.bean.CarStation

/**
 * iOS 端校车定位实现说明（暂未实现，[isSupportLocation] 写死 false）
 *
 * Android 端通过高德 AMapLocation SDK 获取 GCJ-02 经纬度，喂给
 * [com.cyxbs.pages.schoolcar.bean.GeoLocation.toUserLocation] 的线性公式
 * （`px = 93669.803250 * lng - 9984871.926823`,
 *  `py = -106715.931319 * lat + 3152239.647902`）换算成图片地图像素坐标。
 *
 * iOS 之前依赖的 `AMapLocation-NO-IDFA` pod 已删除（详见对应 commit）。后续
 * 要补 iOS 定位可以下方案实现：
 *
 * 1. 用 CoreLocation (`CLLocationManager`) 获取经纬度——返回的是 **WGS-84** 坐标
 * 2. 客户端做 WGS-84 → GCJ-02 转换（公开算法，约 50 行 Kotlin，社区有大量参考
 *    实现），转换后再喂给 [toUserLocation]，保证与 Android 行为一致
 * 3. 罗盘角度用 `CLLocationManager.headingAvailable` + `CLHeading`，或者
 *    CoreMotion 的 `CMMotionManager`（Android 端就是用系统传感器实现的，对应
 *    [com.cyxbs.pages.schoolcar.location.RotationHelper]）
 * 4. 权限走 iOS 原生 `requestWhenInUseAuthorization` 流程，参考 Android 端
 *    [com.cyxbs.pages.schoolcar.location.PermissionsHelper]
 * 5. 距校园中心 1500 米外自动关定位的逻辑用 commonMain 已有的
 *    `AMapLocationHelper.calculateDistance`（Haversine 公式，纯 kotlin.math
 *    实现，不依赖高德）
 *
 * 选 CoreLocation 而非高德 SDK 的原因：CMP `mapcompose` 已走"自实现、不依赖第
 * 三方"路线，CoreLocation 是苹果原生（权限/生命周期/电量自动管），GCJ-02
 * 转换是一次性成本，长期少一个 fat framework 维护负担更划算。
 */
actual class SchoolCarViewModel : CommonSchoolCarViewModel() {
  actual override fun getClosedSite(): CarStation? {
    return null
  }

  actual override val isSupportLocation: Boolean
    get() = false

  actual override val shouldShowUserPositionMarker: State<Boolean> = mutableStateOf(false)
}
