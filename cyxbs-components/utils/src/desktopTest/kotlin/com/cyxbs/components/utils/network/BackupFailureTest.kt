package com.cyxbs.components.utils.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 覆盖新旧网络栈共用名单的关键边界，防止将认证包装、取消或所有 IO 失败都用于容灾。 */
class BackupFailureTest {
  @Test
  fun onlyExplicitNetworkFailuresTriggerBackup() {
    listOf(
      ConnectTimeoutException("连接超时"), SocketTimeoutException("读写超时"),
      UnknownHostException("DNS 失败"), ConnectException("连接拒绝"), NoRouteToHostException("路由不可达"),
    ).forEach { assertTrue(it.shouldTryBackup(), it.toString()) }
    listOf(
      IOException("普通 IO"), SocketException("连接重置"), SSLHandshakeException("证书错误"),
      CancellationException("取消"), IllegalStateException("业务失败"),
      IOException("认证获取失败", UnknownHostException("刷新域名 DNS 失败")),
    ).forEach { assertFalse(it.shouldTryBackup(), it.toString()) }
  }
}
