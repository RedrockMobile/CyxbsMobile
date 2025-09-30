package com.cyxbs.components.utils.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * 因为学校域名 DNS 解析原因，走 ipv6 会很慢，所以优先使用 ipv4
 *
 * @author RQ527 (Ran Sixiang)
 * @email 1799796122@qq.com
 * @date 2023/9/13
 * @Description:
 */
object OkHttpDnsService {

  val dns = object : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
      val system = Dns.Companion.SYSTEM.lookup(hostname)
      return system.sortedBy { // 因为学校域名 DNS 解析原因，走 ipv6 会很慢，所以优先使用 ipv4
        when (it) {
          is Inet4Address -> 0
          is Inet6Address -> 1
          else -> 2
        }
      }
    }
  }
}