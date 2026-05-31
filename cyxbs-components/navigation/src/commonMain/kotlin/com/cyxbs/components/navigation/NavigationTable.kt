package com.cyxbs.components.navigation

/**
 * @Desc : 主页路由表
 * @Author : zzx
 * @Date : 2025/10/27 15:35
 */

/**
 * 路由表命名规则：
 *
 * 1. 对于 Activity:
 *    NAV_页面 = "页面"
 *    示例: NAV_LOGIN = "login"
 *
 * 2. 对于 Fragment:
 *    NAV_FRAGMENT_页面 = "页面"
 *
 * 3. 对于弹窗页面:
 *    NAV_DIALOG_页面 = "页面"
 *    示例: NAV_DIALOG_UPDATE = "dialog/update"
 *
 * 对于一个页面下存在多个子页面，应该作为页面 Argument 的一个 query 参数，不应该单独声明
 * 比如：
 * ```
 * // Home 页一个 ViewPager 下存在三个页面：discover、fairground、mine
 * class HomeNavArgument(
 *     val page: String = "discover" // 这里默认定位 discover
 * )
 *
 * // 如果外界想直接定位到「我的」二级页面，那么应该使用如下 deeplink：
 * "cyxbs://home?page=mine"
 * ```
 */

// 主页
const val NAV_HOME = "home"

// 登录
const val NAV_LOGIN = "login"

// 关于我们
const val NAV_ABOUT = "about"

// 更新弹窗
const val NAV_DIALOG_UPDATE = "dialog/update"

//美食咨询处
const val NAV_FOOD = "food"

// 邮乐园（入口页）
const val NAV_FAIRGROUND = "fairground"

// 课表单页
const val NAV_COURSE = "course"

// 查找他人课表
const val NAV_COURSE_FIND = "course_find"

// 公告弹窗
const val NAV_DIALOG_NOTICE = "dialog/notice"

// 地图
const val NAV_MAP = "map"

// 地图-查看图片
const val NAV_MAP_SHOW_PICTURE = "map_show_picture"

// 校车轨迹
const val NAV_SCHOOL_CAR = "school_car"

// 空教室
const val NAV_EMPTY_ROOM = "emptyroom"