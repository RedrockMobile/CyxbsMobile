package com.cyxbs.pages.mine.network

import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.IApi
import com.cyxbs.pages.mine.network.model.BindingResponse
import com.cyxbs.pages.mine.network.model.ConfirmCode
import com.cyxbs.pages.mine.network.model.ConfirmQuestion
import com.cyxbs.pages.mine.network.model.Email
import com.cyxbs.pages.mine.network.model.EmailCode
import com.cyxbs.pages.mine.network.model.IdsGetCode
import com.cyxbs.pages.mine.network.model.ScoreStatus
import com.cyxbs.pages.mine.network.model.SecurityQuestion
import com.cyxbs.pages.mine.network.model.UploadImgResponse
import com.mredrock.cyxbs.common.bean.RedrockApiStatus
import com.mredrock.cyxbs.common.bean.RedrockApiWrapper
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

/**
 * Created by zia on 2018/8/15.
 */
interface ApiService:IApi {

    /**
     * 上传头像
     */
    @Multipart
    @PUT("magipoke/person/upload/avatar")
    fun uploadSocialImg(
        @Part("stunum") stunum: RequestBody,
        @Part fold: MultipartBody.Part
    ): Observable<RedrockApiWrapper<UploadImgResponse>>

    /**
     * 上传图片
     */
    @FormUrlEncoded
    @POST("/magipoke/Person/SetInfo")
    fun updateUserImage(
        @Field("photo_thumbnail_src") photo_thumbnail_src: String?,
        @Field("photo_src") photo_src: String?
    ): Observable<RedrockApiStatus>

    /**
     * 签到部分
     */
    @POST("/magipoke-intergral/QA/Integral/checkIn")
    fun checkIn(): Observable<RedrockApiStatus>

    //获取积分
    @POST("/magipoke-intergral/QA/User/getScoreStatus")
    fun getScoreStatus(): Observable<RedrockApiWrapper<ScoreStatus>>


    /**
     * 说明：修改密码模块的接口存在两种情况
     * 即来自登陆界面以及来自个人界面
     * 接口方法中以FormLogin结尾的就是自登陆界面时需要调用的接口
     * 反之就是自个人界面调用的接口
     */

    /**
     * 修改密码
     * 调用此方法必须处于登录状态
     * 故不需要传递认证int进来
     */
    @FormUrlEncoded
    @POST("/user-secret/user/password/personal")
    fun resetPassword(
        @Field("origin_password") origin_password: String,
        @Field("new_password") new_password: String
    ): Observable<RedrockApiStatus>

    /**
     * 修改密码
     * 调用此方法时处于登陆界面
     */
    @FormUrlEncoded
    @POST("/user-secret/user/password/valid")
    fun resetPasswordFromLogin(
        @Field("stu_num") stu_num: String,
        @Field("new_password") new_password: String,
        @Field("code") code: Int
    ): Observable<RedrockApiStatus>

    /**
     * 设置密保问题答案
     * 必须在个人界面中调用此方法
     */
    @FormUrlEncoded
    @POST("/user-secret/user/bind/question")
    fun setSecurityQuestionAnswer(
        @Field("id") id: Int, //问题的id
        @Field("content") content: String
    ): Observable<RedrockApiStatus>//答案的主体内容

    /**
     * 获取所有的密保问题
     */
    @GET("/user-secret/user/question")
    fun getAllSecurityQuestions(): Observable<RedrockApiWrapper<List<SecurityQuestion>>>

    /**
     * 获取Email验证码
     * 此接口用于绑定邮箱时向此邮箱发送验证码
     */
    @FormUrlEncoded
    @POST("/user-secret/user/bind/email/code")
    fun getEmailCode(
        @Field("email") email: String
    ): Observable<RedrockApiWrapper<EmailCode>>

    /**
     * 验证Email验证码
     * 此接口用于绑定邮箱时验证验证码是否正确
     */
    @FormUrlEncoded
    @POST("/user-secret/user/bind/email")
    fun confirmEmailCode(
        @Field("email") email: String, //问题的id
        @Field("code") code: String
    ): Observable<RedrockApiStatus>

    /**
     * 向绑定的邮箱发送找回密码用的验证码
     * （自登陆界面，需要传递学号进来）
     */
    @FormUrlEncoded
    @POST("/user-secret/user/valid/email/code")
    fun getEmailFindPasswordCode(
        @Field("stu_num") stu_num: String
    ): Observable<RedrockApiWrapper<ConfirmCode>>

    /**
     * 验证邮箱验证码是否正确
     * 来自登陆界面
     * 此处的返回值中不一定含有data字段
     * 需要根据返回值的status判断是否拥有data字段
     * 邮箱需要通过其他接口获取
     */
    @FormUrlEncoded
    @POST("/user-secret/user/valid/email")
    fun confirmCodeWithoutLogin(
        @Field("stu_num") stu_num: String,
        @Field("email") email: String,
        @Field("code") code: Int
    ): Observable<RedrockApiWrapper<ConfirmQuestion>>

    /**
     * 获取用户邮箱地址
     * 没有进行用户数据的解析
     * 根据不同的status，不一定会有data字段
     */
    @FormUrlEncoded
    @POST("/user-secret/user/bind/email/detail")
    fun getUserEmail(
        @Field("stu_num") stu_num: String
    ): Observable<RedrockApiWrapper<Email>>

    /**
     * 获取学生的密保问题
     */
    @FormUrlEncoded
    @POST("/user-secret/user/bind/question/detail")
    fun getUserQuestion(
        @Field("stu_num") stu_num: String
    ): Observable<RedrockApiWrapper<List<SecurityQuestion>>>

    /**
     * 验证密保问题的回答是否正确
     */
    @FormUrlEncoded
    @POST("/user-secret/user/valid/question")
    fun confirmAnswer(
        @Field("stu_num") stu_num: String,
        @Field("question_id") question_id: Int,
        @Field("content") content: String
    ): Observable<RedrockApiWrapper<ConfirmQuestion>>

    /**
     * 获取通过ids改密所需的验证码 上传参数为json类型
     */
    @POST("/user-secret/user/valid/ids")
    fun getIdsCode(@Body body: RequestBody): Single<ApiWrapper<IdsGetCode>>

    /**
     * 通过[getIdsCode]获取到的验证码来修改密码
     * @param stuNum 学号
     * @param newPassword 新密码
     * @param code 验证码
     */
    @FormUrlEncoded
    @POST("/user-secret/user/password/valid")
    fun changePasswordByIds(
        @Field("stu_num") stuNum: String,
        @Field("new_password") newPassword: String,
        @Field("code") code: Int
    ): Single<ApiStatus>

    /*
     * 判断旧密码是否正确
     */
    @FormUrlEncoded
    @POST("/user-secret/user/judge/password")
    fun originPassWordCheck(
        @Field("password") password: String
    ): Observable<RedrockApiStatus>

    /**
     * 检查是否绑定信息
     */
    @FormUrlEncoded
    @POST("/user-secret/user/bind/is")
    fun checkBinding(@Field("stu_num") stu_num: String): Observable<RedrockApiWrapper<BindingResponse>>

    /**
     * 检查是否为默认密码
     */
    @FormUrlEncoded
    @POST("/user-secret/user/judge/origin")
    fun checkDefaultPassword(@Field("stu_num") stu_num: String): Observable<RedrockApiStatus>
}

