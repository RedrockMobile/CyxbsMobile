package com.cyxbs.components.utils.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

internal actual fun createHttpClientEngine(): HttpClientEngine = Js.create {

}