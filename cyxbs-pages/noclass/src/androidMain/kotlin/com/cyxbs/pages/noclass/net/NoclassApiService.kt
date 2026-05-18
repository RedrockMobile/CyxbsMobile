package com.cyxbs.pages.noclass.net

import com.cyxbs.components.utils.network.ApiGenerator
import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.pages.noclass.bean.NoClassBatchResponseInfo
import com.cyxbs.pages.noclass.bean.NoClassGroup
import com.cyxbs.pages.noclass.bean.NoClassTemporarySearch
import com.cyxbs.pages.noclass.bean.NoclassGroupId
import com.cyxbs.pages.noclass.bean.Student
import io.reactivex.rxjava3.core.Single
import retrofit2.http.*

/**
 *
 * @ProjectName:    CyxbsMobile_Android
 * @Package:        com.cyxbs.pages.noclass.net
 * @ClassName:      NoclassApiService
 * @Author:         Yan
 * @CreateDate:     2022年08月23日 02:02:00
 * @UpdateRemark:   更新说明：
 * @Version:        1.0
 * @Description: */
interface NoclassApiService {
    companion object {
        val INSTANCE by lazy {
            ApiGenerator.getApiService(NoclassApiService::class)
        }
    }

    /**
     * 没课约获取全部分组信息
     */
    @GET("/magipoke-jwzx/no_class/group/all")
    fun getGroupAll(): Single<ApiWrapper<List<NoClassGroup>>>

    /**
     * 没课约上传分组
     */
    @FormUrlEncoded
    @POST("/magipoke-jwzx/no_class/group")
    fun postGroup(
        @Field("name") name: String,
        @Field("stu_nums") stuNums: String,
    ): Single<ApiWrapper<NoclassGroupId>>

    /**
     * 没课约删除分组
     */
    @DELETE("/magipoke-jwzx/no_class/group")
    fun deleteGroup(
        @Query("group_ids") groupIds: String
    ): Single<ApiStatus>

    /**
     * 没课约更新分组
     */
    @FormUrlEncoded
    @PUT("/magipoke-jwzx/no_class/group")
    fun updateGroup(
        @Field("group_id") groupId: String,
        @Field("name") name: String,
        @Field("is_top") isTop: String,
    ): Single<ApiStatus>

    /**
     * 没课约分组添加成员
     */
    @FormUrlEncoded
    @POST("/magipoke-jwzx/no_class/member")
    fun addGroupMember(
        @Field("group_id") groupId: String,
        @Field("stu_nums") stuNum: String
    ): Single<ApiStatus>

    /**
     * 没课约分组删除成员
     */
    @HTTP(method = "DELETE", path = "/magipoke-jwzx/no_class/member", hasBody = true)
    @FormUrlEncoded
    fun deleteGroupMember(
        @Field("group_id") groupId: String,
        @Field("stu_nums") stuNum: String,
    ): Single<ApiStatus>


    @GET("/magipoke-jwzx/search/people")
    fun searchPeople(
        @Query("stu") stu: String
    ): Single<ApiWrapper<List<Student>>>

    @GET("/magipoke-jwzx/no_class/group/search/temporary")
    fun searchAll(
        @Query("key") key : String
    ) : Single<ApiWrapper<NoClassTemporarySearch>>

    /**
     * 批量添加的添加信息检查
     */
    @FormUrlEncoded
    @POST("/magipoke-jwzx/no_class/member/check")
    fun checkUploadInfo(
        @Field("content") content : List<String>
    ): Single<ApiWrapper<NoClassBatchResponseInfo>>
}