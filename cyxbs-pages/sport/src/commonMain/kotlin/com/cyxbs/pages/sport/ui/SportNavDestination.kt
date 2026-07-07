package com.cyxbs.pages.sport.ui

import com.cyxbs.pages.sport.api.SportNavArgument
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_SPORT
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.pages.sport.viewModel.SportViewModel
import com.g985892345.provider.api.annotation.ImplProvider
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@ImplProvider(clazz = MainNavDestination::class, name = NAV_SPORT)
class SportNavDestination : MainNavDestination<SportNavArgument> (argumentClass = SportNavArgument::class){
    override val needLogin: Boolean
        get() = true

    @Composable
    override fun DestinationContent(parcel: DestinationParcel<SportNavArgument>) {
        SportPage()
        viewModel { SportViewModel() }
    }

}

@Preview(showBackground = true)
@Composable
fun SportPage(){
    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
            .background(LocalAppColors.current.topBg)
            .systemBarsPadding(),
        constraintSet = createConstraintSet()
    ) {
        TopBarCompose(modifier = Modifier.layoutId(SportElement.TopBar))
    }
}

@Composable
private fun createConstraintSet(): ConstraintSet {
    val windowSize = getWindowScreenSize()
    return ConstraintSet {
        SportConstraintSet(
            scope = this,
            windowSize = windowSize
        ).createConstrain()
    }
}

@Composable
private fun TopBarCompose(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            modifier = Modifier
                .padding(start = 15.dp)
                .clickableNoIndicator {
                    MainNavController.popBackStack()
                },
            painter = painterResource(ConfigRes.configIcBack()),
            contentDescription = "back",
            contentScale = ContentScale.Crop
        )
        Text(
            modifier = Modifier
                .padding(start = 20.dp)
                .align(Alignment.CenterVertically),
            text = "体育打卡",
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.tvLv3,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            modifier = Modifier
                .padding(end = 15.dp)
                .align(Alignment.CenterVertically ),
            text = "2026年 春",
            color = 0xFF697c9b.dark(0xFF606061),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

