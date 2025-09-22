package com.cyxbs.pages.affair.ui.activity

import android.content.Intent
import android.os.Bundle
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.init.appContext

/**
 * .
 *
 * @author 985892345
 * @date 2022/9/24 19:53
 */
class DeleteRemindActivity : BaseActivity() {
  companion object {
    fun start(affairId: Int) {
      com.cyxbs.components.init.appContext.startActivity(
        Intent(com.cyxbs.components.init.appContext, DeleteRemindActivity::class.java)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          .putExtra(DeleteRemindActivity::mAffairId.name, affairId)
      )
    }
  }
  
  private val mAffairId by intent<Int>()
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
  }
}