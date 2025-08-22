package com.cyxbs.pages.qa.home.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.filter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.pages.qa.R
import com.cyxbs.pages.qa.detail.ui.DetailActivity
import com.cyxbs.pages.qa.home.HomeActivity
import com.cyxbs.pages.qa.home.SearchActivity
import com.cyxbs.pages.qa.home.adapter.QaHomeRVAdapter
import com.cyxbs.pages.qa.home.adapter.QaSearchRVAdapter
import com.cyxbs.pages.qa.home.model.bean.NewMessageAnalyzer
import com.cyxbs.pages.qa.home.viewmodel.HomeViewModel
import com.cyxbs.pages.qa.home.viewmodel.SearchViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * description ： Qa全部问题展示
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/13 20:30
 */
class QaAllFragment : BaseFragment() {
    private val newMessageAnalyzer: NewMessageAnalyzer by lazy { NewMessageAnalyzer(requireContext()) }
    private val homeViewModel: HomeViewModel by lazy { ViewModelProvider(requireActivity())[HomeViewModel::class.java] }
    private val searchViewModel: SearchViewModel by lazy { ViewModelProvider(requireActivity())[SearchViewModel::class.java] }
    private val mRecycleView by R.id.qa_all_rv.view<RecyclerView>()
    private val homeRvAdapter: QaHomeRVAdapter by lazy {
        QaHomeRVAdapter(homeViewModel).apply {
            /*
            这里为什么这样传，为了防止内存泄漏
            为什么不算强引用
            如果 Lambda 捕获了 Fragment 本身，Adapter 持有这个 Lambda，Fragment 就会被引用，不能回收 → 泄漏。
            这里通过 context? 临时访问 Context，没有把 Fragment 本身或 Context 对象长期保存在 Adapter 内部。
            Fragment 销毁后，context 变成 null，Lambda 调用不会阻止 GC 回收 Fragment。
             */
            setOnItemClickListener { id ->
                context?.let { ctx -> DetailActivity.startActivity(ctx, id) }
            }
        }
    }
    private val searchRVAdapter: QaSearchRVAdapter by lazy {
        QaSearchRVAdapter(searchViewModel).apply {
            setOnItemClickListener { id ->
                context?.let { ctx -> DetailActivity.startActivity(ctx, id) }
            }
        }
    }

    //监听页面刷新状态 添加新消息的处理
    private var homeLoadStateListener: ((CombinedLoadStates) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.qa_fragment_all, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun initView() {
        //因为这几个fragment两个activity使用，这里需要判断究竟是哪一个
        when (requireActivity()) {
            is HomeActivity -> initHomeView()
            is SearchActivity -> initSearchView()
            else -> initDefaultView()
        }

    }

    private fun initHomeView() {
        mRecycleView.adapter = homeRvAdapter
        mRecycleView.layoutManager = LinearLayoutManager(context)

        var isDotUpdated = false
        homeLoadStateListener = { loadStates ->
            val refreshState = loadStates.refresh
            val appendState = loadStates.append

            // 判断首次加载完成，并且是第一页加载结束
            if (!isDotUpdated &&
                refreshState is LoadState.NotLoading &&
                appendState is LoadState.NotLoading &&
                homeRvAdapter.itemCount >= homeRvAdapter.snapshot().items.size // 或者 pageSize
            ) {
                val stats = newMessageAnalyzer.analyze(homeRvAdapter.snapshot().items)
                (activity as? HomeActivity)?.apply {
                    updateTabDot(1, stats.newStudentCount)
                    updateTabDot(2, stats.lifeCount)
                    updateTabDot(3, stats.learningCount)
                    updateTabDot(4, stats.otherCount)
                }
                isDotUpdated = true
            }
        }

        // 添加监听
        homeLoadStateListener?.let { homeRvAdapter.addLoadStateListener(it) }

        viewLifecycleScope.launch {
            homeViewModel.pagingDataFlow.collectLatest { pagingData ->
                val filteredPagingData = pagingData.filter { it.status == 2 }
                homeRvAdapter.submitData(filteredPagingData)

            }
        }

    }


    private fun initSearchView() {
        mRecycleView.adapter = searchRVAdapter
        mRecycleView.layoutManager = LinearLayoutManager(context)
        searchViewModel.items.observe(viewLifecycleOwner) { qaData ->
            val filteredPagingData = qaData.filter { it.status == 2 }
            val isFullRefresh = searchViewModel.isFullRefresh.value ?: true
            if (isFullRefresh) {
                context?.getSp("search_keyword")?.getString("keyword", "")?.let { str ->
                    searchRVAdapter.keyword = str
                }

                //先清空然后赋值 更体现搜索的意义
                searchRVAdapter.submitList(emptyList()) {
                    searchRVAdapter.submitList(filteredPagingData)
                }

            } else {
                // 本地点赞/缓存更新 → 局部刷新
                searchRVAdapter.submitList(filteredPagingData)
            }
        }
    }

    private fun initDefaultView() {
        // 默认视图初始化逻辑
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // 清理适配器
        mRecycleView.adapter = null
        mRecycleView.layoutManager = null
        homeRvAdapter.setOnItemClickListener(null)
        searchRVAdapter.setOnItemClickListener(null)
        // 显式移除 LoadStateListener
        homeLoadStateListener?.let { homeRvAdapter.removeLoadStateListener(it) }
        homeLoadStateListener = null

        /*
        理论上：viewLifecycleOwner 已经能保证在 onDestroyView() 停止回调，不会触发 UI 更新。
        实践中：手动移除观察者主要是为了：防止 Lambda 捕获的对象被 Adapter 或其他组件持有更明确释放资源，避免复杂场景下潜在泄漏
         */
        searchViewModel.items.removeObservers(viewLifecycleOwner)


    }
}


