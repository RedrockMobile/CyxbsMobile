package com.cyxbs.pages.declare.detail.page.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.pages.declare.R
import com.cyxbs.pages.declare.detail.bean.VoteData
import com.cyxbs.pages.declare.detail.widget.IncreaseCardView
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.setOnSingleClickListener

/**
 * ... 投票详情页面的Adapter
 * @author RQ527 (Ran Sixiang)
 * @email 1799796122@qq.com
 * @date 2023/2/13
 * @Description:
 */
class DetailRvAdapter :
    ListAdapter<VoteData, DetailRvAdapter.InnerHolder>(
        object : DiffUtil.ItemCallback<VoteData>() {
            override fun areItemsTheSame(oldItem: VoteData, newItem: VoteData): Boolean {
                return oldItem.choice == newItem.choice
            }

            override fun areContentsTheSame(oldItem: VoteData, newItem: VoteData): Boolean {
                return newItem.voted == oldItem.voted//投票和未投票状态不一样就差分刷新
            }

        }) {


    private val commonTextColor = appContext.getColor(com.cyxbs.components.config.R.color.config_level_three_font_color)//未投票的文字颜色
    private val votedTextColor = appContext.getColor(R.color.declare_voted_text)//所投选项的文字颜色
    private val noVoteTextColor = appContext.getColor(R.color.declare_novoted_text)//不是所投选项的文字颜色

    private val commonBgColor = Color.parseColor("#0D0028FC")//未投票的背景颜色
    private val votedBgColor = Color.parseColor("#554FFD")//所投选项的背景颜色
    private val votedPercentColor = Color.parseColor("#453EF1")//所投选项的占比背景颜色
    private val noVotePercentColor = Color.parseColor("#1A4A44E4")//不是所投选项的占比背景颜色
    private val noVotePercentTextColor = appContext.getColor(com.cyxbs.components.config.R.color.config_alpha_level_two_font_color)//不是所投选项的百分数文字颜色

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InnerHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.declare_item_detail_rv, parent, false)
        return InnerHolder(view)
    }

    override fun onBindViewHolder(holder: InnerHolder, position: Int) {
        val data = getItem(position)
        holder.run {
            if (data.voted == null) {//未投票
                choiceTv.setTextColor(commonTextColor)
                coverCd.cancelIncrease()
                coverCd.background.setTint(commonBgColor)
                votedBtn.visibility = View.GONE
                percentTv.visibility = View.GONE
            } else {//投票
                percentTv.visibility = View.VISIBLE
                if (data.voted == data.choice) {//是所投选项
                    votedBtn.visibility = View.VISIBLE
                    percentTv.setTextColor(votedTextColor)
                    choiceTv.setTextColor(votedTextColor)
                    coverCd.background.setTint(votedBgColor)
                    coverCd.setPercent(data.percent, votedPercentColor)
                } else {//不是所投选项
                    votedBtn.visibility = View.GONE
                    percentTv.setTextColor(noVotePercentTextColor)
                    choiceTv.setTextColor(noVoteTextColor)
                    coverCd.background.setTint(commonBgColor)
                    coverCd.setPercent(data.percent, noVotePercentColor)
                }
                val percentStr = "${data.percent}%"
                percentTv.text = percentStr
            }
            choiceTv.text = data.choice
        }
    }

    inner class InnerHolder(view: View) : RecyclerView.ViewHolder(view) {
        val choiceTv: TextView
        val votedBtn: ImageView
        val percentTv: TextView
        val coverCd: IncreaseCardView

        init {
            itemView.run {
                choiceTv = findViewById(R.id.declare_detail_item_tv_choice)
                votedBtn = findViewById(R.id.declare_detail_item_iv_voted)
                percentTv = findViewById(R.id.declare_detail_item_tv_percent)
                coverCd = findViewById(R.id.declare_detail_item_cd_cover)
                coverCd.setOnSingleClickListener {
                    listener?.invoke(getItem(absoluteAdapterPosition))
                }
            }
        }
    }

    private var listener: ((data: VoteData) -> Unit)? = null

    fun setOnClickedListener(listener: (data: VoteData) -> Unit) {
        this.listener = listener
    }

}