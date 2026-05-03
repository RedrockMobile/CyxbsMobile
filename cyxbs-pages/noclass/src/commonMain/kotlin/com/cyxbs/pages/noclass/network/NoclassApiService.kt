package com.cyxbs.pages.noclass.network

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.noclass.bean.NoClassGroups
import com.cyxbs.pages.noclass.bean.NoClassTemporarySearchs
import com.cyxbs.pages.noclass.bean.NoclassGroupIds
import com.cyxbs.pages.noclass.bean.Students
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Query

/**
 * 没课约 API 接口（commonMain / Ktorfit 版本）
 *
 *
 */
interface NoclassApiService {

    /** 获取全部分组信息 */
    @GET("magipoke-jwzx/no_class/group/all")
    suspend fun getGroupAll(): ApiWrapper<List<NoClassGroups>>

    /** 创建分组 */
    @FormUrlEncoded
    @POST("magipoke-jwzx/no_class/group")
    suspend fun postGroup(
        @Field("name") name: String,
        @Field("stu_nums") stuNums: String,
    ): ApiWrapper<NoclassGroupIds>

    /** 删除分组 */
    @DELETE("magipoke-jwzx/no_class/group")
    suspend fun deleteGroup(
        @Query("group_ids") groupIds: String
    ): ApiStatus

    /** 更新分组（置顶/取消置顶、重命名等） */
    @FormUrlEncoded
    @PUT("magipoke-jwzx/no_class/group")
    suspend fun updateGroup(
        @Field("group_id") groupId: String,
        @Field("name") name: String,
        @Field("is_top") isTop: String,
    ): ApiStatus

    /** 分组添加成员 */
    @FormUrlEncoded
    @POST("magipoke-jwzx/no_class/member")
    suspend fun addGroupMember(
        @Field("group_id") groupId: String,
        @Field("stu_nums") stuNum: String,
    ): ApiStatus

    /** 分组删除成员 */
    @FormUrlEncoded
    @POST("magipoke-jwzx/no_class/member/delete")
    suspend fun deleteGroupMember(
        @Field("group_id") groupId: String,
        @Field("stu_nums") stuNum: String,
    ): ApiStatus

    /** 搜索学生 */
    @GET("magipoke-jwzx/search/people")
    suspend fun searchPeople(
        @Query("stu") stu: String
    ): ApiWrapper<List<Students>>

    /** 临时分组搜索全部（同学/班级/分组） */
    @GET("magipoke-jwzx/no_class/group/search/temporary")
    suspend fun searchAll(
        @Query("key") key: String
    ): ApiWrapper<NoClassTemporarySearchs>
}
