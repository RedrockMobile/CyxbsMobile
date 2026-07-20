package com.cyxbs.pages.mine.sign.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SIGN
import com.cyxbs.pages.mine.sign.viewmodel.SignComposeViewModel
import kotlinx.serialization.Serializable

/**  
 * description: 签到页
 * author: zzx
 * email: 1487144524@qq.com
 * date: 2026/7/19 13:28
 */
@Serializable
object SignNavArgument : AppNavArgument

@AppNav(route = NAV_SIGN)
class SignNavEntry : AppNavEntry<SignNavArgument>() {
  override fun isNeedLogin(argument: SignNavArgument): Boolean {
    return true
  }

  @Composable
  override fun Content(argument: SignNavArgument) {
    viewModel { SignComposeViewModel() }
    SignScreen(
      onBack = {
        argument.popBackStack()
      }
    )
  }

}