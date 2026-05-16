package com.cyxbs.pages.map.ui.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.cyxbs.pages.map.R
import com.cyxbs.pages.map.ui.fragment.inner.MapViewFragment
import com.cyxbs.pages.map.ui.fragment.inner.SearchFragment
import com.cyxbs.pages.map.util.KeyboardController
import com.cyxbs.pages.map.viewmodel.MapViewModel
import com.cyxbs.pages.map.component.SearchEditText
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.utils.extensions.setOnSingleClickListener


//该MainFragment使用FragmentTransaction管理两个Fragment

class MainFragment : BaseFragment() {
    private lateinit var viewModel: MapViewModel

    private val manager: FragmentManager?
        get() = childFragmentManager
    private var mapViewFragment = MapViewFragment()
    private var searchFragment = SearchFragment()

    private val mIvBack by R.id.map_iv_back.view<ImageView>()
    private val mEtSearch by R.id.map_et_search.view<SearchEditText>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.map_fragment_main, container, false)
    }


    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(MapViewModel::class.java)
        initMapViewFragment()
        mIvBack.setOnSingleClickListener {
            if (manager?.backStackEntryCount ?: 0 == 0) {
                //此处填写退出MapActivity的逻辑
                activity?.finish()
            } else {
                closeSearchFragment()
            }
        }
        //当搜索框被点击，打开搜索Fragment
        mEtSearch.setOnSingleClickListener {
            viewModel.unCheck.value = true
            openSearchFragment()
        }
        //上面这个事件在editText没有焦点时收不到事件，于是加一个
        mEtSearch.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                viewModel.unCheck.value = true
                openSearchFragment()
            }
        }

        viewModel.closeSearchFragment.observe(viewLifecycleOwner, Observer {
            if (it) {
                closeSearchFragment()
            }
        })


        viewModel.mapViewIsInAnimation.observe(viewLifecycleOwner, Observer { isAnimation ->
            if (isAnimation) {
                mEtSearch.animate().alpha(0f).duration = 500
                mEtSearch.isEnabled = false
                mIvBack.animate().alpha(0f).duration = 500
                mIvBack.isEnabled = false
            } else {
                mEtSearch.animate().alpha(1f).duration = 500
                mEtSearch.isEnabled = true
                mIvBack.animate().alpha(1f).duration = 500
                mIvBack.isEnabled = true
            }
        })

        viewModel.mapInfo.observe(viewLifecycleOwner, Observer { t ->
            mEtSearch.hintString = getString(R.string.map_search_hint) + if (t.hotWord == "") getString(R.string.map_search_hint_default_place_name) else t.hotWord
        })

        /**
         * 搜索框的内容发生改变，直接把内容传给viewModel
         */
        mEtSearch.doOnTextChanged { text, start, count, after ->
            viewModel.searchText.value = text.toString()
        }

        viewModel.searchHistoryString.observe(viewLifecycleOwner, Observer {
            mEtSearch.setText(it)
        })

    }

    private fun initMapViewFragment() {
        val transaction = manager?.beginTransaction()
        if (!mapViewFragment.isAdded) {
            transaction?.add(R.id.map_ll_map_fragment, mapViewFragment)?.commit()
        } else {
            transaction?.show(mapViewFragment)?.commit()
        }
    }

    fun closeSearchFragment() {
        viewModel.isClickSymbol.value = false
        mEtSearch.setText("")
        KeyboardController.hideInputKeyboard(mEtSearch)
        val transaction = manager?.beginTransaction()?.setCustomAnimations(R.animator.map_slide_from_left, R.animator.map_slide_to_right, R.animator.map_slide_from_right, R.animator.map_slide_to_left)
        transaction?.hide(searchFragment)
        transaction?.show(mapViewFragment)?.commit()
        manager?.popBackStack()
        viewModel.searchShowState.value = false
    }


    fun openSearchFragment() {
        viewModel.isClickSymbol.value = true
        viewModel.closeSearchFragment.value = false
        if (manager?.backStackEntryCount ?: 0 != 0) {
            //确保不重复打开搜索框
            return
        }
        val transaction = manager?.beginTransaction()?.setCustomAnimations(
                R.animator.map_slide_from_right,
                R.animator.map_slide_to_left,
                R.animator.map_slide_from_left,
                R.animator.map_slide_to_right)
        transaction?.hide(mapViewFragment)
        if (!searchFragment.isAdded) {
            transaction?.add(R.id.map_ll_map_fragment, searchFragment)
        }
        transaction?.show(searchFragment)
        transaction?.addToBackStack("search")?.commit()
        viewModel.searchShowState.value = true
    }


}