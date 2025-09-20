package com.cyxbs.pages.home.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.cyxbs.components.config.route.DISCOVER_ENTRY
import com.cyxbs.components.config.route.FAIRGROUND_ENTRY
import com.cyxbs.components.config.route.MINE_ENTRY
import com.cyxbs.components.config.service.impl

/**
 * Created by dingdeqiao on 2021/3/16
 * 主页功能页Adapter
 */
class MainAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
  override fun getItemCount() = 3
  override fun createFragment(position: Int): Fragment {
    return when (position) {
      0 -> Fragment::class.impl(DISCOVER_ENTRY)
      1 -> Fragment::class.impl(FAIRGROUND_ENTRY)
      2 -> Fragment::class.impl(MINE_ENTRY)
      else -> error("??? 改了 getItemCount() 为什么不改这个 ?")
    }
  }
}