package com.cyxbs.components.utils.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

/**
 * .
 *
 * @author 985892345
 * @date 2025/1/5
 */
internal actual fun createHttpClientEngine(): HttpClientEngine = Js.create {
}