package com.cyxbs.pages.qa.home.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
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
import com.cyxbs.pages.qa.home.viewmodel.HomeViewModel
import com.cyxbs.pages.qa.home.viewmodel.SearchViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * description ： Qa生活类问题展示
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/13 20:32
 */
class QaLifeFragment : BaseFragment() {
    private val searchViewModel: SearchViewModel by lazy { ViewModelProvider(requireActivity())[SearchViewModel::class.java] }
    private val homeViewModel: HomeViewModel by lazy { ViewModelProvider(requireActivity())[HomeViewModel::class.java] }
    private val mRecycleView by R.id.qa_life_rv.view<RecyclerView>()
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.qa_fragment_life, container, false)
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
        val animator = mRecycleView.itemAnimator
        if (animator is androidx.recyclerview.widget.SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }

        initHomeUi()

    }

    private fun initSearchView() {
        mRecycleView.adapter = searchRVAdapter
        mRecycleView.layoutManager = LinearLayoutManager(context)

        initSearchUi()

    }

    private fun initDefaultView() {

    }

    override fun onDestroyView() {
        super.onDestroyView()

        // 清理适配器
        mRecycleView.adapter = null
        mRecycleView.layoutManager = null
        homeRvAdapter.setOnItemClickListener(null)
        searchRVAdapter.setOnItemClickListener(null)

        // 移除 LiveData 观察者
        searchViewModel.QaDataLiveData.removeObservers(viewLifecycleOwner)
    }

    private fun initHomeUi() {
        viewLifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.pagingDataFlow.collectLatest { pagingData ->
                    val filteredPagingData =
                        pagingData.filter { it.status == 2 && it.tags.contains("生活") }
                    homeRvAdapter.submitData(filteredPagingData)
                }

            }
        }
    }

    private fun initSearchUi() {
        searchViewModel.QaDataLiveData.observe(viewLifecycleOwner) { qaData ->
            val filteredList =
                qaData?.items?.filter { it.status == 2 && it.tags.contains("生活") } ?: emptyList()

            val isFullRefresh = searchViewModel.isFullRefresh.value ?: true
            if (isFullRefresh) {
                context?.getSp("search_keyword")?.getString("keyword", "")?.let { str ->
                    searchRVAdapter.keyword = str
                }
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
