package com.cyxbs.pages.qa.home.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.filter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.pages.qa.R
import com.cyxbs.pages.qa.home.adapter.QaHomeRVAdapter
import com.cyxbs.pages.qa.home.adapter.QaSearchRVAdapter
import com.cyxbs.pages.qa.home.HomeActivity
import com.cyxbs.pages.qa.home.SearchActivity
import com.cyxbs.pages.qa.home.interfaces.Refreshable
import com.cyxbs.pages.qa.home.model.bean.Item
import com.cyxbs.pages.qa.home.model.bean.NewMessageAnalyzer
import com.cyxbs.pages.qa.home.model.bean.NewMessageCount
import com.cyxbs.pages.qa.home.viewmodel.HomeViewModel
import com.cyxbs.pages.qa.home.viewmodel.SearchViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * description ： Qa全部问题展示
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/13 20:30
 */
class QaAllFragment : BaseFragment(), Refreshable {
    private val newMessageAnalyzer: NewMessageAnalyzer by lazy { NewMessageAnalyzer(requireContext()) }
    private val homeViewModel: HomeViewModel by lazy { ViewModelProvider(requireActivity())[HomeViewModel::class.java] }
    private val searchViewModel: SearchViewModel by lazy { ViewModelProvider(requireActivity())[SearchViewModel::class.java] }
    private val mRecycleView by R.id.qa_all_rv.view<RecyclerView>()
    private val homeRvAdapter: QaHomeRVAdapter by lazy {
        QaHomeRVAdapter { item, callback ->
            if (item.is_like) {
                homeViewModel.getLike(item.ID) { success -> callback(success) }
            } else {
                homeViewModel.getUnlike(item.ID) { success -> callback(success) }
            }
        }
    }
    private val searchRVAdapter: QaSearchRVAdapter by lazy {
        QaSearchRVAdapter { item, callback ->
            if (item.is_like) {
                homeViewModel.getLike(item.ID) { success -> callback(success) }
            } else {
                homeViewModel.getUnlike(item.ID) { success -> callback(success) }
            }
        }
    }
    private var refreshJob: Job? = null

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

        refreshJob?.cancel()
        refreshJob = viewLifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.getQaData().collect { pagingDatas ->
                    val filteredPagingData = pagingDatas.filter { it.status == 2 }
                    homeRvAdapter.submitData(filteredPagingData)
                    // 从适配器中获取当前已加载的 List<Item>
                    val currentList: List<Item> = homeRvAdapter.snapshot().items

                    // 使用 NewMessageAnalyzer 统计
                    val stats: NewMessageCount = newMessageAnalyzer.analyze(currentList)
                    //直接调用activity的方法
                    (activity as? HomeActivity)?.updateTabDot(0, stats.updatedCount)
                    (activity as? HomeActivity)?.updateTabDot(1, stats.newStudentCount)
                    (activity as? HomeActivity)?.updateTabDot(2, stats.learningCount)
                    (activity as? HomeActivity)?.updateTabDot(3, stats.lifeCount)
                    (activity as? HomeActivity)?.updateTabDot(4, stats.otherCount)

                }
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
                        updateTabDot(0, stats.updatedCount)
                        updateTabDot(1, stats.newStudentCount)
                        updateTabDot(2, stats.learningCount)
                        updateTabDot(3, stats.lifeCount)
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

        // 取消刷新协程
        refreshJob?.cancel()
        refreshJob = null

        // 清理适配器
        mRecycleView.adapter = null

        // 移除 LiveData 观察者
        searchViewModel.QaDataLiveData.removeObservers(viewLifecycleOwner)

    }

    //为了解决在第一个fragment点赞第二个frgament不能更新数据的问题每次滑动到下一个fragment时会重绘一次ui，使用差分刷新就不会出现闪一下的情况
    override fun refreshUI() {
        if (requireActivity() is HomeActivity) {
            viewLifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    homeViewModel.getQaData().collect { pagingDatas ->
                        val filteredPagingData = pagingDatas.filter { it.status == 2 }
                        homeRvAdapter.submitData(filteredPagingData)
                    }
                }
            }
        }
        if (requireActivity() is SearchActivity) {
            searchViewModel.QaDataLiveData.observe(viewLifecycleOwner) {
                initSearchUi()
            }

        }
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
            searchRVAdapter.submitList(filteredList) {
                mRecycleView.scrollToPosition(0)
            }
        }
    }
}

