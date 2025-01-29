package com.cyxbs.pages.volunteer.service

import android.annotation.SuppressLint
import com.cyxbs.components.account.api.AccountState
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.init.InitialManager
import com.cyxbs.components.init.InitialService
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.service.impl
import com.cyxbs.pages.volunteer.event.VolunteerLogoutEvent
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.greenrobot.eventbus.EventBus

/**
 * .
 *
 * @author 985892345
 * 2023/8/10 00:00
 */
@ImplProvider(clazz = InitialService::class, name = "VolunteerInitialService")
class VolunteerInitialService : InitialService {
    @SuppressLint("CheckResult")
    override fun onMainProcess(manager: InitialManager) {
        super.onMainProcess(manager)
        IAccountService::class.impl().state
            .onEach {
                if (it == AccountState.Logout) {
                    // 以前学长用的 EventBus，还是粘性的，我也不知道为什么要这样写
                    EventBus.getDefault().postSticky(VolunteerLogoutEvent())
                }
            }.launchIn(appCoroutineScope)
    }
}