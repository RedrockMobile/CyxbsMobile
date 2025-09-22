package com.cyxbs.pages.course.service

import com.cyxbs.pages.course.api.ILinkService2
import com.cyxbs.pages.course.model.LinkLessonRepository
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.StateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/22
 */
@ImplProvider
object LinkServiceImpl2 : ILinkService2 {
  override val state: StateFlow<ILinkService2.LinkStu>
    get() = LinkLessonRepository.state
}