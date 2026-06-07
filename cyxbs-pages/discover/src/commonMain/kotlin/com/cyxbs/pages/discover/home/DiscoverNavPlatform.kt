package com.cyxbs.pages.discover.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter

/**
 * 发现首页平台相关能力（commonMain 声明，androidMain 实现）。
 *
 * 这些能力依赖仅在 androidMain / mobileMain 可见的服务或 Activity 跳转，
 * 无法直接在 commonMain 调用，故下放到平台层：
 * - [rememberFunctions] 提供功能区按钮列表（图标、文字、点击行为由平台决定）
 * - [rememberFeeds]     提供 Feed 区块列表（每个 Feed 在原项目里是一个 Android Fragment）
 * - [launchNotification] / [jumpCheckIn] / [jumpJwNewsList] / [jumpJwNewsItem]
 *   是 Android Activity / 路由跳转
 *
 * 业务侧通过 `DiscoverNavPlatform::class.implOrNull()` 获取，其它平台暂无实现时优雅降级。
 * 迁移完所有二级页面为 cmp 后可逐步删除本接口。
 */
interface DiscoverNavPlatform {

  /** 功能按钮列表（id 用于持久化顺序，调整后不要随便改） */
  @Composable
  fun rememberFunctions(): List<DiscoverFunctionItem>

  @Composable
  fun rememberFeeds(): List<DiscoverFeedItem>

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

/**
 * 功能区一个按钮（横向滚动条目）
 */
@Stable
class DiscoverFunctionItem(
  /** 稳定 id，用于本地持久化顺序，新增按钮不要复用旧 id */
  val id: String,
  /** 标题（按钮下方文字） */
  val title: String,
  /** 图标 */
  val painter: Painter,
  /**
   * 点击行为。若 [loginPrompt] 非 null，调用前 commonMain 会先弹登录态对话框。
   */
  val onClick: () -> Unit,
  /**
   * 需要登录时弹出的「请登录解锁 {xxx}」文案；为 null 表示不需要登录态拦截。
   */
  val loginPrompt: String? = null,
)

/**
 * Feed 区一个卡片
 */
@Stable
class DiscoverFeedItem(
  /** Feed 内容（在 androidMain 里通常包一个 Fragment 的 AndroidView） */
  val content: @Composable (Modifier) -> Unit,
)
