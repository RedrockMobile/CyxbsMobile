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
 * description ： Qa其他问题展示
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2025/8/13 20:32
 */
class QaOtherFragment : BaseFragment() {
    private val searchViewModel: SearchViewModel by lazy { ViewModelProvider(requireActivity())[SearchViewModel::class.java] }
    private val homeViewModel: HomeViewModel by lazy { ViewModelProvider(requireActivity())[HomeViewModel::class.java] }
    private val mRecycleView by R.id.qa_other_rv.view<RecyclerView>()
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
        return inflater.inflate(R.layout.qa_fragment_other, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun initView() {
        mRecycleView.adapter = homeRvAdapter
        mRecycleView.layoutManager = LinearLayoutManager(context)
        when (requireActivity()) {
            is HomeActivity -> initHomeView()
            is SearchActivity -> initSearchView()
            else -> initDefaultView()
        }
    }

    private fun initHomeView() {
        mRecycleView.adapter = homeRvAdapter
        mRecycleView.layoutManager = LinearLayoutManager(context)

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

        // 移除 LiveData 观察者
        searchViewModel.QaDataLiveData.removeObservers(viewLifecycleOwner)
    }


    private fun initHomeUi() {
        viewLifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.pagingDataFlow.collectLatest { pagingData ->
                    val filteredPagingData =
                        pagingData.filter { it.status == 2 && it.tags.contains("其他") }
                    homeRvAdapter.submitData(filteredPagingData)
                }

            }
        }
    }

    private fun initSearchUi() {
        searchViewModel.QaDataLiveData.observe(viewLifecycleOwner) { qaData ->
            // 确保 items 不为 null
            val filteredList =
                qaData?.items?.filter { it.status == 2 && (it.tags.contains("其他") == true) }
                    ?: emptyList()
            // 刷新列表并滚动到顶部
            val sp = context?.getSp("search_keyword")
            val str = sp?.getString("keyword", "默认值")
            searchRVAdapter.keyword = str.toString()
            searchRVAdapter.submitList(filteredList)

        }
    }
}