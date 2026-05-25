package com.cyxbs.functions.update.dialog

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.scene.DialogSceneStrategy
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_DIALOG_UPDATE
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.ui.ChooseDialogComposeContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 更新弹窗
 *
 * 支持 deeplink 跳转：cyxbs://dialog/update?versionName={}&updateContent={}&downloadUrl={}
 *
 * @author 985892345
 * @date 2025/11/2
 */
@Serializable
class UpdateInfoNavArgument(
  @SerialName("versionName")
  val versionName: String,
  @SerialName("updateContent")
  val updateContent: String,
  @SerialName("downloadUrl")
  val downloadUrl: String,
) : AppNavArgument {
}

@AppNav(route = NAV_DIALOG_UPDATE)
class UpdateInfoDialogNavEntry : AppNavEntry<UpdateInfoNavArgument>() {

  override fun isNeedLogin(argument: UpdateInfoNavArgument): Boolean {
    return false
  }

  override fun getContentKey(argument: UpdateInfoNavArgument): String {
    return "UpdateInfoNavArgument" // 当成单例来显示更新弹窗
  }

  override fun buildMetadata(argument: UpdateInfoNavArgument): Map<String, Any> {
    return DialogSceneStrategy.dialog(
      dialogProperties = DialogProperties(
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
      )
    )
  }

  @Composable
  override fun Content(argument: UpdateInfoNavArgument) {
    ChooseDialogComposeContent(
      positiveBtnText = "立即更新",
      negativeBtnText = "下次一定",
      onClickPositiveBtn = {
        // 下载更新
        IPlatformUpdateInfoDownload::class.implOrNull()
          ?.clickDownload(argument.downloadUrl)
          ?: toast("当前平台无法跳转更新")
      },
      onClickNegativeBtn = {
        argument.popBackStack()
      },
    ) {
      Text(
        text = "有新版本更新",
        color = LocalAppColors.current.tvLv1,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, start = 24.dp)
      )
      Text(
        text = "最新版本: " + argument.versionName + "\n" + argument.updateContent + "\n\n点击点击，现在就更新一发吧~",
        color = LocalAppColors.current.tvLv4,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 16.dp, start = 24.dp)
      )
    }
  }
}

interface IPlatformUpdateInfoDownload {
  fun clickDownload(downloadUrl: String)
}