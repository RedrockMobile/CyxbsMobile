package com.cyxbs.pages.news.ui.activity

import android.app.Activity
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.DISCOVER_NEWS
import com.cyxbs.components.view.ui.JToolbar
import com.cyxbs.pages.news.R
import com.cyxbs.pages.news.ui.adapter.NewsAdapter
import com.cyxbs.pages.news.viewmodel.NewsListViewModel
import com.g985892345.provider.api.annotation.KClassProvider

/**
 * @author zixuan
 * 2019/11/20
 */
@KClassProvider(clazz = Activity::class, name = DISCOVER_NEWS)
class NewsListActivity : BaseActivity() {

    private val viewModel by viewModels<NewsListViewModel>()

    private val srl_list by R.id.srl_list.view<SwipeRefreshLayout>()
    private val rv_list by R.id.rv_list.view<RecyclerView>()
    private val common_toolbar by com.cyxbs.components.view.R.id.toolbar.view<JToolbar>()

    private lateinit var adapter: NewsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.news_activity_list)

        common_toolbar.init(this, "教务新闻")

        viewModel.newsEvent.observe {
            srl_list.isRefreshing = false
            adapter.appendNewsList(it ?: return@observe)
        }

        srl_list.setOnRefreshListener {
            adapter.clear()
            viewModel.clearPages()
            viewModel.loadNewsData()
        }

        adapter = NewsAdapter(viewModel::loadNewsData)
        rv_list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rv_list.adapter = adapter
        rv_list.addItemDecoration(DividerItemDecoration(this,DividerItemDecoration.VERTICAL).apply { setDrawable(ContextCompat.getDrawable(baseContext,R.drawable.news_recycler_item_split)!!) })
    }

}
