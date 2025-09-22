package com.cyxbs.functions.pandora

import com.cyxbs.components.utils.network.INetworkConfigService
import com.g985892345.provider.api.annotation.ImplProvider
import okhttp3.OkHttpClient
import tech.linjiang.pandora.Pandora

/**
 * .
 *
 * @author 985892345
 * @date 2024/2/16 19:31
 */
@ImplProvider(clazz = INetworkConfigService::class, name = "PandoraNetworkConfigService")
object PandoraNetworkConfigService : INetworkConfigService {
  override fun onCreateOkHttp(builder: OkHttpClient.Builder) {
    builder.addInterceptor(Pandora.get().interceptor)
  }
}