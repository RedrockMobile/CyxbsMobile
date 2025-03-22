package com.cyxbs.pages.course.network

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.course.bean.LinkStuBean
import com.cyxbs.pages.course.bean.StuLessonBean
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/15
 */
interface CourseApiService {

  @POST("magipoke-jwzx/kebiao")
  @FormUrlEncoded
  suspend fun getStuLesson(
    @Field("stu_num")
    stuNum: String
  ) : StuLessonBean

  // 得到我的关联
  @GET("magipoke-jwzx/kebiao/relevance/")
  suspend fun getLinkStudent(): ApiWrapper<LinkStuBean>

  // 修改我的关联
  @PUT("magipoke-jwzx/kebiao/relevance/")
  @FormUrlEncoded
  suspend fun changeLinkStudent(
    @Field("stuNum")
    stuNum: String // 注意：这是被关联人的学号
  ): ApiWrapper<LinkStuBean>

  // 删除我的关联
  @DELETE("magipoke-jwzx/kebiao/relevance/")
  suspend fun deleteLinkStudent(): ApiStatus
}