package com.cyxbs.components.utils.logger.event

/**
 * @author : why
 * @time   : 2023/12/4 18:03
 * @bless  : God bless my code
 */

/**
 * 目前存在的点击类型的埋点
 *
 * - 命名都是直接根据数据中台创建的埋点name改的（懒得想了，新增的时候也可以自己想命名）
 *
 * - 【id】 和 【hash】 据后端说都是唯一，且一一对应，注意埋点平台那边修改的时候需要端上同步修改或添加
 *
 * - 目前暂定统一写进utils模块统一管理，如果后面实在太多可以考虑下沉到各业务模块？
 */
enum class ClickEvent(
  val id: String,
  val eventType: EventType
  ) {
    //打开掌上重邮落地页
    EXPOSURE_MOBILE_ZSCY_LANDINGPAGE("20001",  EventType.EXPOSURE),
    //从打开掌邮到关闭掌邮的时间
    TIME_MOBILE_ZSCY("20002", EventType.TIME),
    //打开没课约页面
    EXPOSURE_MOBILE_ZSCY_MKY_HOMEPAGE("20003", EventType.EXPOSURE),
    // 从打开没课约落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_MKY("20004",  EventType.TIME),
    //打开课表查询页面
    EXPOSURE_MOBILE_ZSCY_KBCX_HOMEPAGE("20005",  EventType.EXPOSURE),
    //从打开课表查询落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_KBCX("20006",  EventType.TIME),
    //打开活动中心页面
    EXPOSURE_MOBILE_ZSCY_HDZX_HOMEPAGE("20007",  EventType.EXPOSURE),
    //从打开活动中心落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_HDZX("20008",  EventType.TIME),
    //打开邮票中心页面
    EXPOSURE_MOBILE_ZSCY_YPZX_HOMEPAGE("20009",  EventType.EXPOSURE),
    //从打开邮票中心落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_YPZX_HOMEPAGE("20010",  EventType.TIME),
    //打开美食资讯处页面
    EXPOSURE_MOBILE_ZSCY_MSZXC_HOMEPAGE("20011",  EventType.EXPOSURE),
    //从打开美食资讯处落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_MSZXC("20012",  EventType.TIME),
    //打开表态广场页面
    EXPOSURE_MOBILE_ZSCY_BTGC_HOMEPAGE("20013", EventType.EXPOSURE),
    //从打开表态广场落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_BTGC("20014", EventType.TIME),
    //打开活动布告栏页面
    EXPOSURE_MOBILE_ZSCY_HDBGL_HOMEPAGE("20015",  EventType.EXPOSURE),
    //从打开活动布告栏落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_HDBGL("20016", EventType.TIME),
    //打开重邮地图页面
    EXPOSURE_MOBILE_ZSCY_CYDT_HOMEPAGE("20017",  EventType.EXPOSURE),
    //从打开重邮地图落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_CYDT("20018",  EventType.TIME),
    //打开校车轨迹页面
    EXPOSURE_MOBILE_ZSCY_XCGJ_HOMEPAGE("20019",  EventType.EXPOSURE),
    //从打开校车轨迹落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_XCGJ("20020",  EventType.TIME),
    //打开邮子清单页面
    EXPOSURE_MOBILE_ZSCY_YZQD_HOMEPAGE("20021",  EventType.EXPOSURE),
    //从打开邮子清单落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_YZQD("20022",  EventType.TIME),
    //打开校历页面
    EXPOSURE_MOBILE_ZSCY_XL_HOMEPAGE("20023",  EventType.EXPOSURE),
    //从打开校历落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_XL("20024",  EventType.TIME),
    //打开签到页面
    EXPOSURE_MOBILE_ZSCY_QD_HOMEPAGE("20025",  EventType.EXPOSURE),
    //从打开签到落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_QD("20026",  EventType.TIME),
    //打开消息中心页面
    EXPOSURE_MOBILE_ZSCY_XXZX_HOMEPAGE("20027",  EventType.EXPOSURE),
    //从打开消息中心落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_XXZX("20028",  EventType.TIME),
    //打开反馈中心页面
    EXPOSURE_MOBILE_ZSCY_FKZX_HOMEPAGE("20029",  EventType.EXPOSURE),
    //从打开反馈中心落地页到关闭该板块的时间
    TIME_MOBILE_ZSCY_FKZX("20030",  EventType.TIME),
    //打开banner位图片
    EXPOSURE_MOBILE_ZSCY_BANNER_HOMEPAGE("20031",  EventType.EXPOSURE),
    //从打开banner到关闭该板块的时间
    TIME_MOBILE_ZSCY_BANNER("20032",  EventType.TIME);

    enum class EventType { EXPOSURE, TIME }
}