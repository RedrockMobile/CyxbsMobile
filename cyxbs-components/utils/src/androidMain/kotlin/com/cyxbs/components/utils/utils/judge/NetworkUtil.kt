package com.cyxbs.components.utils.utils.judge

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.cyxbs.components.init.appContext
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.network.ApiStatus
import com.cyxbs.components.utils.network.IApi
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.http.GET
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume


/**
 * .
 *
 * @author 985892345
 * @date 2022/10/27 21:17
 */
object NetworkUtil {

  /**
   * 观察网络连接状态，类似于 LiveData，每次观察会返回上一次下发的值
   *
   * true -> 网络可用
   * false -> 网络不可用，但具体是后端问题还是没连接网络，可以通过 [tryPingNetWork] 测试
   */
  val state: Observable<Boolean>
    get() = _state.distinctUntilChanged()

  /**
   * 返回当前网络是否可用
   *
   * 网上写的直接获取网络连接状态的那个方法已经被废弃了，官方换成了回调，下面链接中有官方原因
   * https://developer.android.com/reference/android/net/NetworkInfo
   *
   * 注意：如果是刚开始启动应用时，则会在短时间内处于网络不可用状态，会返回 null，可以使用 [isAvailableExact] 来解决
   */
  val isAvailable: Boolean?
    get() = _state.value

  /**
   * 返回确切的当前网络是否可用
   */
  suspend fun isAvailableExact(): Boolean = suspendCancellableCoroutine { cont ->
    val disposable = state.take(1)
      .subscribe { cont.resume(it) }
    cont.invokeOnCancellation {
      disposable.dispose()
    }
  }

  /**
   * 直到第一次网络请求成功前挂起协程
   */
  suspend fun suspendUntilAvailable(): Unit = suspendCancellableCoroutine { cont ->
    val disposable = state
      .takeWhile { !it }
      .doOnComplete {
        cont.resume(Unit)
      }.subscribe()
    cont.invokeOnCancellation {
      disposable.dispose()
    }
  }

  /**
   * 判断当前网络的连接方式
   * @return
   * true -> 流量
   *
   * false -> WIFI
   *
   * null -> 其他
   */
  fun checkCurrentNetworkType(): Boolean? {
    // 获取 ConnectivityManager 实例
    val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    // 如果有网络连接，则检查网络类型
    connectivityManager.run {
      // 获取当前活动的网络连接
      activeNetwork?.let { network ->
        // Android M 以上建议使用getNetworkCapabilities API，由于掌邮目前（2023.4）最低SDK版本为24，因此未添加低版本的适配代码
        getNetworkCapabilities(network)?.let { networkCapabilities ->
          // 检查网络连接是否有效
          if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            // 检查网络类型并返回结果
            when {
              networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // 通过手机流量连接
                return true
              }

              networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                // 通过WIFI连接
                return false
              }
            }
          }
        }
      }
    }
    // 如果无法检查网络类型，则返回 null
    return null
  }

  /**
   * 此函数获取设备WiFi连接的IP地址。
   * @return 表示设备WiFi连接的IP地址的字符串，如果无法获取IP地址，则返回 null
   *
   * 返回值示例： "10.20.204.92"
   */
  fun getWifiIPAddress(): String? {
    // 获取WifiManager系统服务。
    val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val wifiInfo = wifiManager.connectionInfo
    // 检查wifiInfo不为空且WiFi已启用。
    if (wifiInfo != null && wifiManager.isWifiEnabled) {
      try {
        // 获取设备的IP地址。
        val ipAddress = wifiInfo.ipAddress
        // 将设备IP地址转换为InetAddress。
        val inetAddress = InetAddress.getByAddress(
          ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipAddress).array()
        )
        // 返回表示设备WiFi连接的IP地址的字符串。
        return inetAddress.hostAddress
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
    // 如果无法获取IP地址，则返回null。
    return null
  }

  private val _state = BehaviorSubject.create<Boolean>()

  init {
    val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    connectivityManager
      .registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {

          var job: Job? = null

          override fun onAvailable(network: Network) {
            // 注意：这里回调了 onAvailable 也不代表可用，只是表明连上了网络
            job = appCoroutineScope.launch {
              when (RedrockNetwork.tryPingNetWork()?.isSuccess) {
                true -> _state.onNext(true)
                false -> _state.onNext(false) // 后端服务问题
                null -> _state.onNext(false) // 连上了网络，但是网络不可用
              }
            }
          }

          override fun onLost(network: Network) {
            job?.cancel()
            _state.onNext(false)
          }
          // 打开应用就没网的时候 onUnavailable() 不会回调
        }
      )
    val networkCapabilities = connectivityManager
      .getNetworkCapabilities(connectivityManager.activeNetwork)
    if (networkCapabilities == null) {
      // 此时说明打开应用时就没有连接网络
      _state.onNext(false)
    }
  }

  private interface ApiServer : IApi {
    //仿ping接口，用于检测magipoke系列接口状态
    @GET("magipoke/ping")
    suspend fun pingMagipoke(): ApiStatus
  }
}