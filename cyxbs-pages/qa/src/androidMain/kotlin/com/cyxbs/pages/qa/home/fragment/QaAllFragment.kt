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
            setOnItemClickListener {
                DetailActivity.startActivity(requireContext(),it)
            }
        }
    }
    private val searchRVAdapter: QaSearchRVAdapter by lazy {
        QaSearchRVAdapter(searchViewModel).apply {
            setOnItemClickListener {
                DetailActivity.startActivity(requireContext(),it)
            }
        }
    }

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


        viewLifecycleScope.launch {
            homeViewModel.pagingDataFlow.collectLatest { pagingData ->
                val filteredPagingData = pagingData.filter { it.status == 2 }
                homeRvAdapter.submitData(filteredPagingData)

            }
        }
        homeRvAdapter.addLoadStateListener(object : (CombinedLoadStates) -> Unit {
            override fun invoke(loadStates: CombinedLoadStates) {
                val refreshState = loadStates.refresh
                if (refreshState is LoadState.NotLoading && homeRvAdapter.itemCount > 0) {
                    val currentList = homeRvAdapter.snapshot().items
                    val stats = newMessageAnalyzer.analyze(currentList)
                    // 更新 Tab 角标
                    (activity as? HomeActivity)?.apply {
                        //updateTabDot(0, stats.updatedCount)
                        updateTabDot(1, stats.newStudentCount)
                        updateTabDot(2, stats.lifeCount)
                        updateTabDot(3, stats.learningCount)
                        updateTabDot(4, stats.otherCount)
                    }
                    //  移除监听器，防止后续分页重复触发
                    homeRvAdapter.removeLoadStateListener(this)
                }
            }
        })

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

        // 移除 LiveData 观察者
        searchViewModel.QaDataLiveData.removeObservers(viewLifecycleOwner)

    }

    private fun initSearchUi() {
        searchViewModel.QaDataLiveData.observe(viewLifecycleOwner) { qaData ->

            val filteredList = qaData?.items?.filter { it.status == 2 } ?: emptyList()

            // 使用 safe call 来避免空指针
            context?.getSp("search_keyword")?.let { sp ->
                val str = sp.getString("keyword", "默认值")
                searchRVAdapter.keyword = str ?: "默认值"  // 确保 keyword 不为 null

            }


            // 刷新列表并滚动到顶部
            searchRVAdapter.submitList(filteredList)
        }
    }
}

