package com.cyxbs.functions.update.service

import android.content.Intent
import android.net.Uri
import com.cyxbs.components.init.appTopActivity
import com.cyxbs.functions.update.dialog.IPlatformUpdateInfoDownload
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * .
 *
 * @author 985892345
 * @date 2025/11/2
 */
@ImplProvider
class AndroidUpdateInfoDownload : IPlatformUpdateInfoDownload {
  override fun clickDownload(downloadUrl: String) {
    /*
    * 22-8-30
    * 因为应用内更新有很多毛病，所以采用浏览器下载
    * */
    runCatching {
      appTopActivity.get()?.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
      )
    }.onFailure {
      // 如果 activity 找不到，可能链接存在问题，重定向到官网
      appTopActivity.get()?.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://app.redrock.team/#/download"))
      )
    }
  }
}