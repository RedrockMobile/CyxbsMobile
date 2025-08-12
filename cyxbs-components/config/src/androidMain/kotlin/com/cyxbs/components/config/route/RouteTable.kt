package com.cyxbs.components.config.route

/**
 * 路由表命名规则：
 *
 * 1、常量名（全大写）：模块名_功能描述，例：QA_ENTRY
 * 2、二级路由：/模块名/功能描述，例：/qa/entry
 * 3、多级路由：/模块依赖关系倒置/功能描述，例：/map/discover/entry
 *
 * 注意：如果你的 api 模块只有父模块实现，请把路由地址放在你的 api 模块中！！！
 *     这里只放共用的路由地址
 *
 * TODO 由于目前没有完全迁移 lib_common，所以有些看起来没有使用的变量，可能以后需要使用，所以暂时不要删除
 *
 * 其余配置：缺省页拦截在 [ActivityInterceptor]
 */

const val DISCOVER_ENTRY = "/discover/entry"
const val MINE_ENTRY = "/mine/entry"

const val DISCOVER_OTHER_COURSE = "/other_course/discover/entry"
const val DISCOVER_NO_CLASS = "/no_class/discover/entry"
const val DISCOVER_MAP = "/map/discover/entry"
const val DISCOVER_CALENDAR = "/calendar/discover/entry"
const val DISCOVER_EMPTY_ROOM = "/empty_room/discover/entry"
const val DISCOVER_GRADES = "/grades/discover/entry"
const val DISCOVER_VOLUNTEER = "/volunteer/discover/entry"
const val DISCOVER_VOLUNTEER_RECORD = "/volunteer/discover/record"
const val DISCOVER_SCHOOL_CAR = "/school_car/discover/entry"
const val DISCOVER_NEWS = "/news/discover/entry"
const val DISCOVER_TODO_MAIN = "/todo/discover/entry"
const val DISCOVER_SPORT = "/sport/discover/entry"

//查电费在发现页面的展示信息
const val DISCOVER_ELECTRICITY_FEED = "/electricity/discover/feed"

//教务新闻子项入口
const val DISCOVER_NEWS_ITEM = "/news/discover/item"

//签到页
const val MINE_CHECK_IN = "/check_in/mine/entry"

//通知页主页
const val NOTIFICATION_HOME = "/notification/entry"

//通知设置页
const val NOTIFICATION_SETTING = "/notification/setting"

// 个人界面的确认密码模块
const val MINE_FORGET_PASSWORD = "/forget_password/mine/entry"

//小组件用，增加todo
const val TODO_ADD_TODO_BY_WIDGET = "/widget/todo/entry"

// module_store 邮票中心页
const val STORE_ENTRY = "/store/entry"

//课表上课地点跳转到地图key
const val COURSE_POS_TO_MAP = "COURSE_POS_TO_MAP"

//下面是邮乐场板块的地址
//游乐场主界面
const val FAIRGROUND_ENTRY = "/fairground/entry"
//活动布告栏
const val UFIELD_MAIN_ENTRY = "/ufield/main/entry"
//活动中心
const val UFIELD_CENTER_ENTRY = "/ufield/campaign/entry"
//活动详情
const val UFIELD_DETAIL_ENTRY = "/ufield/detail/entry"
//表态页面
const val DECLARE_ENTRY = "/declare/entry"
//美食页面
const val FOOD_ENTRY = "/food/entry"
//QA广场
const val QA_ENTRY = "/qa/entry"