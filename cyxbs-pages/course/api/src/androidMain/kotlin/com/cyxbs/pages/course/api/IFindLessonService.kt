package com.cyxbs.pages.course.api

import android.content.Context

/**
 * 打开查找他人课表界面的服务类
 *
 * 注意：老师课表查询接口已下线，此处仅保留学生相关方法。
 *
 * @author 985892345
 * @date 2022/9/22 15:50
 */
interface IFindLessonService {

  /**
   * 直接打开查找他人课表的入口
   */
  fun startActivity(context: Context)

  /**
   * 打开学号为 [stuNum] 的学生课表
   */
  fun startActivityByStuNum(context: Context, stuNum: String)

  /**
   * 定向查找名字包含 [stuName] 的学生
   */
  fun startActivityByStuName(context: Context, stuName: String)
}
