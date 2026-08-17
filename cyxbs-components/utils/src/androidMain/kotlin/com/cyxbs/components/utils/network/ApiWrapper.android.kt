package com.cyxbs.components.utils.network

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
actual data class ApiWrapper<T>(
	@SerialName(value = "status")
	@SerializedName(value = "status")
	actual override val status: Int,
	@SerialName(value = "info")
	@SerializedName(value = "info")
	actual override val info: String,
	@SerialName(value = "data")
	@SerializedName(value = "data")
	actual val rawData: T? = null, // 在 status 不成功时，data 仍可能由具体接口约定返回
) : IApiWrapper<T> {

	/** 保留原有 data/status/info 的位置参数顺序，避免公共字段重排破坏既有调用。 */
	constructor(rawData: T?, status: Int, info: String) : this(status, info, rawData)

	actual override val data: T
		get() {
      throwApiExceptionIfFail()
      return rawData!!
    }
}
