package com.cyxbs.components.config.navigation

/**
 * @Desc : 主页路由表
 * @Author : zzx
 * @Date : 2025/10/27 15:35
 */

/**
 * 路由表命名规则：
 *
 * 1、常量名（全大写）：NAV_模块名_功能描述，例：NAV_QA_ENTRY
 * 2、二级路由：模块名/功能描述，例：qa/entry
 * 3、多级路由：模块依赖关系倒置/功能描述，例：map/discover/entry
 */

// 登录
const val NAV_LOGIN_ENTRY = "login"

// 关于我们
const val NAV_ABOUT_ENTRY = "about"