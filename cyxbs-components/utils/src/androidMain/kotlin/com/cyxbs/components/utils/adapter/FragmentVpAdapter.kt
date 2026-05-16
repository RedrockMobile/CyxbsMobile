package com.cyxbs.components.utils.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 *
 * 正确的 FragmentStateAdapter 用法
 * ```
 * // 下面这种封装是错误的 !!!
 *
 * class FragmentVpAdapter(activity: FragmentActivity, list: List<Fragment>) : FragmentStateAdapter(activity) {
 *     override fun createFragment(position: Int): Fragment = list[position]
 * }
 *
 * val list = listOf(Fragment1(), Fragment2())
 * FragmentVpAdapter(this, list)
 * ```
 *
 * ## 原因
 * createFragment() 方法并不是每次都会回调，如果经历了屏幕旋转等，Activity 恢复后创建的 Vp2 他会自动帮你还原 Fragment，
 * 此时就不会再回调 createFragment()
 *
 * 你传进来的 list 就创建了多余的 Fragment，如果你还直接拿来用，就会导致一系列的问题
 *
 * 所以需要使用接口延迟创建 Fragment，正确的创建时机是在 createFragment() 回调时
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/7/25 18:22
 */
open class FragmentVpAdapter private constructor(
  fragmentManager: FragmentManager,
  lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {
  
  constructor(activity: FragmentActivity) : this(activity.supportFragmentManager, activity.lifecycle)
  constructor(fragment: Fragment) : this(fragment.childFragmentManager, fragment.lifecycle)
  
  protected val mFragmentList = arrayListOf<() -> Fragment>()
  
  open fun add(fragment: () -> Fragment): FragmentVpAdapter {
    mFragmentList.add(fragment)
    return this
  }
  
  open fun add(fragment: Class<out Fragment>): FragmentVpAdapter {
    // 官方源码中在恢复 Fragment 时就是调用的这个反射方法，该方法应该不是很耗性能 :)
    mFragmentList.add { fragment.getDeclaredConstructor().newInstance() }
    return this
  }
  
  override fun getItemCount(): Int = mFragmentList.size
  
  override fun createFragment(position: Int): Fragment = mFragmentList[position].invoke()
}