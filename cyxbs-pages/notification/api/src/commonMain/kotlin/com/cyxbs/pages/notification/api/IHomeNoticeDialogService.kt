package com.cyxbs.pages.notification.api

import androidx.compose.runtime.Composable
import com.cyxbs.components.config.service.impl

/**
 * 首页重要通知
 *
 * 接口文档：https://rbtdi8ocgh.feishu.cn/docx/Dq6vdhQl2oW4UTxekS6cwInynrg
 *
 * @author 985892345
 * @date 2025/11/3
 */
interface IHomeNoticeDialogService {

  @Composable
  fun HomeNoticeDialogContent()

  companion object : IHomeNoticeDialogService by IHomeNoticeDialogService::class.impl()
}