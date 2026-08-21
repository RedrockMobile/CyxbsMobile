package com.cyxbs.pages.schedule.ui.todo

import androidx.compose.ui.graphics.Color
import com.cyxbs.components.utils.compose.color

/**
 * 邮子清单页面专用的语义配色。
 *
 * 这些颜色来自清单设计稿，只服务于 Schedule 模块，不进入全局 [com.cyxbs.components.config.compose.theme.AppColor]，
 * 避免其他业务误用页面级视觉规范。
 */

/** 分类选中态、临期提示条和悬浮新建按钮共用的主色。 */
internal val ScheduleTodoAccentColor: Color = 0xFF3852DA.color()

/** 已超期标签的文字和旗子颜色。 */
internal val ScheduleTodoOverdueColor: Color = 0xFFE2554E.color()

/** 已超期标签的容器颜色。 */
internal val ScheduleTodoOverdueContainerColor: Color = 0xFFFED7D5.color()

/** 临期标签的文字和旗子颜色。 */
internal val ScheduleTodoDueSoonColor: Color = 0xFFE77D29.color()

/** 临期标签的半透明容器颜色；透明度按 Figma 标注固定为 38%。 */
internal val ScheduleTodoDueSoonContainerColor: Color = 0x61F98E39.color()

/** 浅色主题下清单卡片的设计稿底色。 */
internal val ScheduleTodoCardContainerColor: Color = 0xFFF9FAFC.color()

/** 时间、提醒和备注胶囊在浅色主题下共用的半透明底色。 */
internal val ScheduleTodoInfoContainerColor: Color = 0x80AABCD8.color()

/** 时间、提醒和备注胶囊在浅色主题下共用的正文色，透明度固定为 60%。 */
internal val ScheduleTodoInfoContentColor: Color = 0x9915315B.color()

/** “关联到课表”图标选中时的背景色，对应 rgba(21, 49, 91, 0.8)。 */
internal val ScheduleTodoCalendarLinkSelectedColor: Color = 0xCC15315B.color()

/** 普通未完成圆圈的浅色主题描边色，透明度固定为 46%。 */
internal val ScheduleTodoPendingIndicatorColor: Color = 0x754C4C4C.color()

/** 已完成圆圈的设计稿“完成色”；原色 #257E20 以 45% 透明度呈现。 */
internal val ScheduleTodoCompletedIndicatorColor: Color = 0x73257E20.color()

/** 批量管理“全选”按钮的浅色主题底色。 */
internal val ScheduleTodoSelectAllContainerColor: Color = 0xFFD2DBEA.color()

/** 批量管理主色按钮上的浅色文字。 */
internal val ScheduleTodoOnAccentColor: Color = 0xFFF4F4F4.color()

/** 顶部“完成”按钮的浅色文字。 */
internal val ScheduleTodoHeaderOnAccentColor: Color = 0xFFF9F9F9.color()

/** 悬浮新建按钮加号的设计稿颜色。 */
internal val ScheduleTodoAddIconColor: Color = 0xFFEEEEEE.color()
