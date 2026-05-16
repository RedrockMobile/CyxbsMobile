package com.cyxbs.pages.course.page.course.ui.home.utils

import android.view.View
import android.view.ViewGroup
import android.view.animation.LayoutAnimationController
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.cyxbs.components.utils.extensions.anim
import com.cyxbs.pages.course.page.course.ui.home.viewmodel.HomeCourseViewModel
import com.cyxbs.pages.course.widget.R
import com.cyxbs.pages.course.widget.internal.view.course.ICourseViewGroup
import com.ndhzs.netlayout.child.OnChildExistListener

/**
 * 课表第一次进入动画
 *
 * @author 985892345
 * @date 2022/9/21 17:28
 */
object EnterAnimUtils {
  
  fun startEnterAnim(
    course: ICourseViewGroup,
    viewModel: HomeCourseViewModel,
    viewLifecycleOwner: LifecycleOwner
  ) {
    val viewList = arrayListOf<View>()
    // 把所有添加进来的 View 都修改透明度为 0
    viewList.addAll(course.getIterable().onEach { it.alpha = 0F })
    val viewListener = object : OnChildExistListener {
      override fun onChildViewAdded(parent: ViewGroup, child: View) {
        child.alpha = 0F
        viewList.add(child)
      }
      
      override fun onChildViewRemoved(parent: ViewGroup, child: View) {
        child.alpha = 1F
        viewList.remove(child)
      }
    }
    course.addChildExistListener(viewListener)
    // 观察 BottomSheet 滑动的值
    viewModel.courseService
      .bottomSheetSlideOffset
      .observe(
        viewLifecycleOwner,
        object : Observer<Float> {
          override fun onChanged(value: Float) {
            if (value > 0.8F) {
              // 执行一次就取消观察
              viewModel.courseService.bottomSheetSlideOffset.removeObserver(this)
              // 设置入场动画
              course.setLayoutAnimation(
                LayoutAnimationController(
                  R.anim.course_widget_anim_entrance.anim,
                  0.1F
                )
              )
              course.startLayoutAnimation() // 现在已经布局了，需要手动开启动画执行
              viewList.forEach { it.alpha = 1F }
              course.removeChildExitListener(viewListener) // 移除上面设置的监听
            }
          }
        }
      )
    viewLifecycleOwner.lifecycle.addObserver(
      object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
          // 需要在这里移除动画，防止 Fragment 重新加载而触发动画
          course.setLayoutAnimation(null)
          owner.lifecycle.removeObserver(this)
        }
      }
    )
  }
}