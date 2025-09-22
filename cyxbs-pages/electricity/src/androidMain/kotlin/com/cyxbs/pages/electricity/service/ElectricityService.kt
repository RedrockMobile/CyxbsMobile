package com.cyxbs.pages.electricity.service

import androidx.fragment.app.Fragment
import com.cyxbs.components.config.route.DISCOVER_ELECTRICITY_FEED
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.electricity.api.IElectricityService
import com.g985892345.provider.api.annotation.ImplProvider

/**
 * Created by yyfbe, Date on 2020/8/31.
 */
@ImplProvider
object ElectricityService : IElectricityService {
    override fun getElectricityFeed(): Fragment {
        return Fragment::class.impl(DISCOVER_ELECTRICITY_FEED)
    }
}