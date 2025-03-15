package com.cyxbs.pages.course.network

import com.cyxbs.pages.course.bean.StuLessonBean
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.POST

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/15
 */
interface CourseApiService {

  @POST("/magipoke-jwzx/kebiao")
  @FormUrlEncoded
  suspend fun getStuLesson(
    @Field("stu_num")
    stuNum: String
  ) : StuLessonBean
}