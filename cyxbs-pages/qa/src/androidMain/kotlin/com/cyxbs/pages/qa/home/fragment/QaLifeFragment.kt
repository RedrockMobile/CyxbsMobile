package com.cyxbs.pages.qa.home.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.filter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.visible
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
    private val searchViewModel: SearchViewModel by activityViewModels<SearchViewModel>()
    private val homeViewModel: HomeViewModel by activityViewModels<HomeViewModel>()
    private val mRecycleView by R.id.qa_life_rv.view<RecyclerView>()
    private val mIvNoContent by R.id.qa_search_iv_no_content.view<ImageView>()
    private val mTvNoContent by R.id.qa_search_tv_no_content.view<TextView>()
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

    private fun initSearchView() {
        mRecycleView.adapter = searchRVAdapter
        mRecycleView.layoutManager = LinearLayoutManager(context)

        searchViewModel.items.observe(viewLifecycleOwner) { qaData ->
            val filteredList =
                qaData?.filter { it.status == 2 && it.tags.contains("生活") } ?: emptyList()

            val isFullRefresh = searchViewModel.isFullRefresh.value ?: true
            if (isFullRefresh) {
                context?.getSp("search_keyword")?.getString("keyword", "")?.let { str ->
                    searchRVAdapter.keyword = str
                }
                searchRVAdapter.submitList(emptyList()) {
                    if (filteredList.isEmpty()){
                        showNoContent()
                    } else {
                        hideNoContent()
                        searchRVAdapter.submitList(filteredList)
                    }
                }

            } else {
                // 本地点赞/缓存更新 → 局部刷新
                searchRVAdapter.submitList(filteredList)
            }
        }

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
    }

    fun showNoContent(){
        mIvNoContent.visible()
        mTvNoContent.visible()
        mRecycleView.gone()
    }

    fun hideNoContent(){
        mIvNoContent.gone()
        mTvNoContent.gone()
        mRecycleView.visible()
    }

}
