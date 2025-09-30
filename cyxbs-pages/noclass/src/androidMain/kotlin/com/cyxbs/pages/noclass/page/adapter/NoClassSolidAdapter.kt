package com.cyxbs.pages.noclass.page.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.pages.noclass.R
import com.cyxbs.pages.noclass.bean.NoClassGroup
import com.cyxbs.pages.noclass.callback.OnSlideChangedListener
import com.cyxbs.pages.noclass.widget.SlideMenuLayout


/**
 * 没课约固定分组界面的adapter
 */
class NoClassSolidAdapter : ListAdapter<NoClassGroup, NoClassSolidAdapter.MyHolder>(solidDiffUtil) {
    companion object {
        private val solidDiffUtil: DiffUtil.ItemCallback<NoClassGroup> =
            object : DiffUtil.ItemCallback<NoClassGroup>() {
                override fun areItemsTheSame(
                    oldItem: NoClassGroup,
                    newItem: NoClassGroup
                ): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(
                    oldItem: NoClassGroup,
                    newItem: NoClassGroup
                ): Boolean {
                    return oldItem == newItem
                }
            }
    }

    //分别是点击名称，置顶，和删除的回调
    private var onClickGroup : ((noclassGroup: NoClassGroup) -> Unit)? = null
    private var onClickGroupIsTop : ((noclassGroup: NoClassGroup, tvIsGroup : TextView) -> Unit)? = null
    private var onClickGroupDelete : ((noclassGroup: NoClassGroup) -> Unit)? = null

    //当前右滑打开的位置
    var rightSlideOpenLoc : Int? = null

    private var mOnItemSlideBack : ((loc : Int) -> Unit)? = null

    fun setOnClickGroup(onClickGroup : (noclassGroup: NoClassGroup) -> Unit){
        this.onClickGroup = onClickGroup
    }

    fun setOnClickGroupIsTop(onClickGroupIsTop : (noclassGroup: NoClassGroup, tvIsGroup : TextView) -> Unit){
        this.onClickGroupIsTop = onClickGroupIsTop
    }

    fun setOnClickGroupDelete(onClickGroupDelete : (noclassGroup: NoClassGroup) -> Unit){
        this.onClickGroupDelete = onClickGroupDelete
    }

    /**
     * 设置上一个滑动的item的closeRight
     */
    fun setOnItemSlideBack(func : (loc : Int) -> Unit){
        this.mOnItemSlideBack = func
    }

    inner class MyHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvGroupName: TextView = itemView.findViewById(R.id.tv_noclass_group_name)
        val tvGroupIsTop: TextView = itemView.findViewById(R.id.tv_noclass_group_top_name)
        private val tvGroupDelete: TextView = itemView.findViewById(R.id.tv_noclass_group_delete_item)
        val mMenuLayout : SlideMenuLayout = itemView.findViewById(R.id.slide_noclass_container)
        init {
            tvGroupIsTop.text = "取消置顶"
            mMenuLayout.setOnTapTouchListener {
                mMenuLayout.closeRightSlide()
                val item = getItem(bindingAdapterPosition)
                onClickGroup?.invoke(item)
            }
            tvGroupIsTop.setOnClickListener {
                mMenuLayout.closeRightSlide()
                val item = getItem(bindingAdapterPosition)
                onClickGroupIsTop?.invoke(item,tvGroupIsTop)
            }
            tvGroupDelete.setOnClickListener {
                mMenuLayout.closeRightSlide()
                val item = getItem(bindingAdapterPosition)
                onClickGroupDelete?.invoke(item)
                rightSlideOpenLoc = null
            }
            mMenuLayout.setOnSlideChangedListener(object : OnSlideChangedListener {
                override fun onSlideStateChanged(
                    slideMenu: SlideMenuLayout,
                    isLeftSlideOpen: Boolean,
                    isRightSlideOpen: Boolean
                ) {
                    if (isRightSlideOpen){
                        // 在滑动另外一个之前先把其它打开的关闭
                        rightSlideOpenLoc?.let {
                            if (it != bindingAdapterPosition){
                                mOnItemSlideBack?.invoke(it)
                            }
                        }
                        rightSlideOpenLoc = bindingAdapterPosition
                    }
                }
                override fun onSlideRightChanged(percent: Float) {}

                override fun onSlideLeftChanged(percent: Float) {}

            })
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        return MyHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.noclass_item_group_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        val item = getItem(position)
        holder.apply {
            tvGroupName.text = item.name
            tvGroupIsTop.text = if(item.isTop) "取消置顶" else "置顶"
            if (item.isOpen){
                mMenuLayout.openRightSlide()
            }else{
                mMenuLayout.closeRightSlide()
            }
        }
    }
    /**
     * 将传进来的列表变成有序列表，上面是置顶
     */
    fun submitListToOrder(noclassGroups: List<NoClassGroup>){
        val orderList = ArrayList<NoClassGroup>()
        noclassGroups.forEach {
            if (it.isTop){
                orderList.add(0,it)
            }else{
                orderList.add(it)
            }
        }
        submitList(orderList)
    }

    fun deleteGroup(noclassGroup: NoClassGroup){
        val list = currentList.toMutableList()
        list.remove(noclassGroup)
        submitList(list)
    }

    /**
     * 远端更新成功之后就调用，将该条数据按照是否有序插入并且提交，相比于submitListToOrder减少了比较
     */
    fun addItemToOrder(item : NoClassGroup, tvIsGroup : TextView){
        val list = currentList.toMutableList()
        if (item.isTop){
            tvIsGroup.text = "置顶"
            list.remove(item)
            //取消置顶就放到最后
            list.add(NoClassGroup(item.id,false,item.members,item.name))  //置顶效果
        }else{
            tvIsGroup.text = "取消置顶"
            list.remove(item)
            //置顶就放到第一个
            list.add(0, NoClassGroup(item.id,true,item.members,item.name))  //置顶效果
        }
        submitList(list)
    }
}