package com.cyxbs.pages.qa.home.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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

        // 先赋值给变量
        homeLoadStateListener = { loadStates ->
            val refreshState = loadStates.refresh
            if (!isDotUpdated && refreshState is LoadState.NotLoading && homeRvAdapter.itemCount > 0) {
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

        initSearchUi()
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

        // 移除观察者
        searchViewModel.QaDataLiveData.removeObservers(viewLifecycleOwner)


    }

    private fun initSearchUi() {
        searchViewModel.QaDataLiveData.observe(viewLifecycleOwner) { qaData ->
            val filteredList = qaData?.items?.filter { it.status == 2 } ?: emptyList()

            val isFullRefresh = searchViewModel.isFullRefresh.value ?: true
            if (isFullRefresh) {
                context?.getSp("search_keyword")?.getString("keyword", "")?.let { str ->
                    searchRVAdapter.keyword = str
                }
                //先清空然后赋值 更体现搜索的意义
                searchRVAdapter.submitList(emptyList()) {
                    searchRVAdapter.submitList(filteredList)
                }

            } else {
                // 本地点赞/缓存更新 → 局部刷新
                searchRVAdapter.submitList(filteredList)
            }
        }

        }
    }


