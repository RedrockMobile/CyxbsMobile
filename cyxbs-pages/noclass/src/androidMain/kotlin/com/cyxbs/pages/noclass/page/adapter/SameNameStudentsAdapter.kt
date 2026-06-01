package com.cyxbs.pages.noclass.page.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.utils.extensions.string
import com.cyxbs.pages.noclass.R
import com.cyxbs.pages.noclass.bean.NoClassBatchResponseInfo.BatchStudent as Student

/**
 * ...
 * @author: Black-skyline
 * @email: 2031649401@qq.com
 * @date: 2023/8/19
 * @Description:
 *
 */
class SameNameStudentsAdapter(val setDoneStatus: (isChooseAny: Boolean, selectedList: List<Student>) -> Unit) :
    ListAdapter<Student, SameNameStudentsAdapter.ViewHolder>(diffUtil) {
    companion object {
        private val diffUtil: DiffUtil.ItemCallback<Student> =
            object : DiffUtil.ItemCallback<Student>() {
                override fun areItemsTheSame(oldItem: Student, newItem: Student): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(oldItem: Student, newItem: Student): Boolean {
                    return oldItem.id == newItem.id &&
                            oldItem.name == newItem.name &&
                            oldItem.major == newItem.major
                }
            }
    }

    /**
     * 已选择的重名学生的 暂存集合
     */
    private val hasSelected by lazy { ArrayList<Student>() }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val studentName: TextView = itemView.findViewById(R.id.noclass_batch_tv_student_name)
        val studentInfo: TextView =
            itemView.findViewById(R.id.noclass_batch_tv_student_other_info)
        val isSelected: CheckBox =
            itemView.findViewById(R.id.noclass_batch_cb_student_select_status)

        init {
            isSelected.setOnCheckedChangeListener { _, status ->
                val itemData = getItem(bindingAdapterPosition)
                itemData.isSelected = status
                if (status) { // 如果选中了
                    if (!hasSelected.contains(itemData))
                        hasSelected.add(itemData)
                } else { // 取消选中
                    if (hasSelected.contains(itemData))
                        hasSelected.remove(itemData)
                }
                // 判断已选择的重名学生的 暂存集合是否为空
                if (hasSelected.isEmpty()) {
                    setDoneStatus(false, hasSelected)
                } else {
                    setDoneStatus(true, hasSelected)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.noclass_item_batch_student, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val itemData = getItem(position)
        holder.studentName.text = itemData.name
        holder.studentInfo.text = String.format(
            R.string.noclass_batch_same_name_student_info.string,
            itemData.major,
            itemData.id
        )
        holder.isSelected.isChecked = getItem(position).isSelected
    }
}