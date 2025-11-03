package com.cyxbs.components.utils.network

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
actual data class ApiWrapper<T>(
	@SerialName(value = "data")
	@SerializedName(value = "data")
	val dataNullable: T? = null, // 在 info 不为 10000 时，data 可能会为 null
	@SerialName(value = "status")
	@SerializedName(value = "status")
	actual override val status: Int,
	@SerialName(value = "info")
	@SerializedName(value = "info")
	actual override val info: String
) : IApiWrapper<T> {

	actual override val data: T
		get() = dataNullable!!
}