package com.cyxbs.pages.affair.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.pages.affair.R
import com.cyxbs.pages.affair.ui.adapter.data.AffairWeekData.Companion.WEEK_ARRAY
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.setOnSingleClickListener

/**
 * author: WhiteNight(1448375249@qq.com)
 * date: 2022/9/7
 * description:
 */
class AffairWeekAdapter : ListAdapter<AffairWeekAdapter.AffairWeekSelectData, AffairWeekAdapter.VHolder>(
  object : DiffUtil.ItemCallback<AffairWeekSelectData>() {
    override fun areItemsTheSame(
      oldItem: AffairWeekSelectData,
      newItem: AffairWeekSelectData
    ): Boolean {
      return oldItem.week == newItem.week
    }

    override fun areContentsTheSame(
      oldItem: AffairWeekSelectData,
      newItem: AffairWeekSelectData
    ): Boolean {
      return oldItem.isChoice == newItem.isChoice
    }

    override fun getChangePayload(
      oldItem: AffairWeekSelectData,
      newItem: AffairWeekSelectData
    ): Any {
      return ""
    }
  }
) {
  
  data class AffairWeekSelectData(val week: Int, var isChoice: Boolean = false)

  inner class VHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val textView: TextView = itemView.findViewById(R.id.affair_tv_add_affair_week)

    init {
      textView.setOnSingleClickListener {
        val data = getItem(layoutPosition)
        submitList(checkWeekSelectData(currentList, data))
      }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VHolder {
    val view = LayoutInflater
      .from(parent.context).inflate(R.layout.affair_rv_item_edit_affair_week, parent, false)
    return VHolder(view)
  }

  override fun onBindViewHolder(holder: VHolder, position: Int) {
    holder.textView.text = WEEK_ARRAY[getItem(position).week]
    if (getItem(position).isChoice) {
      holder.textView.setTextColor(com.cyxbs.components.init.appContext.getColor(R.color.affair_edit_affair_select_week_tv))
    } else {
      holder.textView.setTextColor(com.cyxbs.components.init.appContext.getColor(com.cyxbs.components.config.R.color.config_level_two_font_color))
    }
  }
  
  /**
   * 判断整学期选择的逻辑
   */
  private fun checkWeekSelectData(
    weekList: List<AffairWeekSelectData>,
    weekData: AffairWeekSelectData
  ): List<AffairWeekSelectData> {
    val tmpList = arrayListOf<AffairWeekSelectData>()
    val newList = arrayListOf<AffairWeekSelectData>()
    // 先将选择状态反转
    weekList.forEach {
      if (it.week == weekData.week) tmpList.add(
        AffairWeekSelectData(
          it.week,
          !it.isChoice
        )
      ) else tmpList.add(it)
    }
    if (weekData.week != 0) {
      // 如果点击的不是整学期,将整学期重置为未选择
      tmpList[0] = AffairWeekSelectData(0, false)
    }
    // 整学期选择后,其他不能选择
    if (tmpList[0].isChoice) {
      newList.add(AffairWeekSelectData(0, true))
      for (i in 1 until weekList.size) {
        newList.add(AffairWeekSelectData(tmpList[i].week, false))
      }
    } else {
      // 反之,其他周数选择后,整学期不能选择
      newList.add(AffairWeekSelectData(0, false))
      for (i in 1 until weekList.size) {
        newList.add(AffairWeekSelectData(tmpList[i].week, tmpList[i].isChoice))
      }
    }
    return newList
  }
}