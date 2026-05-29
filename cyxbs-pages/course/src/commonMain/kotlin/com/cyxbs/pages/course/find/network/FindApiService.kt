package com.cyxbs.pages.course.find.network

import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.course.find.bean.FindStuBean
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query

/**
 * 查找他人课表相关网络接口
 *
 * @author 985892345
 * @date 2026/5/27
 */
interface FindApiService {

  // 搜索学生（支持不完整学号或姓名）
  @GET("magipoke-jwzx/search/people")
  suspend fun getStudents(
    @Query("stu") stu: String,
  ): ApiWrapper<List<FindStuBean>>
}
