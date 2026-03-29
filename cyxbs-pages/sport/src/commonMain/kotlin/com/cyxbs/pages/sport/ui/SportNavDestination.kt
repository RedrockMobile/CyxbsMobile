package com.cyxbs.pages.sport.ui

import com.cyxbs.pages.sport.api.SportNavArgument
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.dp
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
        modifier = modifier
    ) {
        Image(
            modifier = Modifier
                .padding(4.dp)
                .clickableNoIndicator {
                    MainNavController.popBackStack()
                },
            painter = painterResource(ConfigRes.configIcBack()),
            contentDescription = "back",
            contentScale = ContentScale.Crop
        )
    }
}

