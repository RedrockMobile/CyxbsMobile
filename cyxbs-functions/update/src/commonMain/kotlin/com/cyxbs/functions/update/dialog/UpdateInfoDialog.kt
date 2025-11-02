package com.cyxbs.functions.update.dialog

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.navigation.DialogDestinationParcel
import com.cyxbs.components.config.navigation.MainNavDialog
import com.cyxbs.components.config.navigation.NAV_DIALOG_UPDATE
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.ui.ChooseDialogComposeContent
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 更新弹窗
 *
 * 支持 deeplink 跳转：cyxbs://dialog/update/{versionName}/{updateContent}/{downloadUrl}
 *
 * @author 985892345
 * @date 2025/11/2
 */
@Serializable
class UpdateInfoArgument(
  @SerialName("versionName")
  val versionName: String,
  @SerialName("updateContent")
  val updateContent: String,
  @SerialName("downloadUrl")
  val downloadUrl: String,
) {
  fun navigate() {
    MainNavController.navigate(this) {
      launchSingleTop = true
      restoreState = false
    }
  }
}

@ImplProvider(clazz = MainNavDialog::class, name = NAV_DIALOG_UPDATE)
class UpdateInfoDialog : MainNavDialog<UpdateInfoArgument>(
  argumentClass = UpdateInfoArgument::class,
  dialogProperties = DialogProperties(
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
  ),
) {
  override val needLogin: Boolean
    get() = false

  @Composable
  override fun DialogContent(parcel: DialogDestinationParcel<UpdateInfoArgument>) {
    ChooseDialogComposeContent(
      positiveBtnText = "立即更新",
      negativeBtnText = "下次一定",
      btnSize = DpSize(110.dp, 36.dp),
      onClickPositiveBtn = {
        // 下载更新
        IPlatformUpdateInfoDownload::class.implOrNull()
          ?.clickDownload(parcel.argument.downloadUrl)
          ?: toast("当前平台无法跳转更新")
      },
      onClickNegativeBtn = {
        MainNavController.popBackStack()
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
        text = "最新版本: " + parcel.argument.versionName + "\n" + parcel.argument.updateContent + "\n\n点击点击，现在就更新一发吧~",
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