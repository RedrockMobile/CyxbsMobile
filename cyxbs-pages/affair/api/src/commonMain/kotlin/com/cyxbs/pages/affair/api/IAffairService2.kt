package com.cyxbs.pages.affair.api

import kotlinx.coroutines.flow.StateFlow


/**
 * .
 *
 * @author 985892345
 * @date 2025/5/25
 */
interface IAffairService2 {

  /**
   * 观察登陆人的事务模型
   *
   * 事务模型中包含 增、删、改 相关功能，详情看 [AffairGroupModel]
   */
  fun observeAffairGroupModel(): StateFlow<AffairGroupModel?>
}