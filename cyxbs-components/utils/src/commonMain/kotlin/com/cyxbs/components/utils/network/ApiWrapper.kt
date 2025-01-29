package com.cyxbs.components.utils.network

import com.cyxbs.components.account.api.ITokenService
import com.cyxbs.components.config.serializable.GsonDataBean
import com.cyxbs.components.utils.service.impl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [ApiWrapper] 里面封装了 [data]、[status]、[info] 字段，是为了统一网络请求数据的最外层结构
 *
 * ## 注意
 * - 如果你遇到了 json 报错，可能是你数据类写错了，只需要提供 [data] 对应的类即可
 * - 如果你的网络请求数据类有其他变量，请使用 [IApiWrapper] 这个接口
 *
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/29 23:06
 */
@Serializable
data class ApiWrapper<T>(
  @SerialName("data")
  override val data: T,
  @SerialName("status")
  override val status: Int,
  @SerialName("info")
  override val info: String
) : IApiWrapper<T>, GsonDataBean

/**
 * 没有 data 字段的接口数据包裹类
 *
 * 该类符合后端的接口规范，最外层字段值包含 [status] 和 [info]
 *
 * 禁止私自添加其他字段
 * - 如果需要添加且不是老接口，那说明是后端没有遵守规范，让后端自己改接口
 * - 如果是老接口，请自己使用 map 操作符判断
 */
@Serializable
data class ApiStatus(
  @SerialName("status")
  override val status: Int,
  @SerialName("info")
  override val info: String
) : IApiStatus, GsonDataBean

interface IApiWrapper<T> : IApiStatus {
  val data: T
}

interface IApiStatus {
  val status: Int
  val info: String

  /**
   * 数据的状态码
   *
   * 与后端规定了 10000 为 数据请求成功
   *
   * 注意区分：数据 Http 状态码 与 数据状态码
   *
   * 对于 [status] 不为 10000 时，建议采用下面这种写法来处理
   * ```
   *
   * ```
   */
  fun isSuccess(): Boolean {
    // 10000 是 21 年后的新规定，200 是以前老接口的规定
    return status == 10000 || status == 200 // 请不要私自加其他的成功状态！！！
  }

  @Throws(ApiException::class)
  fun throwApiExceptionIfFail() {
    // 后端文档：https://redrock.feishu.cn/wiki/wikcnB9p6U45ZJZmxwTEu8QXvye
    // 后端后台密钥对7天一更新，此时使用token请求会报 20002 verify failed，按照token过期的逻辑处理，刷新token
    if (status == 20002 || status == 20003) {
      // token 过期
      ITokenService::class.impl().tryTokenExpired()
    } else if (status == 20004) {
      // refreshToken 过期，这里只能让用户重新登录，正常情况下会在主界面就触发拦截跳转至登录页
    }
    if (!isSuccess()) {
      throw ApiException(status, info)
    }
  }
}