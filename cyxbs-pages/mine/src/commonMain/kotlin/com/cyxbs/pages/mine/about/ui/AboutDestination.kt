package com.cyxbs.pages.mine.about.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import com.cyxbs.components.config.APP_WEBSITE
import com.cyxbs.components.config.ICP_WEBSITE
import com.cyxbs.components.config.compose.theme.DefaultIndication
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainDestination
import com.cyxbs.components.config.navigation.NAV_ABOUT_ENTRY
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.components.utils.compose.shareText
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.utils.get.getAppVersionName
import com.cyxbs.functions.update.api.AppUpdateStatus
import com.cyxbs.functions.update.api.IAppUpdateService
import com.cyxbs.pages.mine.about.service.IAboutService
import com.g985892345.provider.api.annotation.ImplProvider
import cyxbsmobile.cyxbs_pages.mine.generated.resources.Res
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_arrow_right
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock

/**
 * @Desc : 关于我们页面
 * @Author : zzx
 * @Date : 2025/10/27 15:42
 */

// 关于我们的路由参数
@Serializable
object AboutArgument

@ImplProvider(clazz = MainDestination::class, name = NAV_ABOUT_ENTRY)
class AboutDestination : MainDestination<AboutArgument>(AboutArgument::class) {
    @Composable
    override fun DestinationContent(parcel: DestinationParcel<AboutArgument>) {
        AboutPage()
    }
}

@Composable
private fun AboutPage() {
    ConstraintLayout(
        constraintSet = createConstraintSet(),
        modifier = Modifier.fillMaxSize()
            .background(LocalAppColors.current.bottomBg)
            .systemBarsPadding(),
        animateChangesSpec = spring(
            stiffness = Spring.StiffnessMediumLow
        )
    ) {
        TopBarCompose(modifier = Modifier.layoutId(Element.Topbar))
        LogoCompose(modifier = Modifier.layoutId(Element.Logo))
        AppInfoCompose(modifier = Modifier.layoutId(Element.AppInfo))
        BackgroundIvCompose(modifier = Modifier.layoutId(Element.BackgroundIv))
        VersionUpdateCompose(modifier = Modifier.layoutId(Element.VersionUpdate))
        VersionInfoCompose(modifier = Modifier.layoutId(Element.VersionInfo))
        ProductWebsiteCompose(modifier = Modifier.layoutId(Element.ProductWebsite))
        ShareCompose(modifier = Modifier.layoutId(Element.Share))
        BottomInfoCompose(modifier = Modifier.layoutId(Element.BottomInfo))
    }
}

@Composable
private fun createConstraintSet(): ConstraintSet {
    val windowSize = getWindowScreenSize()
    return ConstraintSet {
        AboutConstraintSet(
            scope = this,
            windowSize = windowSize
        ).createConstrain()
    }
}

@Composable
private fun TopBarCompose(modifier: Modifier = Modifier) {
    TopAppBar(
        modifier = modifier,
        backgroundColor = Color.Transparent,
        elevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                painter = painterResource(ConfigRes.configIcBack()),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp)
                    .clickableNoIndicator {
                        MainNavController.popBackStack()
                    }
            )
            Text(
                text = "关于我们",
                fontSize = 20.sp,
                color = LocalAppColors.current.tvLv1,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LogoCompose(modifier: Modifier = Modifier) {
    Image(
        modifier = modifier.size(96.dp, 96.dp),
        painter = painterResource(ConfigRes.configIcAppLogo()),
        contentDescription = null
    )
}

@Composable
private fun AppInfoCompose(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "掌上重邮",
            fontSize = 20.sp,
            color = LocalAppColors.current.tvLv2,
            fontWeight = FontWeight.Bold
        )
        Text(
            modifier = Modifier.padding(top = 5.dp),
            text = "Version ${getAppVersionName()}",
            fontSize = 13.sp,
            color = LocalAppColors.current.tvLv2
        )
    }
}

@Composable
private fun BackgroundIvCompose(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = LocalAppColors.current.topBg,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
    )
}

@Composable
private fun VersionUpdateCompose(modifier: Modifier = Modifier) {
    val updateStatus = remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = DefaultIndication,
                onClick = {
                    val appUpdateService = IAppUpdateService
                    if (appUpdateService.getUpdateStatus().value == AppUpdateStatus.Result.Dated) {
                        appUpdateService.noticeUpdate()
                    } else coroutineScope.launch {
                        val status = appUpdateService.checkUpdate()
                        when (status) {
                            AppUpdateStatus.Result.Dated -> appUpdateService.noticeUpdate()
                            AppUpdateStatus.Result.Valid -> toast("已经是最新版了哦")
                            is AppUpdateStatus.Result.Error -> {
                                toast("有一股神秘力量阻拦了更新，可尝试点击「产品官网」下载最新版～")
                                // TODO 打开 CrashDialog
                            }
                        }
                    }
                },
                onLongClick = {
                    if (isDebug()) {
                        IAppUpdateService::class.implOrNull()?.debug()
                    }
                }
            )
            .padding(top = 9.dp, bottom = 9.dp)
    ) {
        Text(
            modifier = Modifier.padding(start = 20.dp),
            text = "版本更新",
            color = LocalAppColors.current.tvLv2,
            fontSize = 16.sp
        )
        Text(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 29.dp),
            text = updateStatus.value + if (isDebug()) " 长按测试(debug才显示)" else "",
            fontSize = 13.sp,
            color = 0x80294169.dark(0x48F0F0F2)
        )
        Image(
            modifier = Modifier.padding(end = 11.dp).size(width = 6.dp, height = 13.dp)
                .align(Alignment.CenterEnd),
            painter = painterResource(Res.drawable.mine_ic_arrow_right),
            contentDescription = null
        )
    }
    LaunchedEffect(Unit) {
        IAppUpdateService.getUpdateStatus().collectLatest {
            updateStatus.value = when (it) {
                AppUpdateStatus.Checking -> "检查中..."
                AppUpdateStatus.Result.Dated -> "发现新版本"
                AppUpdateStatus.Result.Valid -> "已是最新版本"
                is AppUpdateStatus.Result.Error -> "新版本请求失败 >_<"
            }
        }
    }
}

@Composable
private fun VersionInfoCompose(modifier: Modifier = Modifier) {
    val showState = remember { mutableStateOf(false) }
    InfoItem(modifier, "版本信息") {
        showState.value = true
    }
    UpdateInfoDialog(showState)
}

@Composable
private fun ProductWebsiteCompose(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    InfoItem(modifier, "产品官网") {
        uriHandler.openUri(APP_WEBSITE)
    }
}

@Composable
private fun ShareCompose(modifier: Modifier = Modifier) {
    InfoItem(modifier, "分享") {
        shareText("掌上重邮是重邮首款校园生活类App，拥有查课表，签到，邮问等功能，记得分享给好友哦。 $APP_WEBSITE")
    }
}

@Composable
private fun BottomInfoCompose(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val blueTextColor = 0xFF0BCCF0.dark(0xFF0BCCF0)
    val declareTextColor = 0x64294169.dark(0x48F0F0F2)
    val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                text = "《用户协议》",
                fontSize = 11.sp,
                color = blueTextColor,
                modifier = Modifier.clickableNoIndicator {
                    IAboutService::class.implOrNull()?.clickUserAgreement()
                }
            )
            Text(
                text = "&",
                fontSize = 11.sp,
                color = LocalAppColors.current.tvLv2
            )
            Text(
                text = "《隐私政策》",
                fontSize = 11.sp,
                color = blueTextColor,
                modifier = Modifier.clickableNoIndicator {
                    IAboutService::class.implOrNull()?.clickPrivacyPolicy()
                }
            )
        }
        Text(
            modifier = Modifier.padding(top = 5.dp),
            text = "红岩网校工作站出品",
            fontSize = 11.sp,
            color = declareTextColor
        )
        Text(
            modifier = Modifier.padding(top = 5.dp),
            text = "CopyRight © 2015-${currentYear} All Rights Reserved",
            fontSize = 11.sp,
            color = declareTextColor
        )
        Text(
            modifier = Modifier
                .padding(top = 5.dp)
                .clickableNoIndicator {
                    uriHandler.openUri(ICP_WEBSITE)
                },
            text = "ICP备案号: 渝ICP备17002788号-7A",
            fontSize = 11.sp,
            color = blueTextColor
        )
    }
}

@Composable
private fun InfoItem(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = MutableInteractionSource()
    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = DefaultIndication,
                onClick = onClick
            )
            .padding(top = 9.dp, bottom = 9.dp)
    ) {
        Text(
            modifier = Modifier.padding(start = 20.dp),
            text = text,
            color = LocalAppColors.current.tvLv2,
            fontSize = 16.sp
        )
        Image(
            modifier = Modifier.padding(end = 11.dp).size(width = 6.dp, height = 13.dp)
                .align(Alignment.CenterEnd),
            painter = painterResource(Res.drawable.mine_ic_arrow_right),
            contentDescription = null
        )
    }
}