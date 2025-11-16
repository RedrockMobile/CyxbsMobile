package com.cyxbs.components.utils.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
actual data class ApiWrapper<T>(
	@SerialName(value = "data")
	val dataNullable: T? = null,//info不为10000时，data可能为null

	@SerialName(value = "status")
	actual override val status: Int,

	@SerialName(value = "info")
	actual override val info: String
) : IApiWrapper<T> {
	actual override val data: T
		get() {
      throwApiExceptionIfFail()
      return dataNullable!!
    }
}