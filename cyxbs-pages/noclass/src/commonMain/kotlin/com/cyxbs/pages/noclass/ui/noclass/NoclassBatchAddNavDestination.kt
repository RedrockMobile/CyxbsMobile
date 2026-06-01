package com.cyxbs.pages.noclass.ui.noclass

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_NO_CLASS_BATCHADD
import com.cyxbs.pages.noclass.api.NoclassBatchAddArgument
import com.cyxbs.pages.noclass.ui.noClassCourse.NoclassBatchAddPage
import com.g985892345.provider.api.annotation.ImplProvider

@ImplProvider(clazz = MainNavDestination::class, name = NAV_NO_CLASS_BATCHADD)
class NoclassBatchAddNavDestination : MainNavDestination<NoclassBatchAddArgument>(NoclassBatchAddArgument::class) {
    override val needLogin: Boolean = false

    @Composable
    override fun DestinationContent(parcel: DestinationParcel<NoclassBatchAddArgument>) {
        NoclassBatchAddPage()
    }
}
