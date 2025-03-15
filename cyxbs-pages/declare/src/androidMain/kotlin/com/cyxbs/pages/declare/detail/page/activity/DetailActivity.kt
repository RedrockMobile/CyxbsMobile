package com.cyxbs.pages.declare.detail.page.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.declare.R
import com.cyxbs.pages.declare.detail.bean.VoteData
import com.cyxbs.pages.declare.detail.page.adapter.DetailRvAdapter
import com.cyxbs.pages.declare.detail.page.viewmodel.DetailViewModel
import com.cyxbs.pages.store.api.IStoreService

class DetailActivity : BaseActivity() {
    companion object {
        /**
         * 启动投票详情页面
         */
        fun startActivity(context: Context, id: Int) {
            context.startActivity(
                Intent(
                    context,
                    DetailActivity::class.java
                ).apply { putExtra("id", id) })
        }
    }

    private val mViewModel by viewModels<DetailViewModel>()

    private val declareDetailRecyclerview by R.id.declare_detail_recyclerview.view<RecyclerView>()
    private val declareDetailIvToolbarArrowLeft by R.id.declare_detail_iv_toolbar_arrow_left.view<View>()
    private val declareDetailTitle by R.id.declare_detail_title.view<TextView>()
    private val declareDetailCl by R.id.declare_detail_cl.view<View>()
    private val declareDetailNoNet by R.id.declare_detail_no_net.view<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.declare_activity_detail)
        val id = intent.getIntExtra("id", -1)
        //因为投票后返回的是个map，map是无序的，所以这里用个list记下未投票之前的选项排布顺序
        val voteDataList = mutableListOf<VoteData>()

        val declareDetailRvAdapter = DetailRvAdapter()

        declareDetailRecyclerview.run {
            layoutManager = LinearLayoutManager(this@DetailActivity)
            adapter = declareDetailRvAdapter
        }

        declareDetailIvToolbarArrowLeft.setOnClickListener {
            finish()
        }

        mViewModel.detailLiveData.observe {
            voteDataList.clear()
            declareDetailTitle.text = it.title
            val votedList = mutableListOf<VoteData>()//差分刷新要求 源数据集和新数据集 不是同一个对象才能生效

            if (it.choices != null) {//防止后端返回个没有选项的投票
                for (s: String in it.choices) {//未投票
                    voteDataList.add(VoteData(it.voted, s, 0))
                }
            }

            if (it.statistic != null) {//投过票
                var sumVotes = 0
                for (v in it.statistic) {//计算总票数
                    sumVotes += v.value
                }
                for (data in voteDataList) {//计算占比（不知道为什么占比不在后端计算-_-|||）
                    data.percent = (it.statistic[data.choice]!! * 100) / sumVotes
                }
            }
            votedList.addAll(voteDataList)//除去旧的数据集
            declareDetailRvAdapter.submitList(votedList)//每次提交新的数据集
        }

        mViewModel.votedLiveData.observe {
            var sumVotes = 0
            val votedList = mutableListOf<VoteData>()//差分刷新要求 源数据集和新数据集 不是同一个对象才能生效
            for (data in voteDataList) { //计算总票数
                sumVotes += it.statistic[data.choice]!!
            }
            for (data in voteDataList) {//计算占比
                data.percent = (it.statistic[data.choice]!! * 100) / sumVotes
                votedList.add(VoteData(it.voted, data.choice, data.percent))
            }
            declareDetailRvAdapter.submitList(votedList)
            IStoreService::class.impl().postTask(
                IStoreService.Task.JOIN_DECLARE,
                "",
                "今日已完成表态一次，获得10邮票"
            )
        }

        mViewModel.cancelLiveData.observe {
            if (it.Id == id) mViewModel.getDeclareDetail(id)//取消投票成功就再刷新一下数据
        }

        declareDetailRvAdapter.setOnClickedListener {
            if (it.voted == null) {//未投票
                mViewModel.putChoice(id, it.choice)
            } else if (it.voted == it.choice) {//已经投票并且点击的是投票选项就取消投票
                mViewModel.cancelChoice(id)
            }
        }

        mViewModel.errorLiveData.observe {
            if (it) {
                declareDetailCl.gone()
                declareDetailNoNet.visible()
            } else {
                declareDetailCl.visible()
                declareDetailNoNet.gone()
            }
        }

        mViewModel.getDeclareDetail(id)
    }
}