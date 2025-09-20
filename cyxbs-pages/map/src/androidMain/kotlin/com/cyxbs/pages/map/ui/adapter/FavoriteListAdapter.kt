package com.cyxbs.pages.map.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.cyxbs.pages.map.R
import com.cyxbs.pages.map.bean.FavoritePlace
import com.cyxbs.pages.map.viewmodel.MapViewModel
import com.cyxbs.components.view.text.MarqueeTextView
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible


/**
 *@author zhangzhe
 *@date 2020/8/12
 *@description
 */

class FavoriteListAdapter(context: Context, val viewModel: MapViewModel, private var mList: MutableList<FavoritePlace>) :
        RecyclerView.Adapter<FavoriteListAdapter.ViewHolder>() {

    private var mLayoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder {

        return ViewHolder(mLayoutInflater.inflate(R.layout.map_recycle_item_favorite_list, container, false))

    }

    fun setList(list: MutableList<FavoritePlace>) {
        mList.clear()
        mList.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return mList.size

    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.placeNickname.text = mList[position].placeNickname
        if (position == mList.size - 1) {
            holder.line.gone()
        } else {
            holder.line.visible()
        }
        holder.itemView.setOnSingleClickListener {
            viewModel.showIconById.value = mList[position].placeId
            viewModel.getPlaceDetails(mList[position].placeId, false)
            viewModel.showPopUpWindow.value = false
            viewModel.bottomSheetStatus.postValue(BottomSheetBehavior.STATE_COLLAPSED)
        }
    }


    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val placeNickname: MarqueeTextView = itemView.findViewById(R.id.map_tv_favorite_list_item_text)
        val line:View = itemView.findViewById(R.id.map_view_favorite_line)
    }

}