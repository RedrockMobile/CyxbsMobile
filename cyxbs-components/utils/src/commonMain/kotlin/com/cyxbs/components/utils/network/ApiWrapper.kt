package com.cyxbs.components.utils.network

import com.cyxbs.components.config.serializable.GsonDataBean
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
expect class ApiWrapper<T>: IApiWrapper<T>{
  override val data: T
  override val status: Int
  override val info: String
  /**
   * 服务端原始 data；业务状态失败时允许为空。
   *
   * 常规接口仍应读取 [data] 并复用统一状态校验。只有明确约定“非成功状态仍返回可处理 data”的接口，才能在
   * 自己的适配层检查 [status] 后读取本属性。
   */
  val rawData: T?
}
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

  /**
   * 仅校验业务状态并抛出 [ApiException]，不执行任何账号或 token 副作用。
   *
   * 认证状态码由网络层使用请求发起时冻结的 lease 处理；保持本方法纯粹可避免 wrapper 反序列化后切号，再延迟
   * 访问 `data` 时把旧响应错误施加到新账号。
   */
  @Throws(ApiException::class)
  fun throwApiExceptionIfFail() {
    if (!isSuccess()) {
      throw ApiException(status, info)
    }
  }
}
