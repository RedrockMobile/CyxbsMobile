package com.cyxbs.pages.discover.home

/**
 * 发现首页平台相关能力（commonMain 声明，androidMain 实现）。
 *
 * 这些能力依赖仅在 androidMain / mobileMain 可见的服务或 Activity 跳转，
 * 无法直接在 commonMain 调用，故下放到平台层：
 * - [launchNotification] / [jumpCheckIn] / [jumpJwNewsList] / [jumpJwNewsItem]
 *   是 Android Activity / 路由跳转
 *
 * 业务侧通过 `DiscoverNavPlatform::class.implOrNull()` 获取，其它平台暂无实现时优雅降级。
 * 迁移完所有二级页面为 cmp 后可逐步删除本接口。
 */
interface DiscoverNavPlatform {

  /** 跳转消息中心 */
  fun launchNotification()

  /** 跳转签到页 */
  fun jumpCheckIn()

  /** 跳转教务在线新闻列表 */
  fun jumpJwNewsList()

  /** 跳转教务在线某条新闻详情 */
  fun jumpJwNewsItem(newId: String)

  /** Banner 点击行为（含登录埋点 + url 跳转） */
  fun onBannerClick(pictureGotoUrl: String, keyword: String)
}
