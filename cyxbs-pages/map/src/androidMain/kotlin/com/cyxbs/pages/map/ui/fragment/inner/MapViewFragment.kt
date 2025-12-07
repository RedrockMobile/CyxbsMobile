package com.cyxbs.pages.map.ui.fragment.inner

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.cyxbs.pages.map.R
import com.cyxbs.pages.map.bean.IconBean
import com.cyxbs.pages.map.bean.PlaceItem
import com.cyxbs.pages.map.component.ClickView
import com.cyxbs.pages.map.component.MapLayout
import com.cyxbs.pages.map.model.DataSet
import com.cyxbs.pages.map.ui.activity.VRActivity
import com.cyxbs.pages.map.ui.adapter.FavoriteListAdapter
import com.cyxbs.pages.map.ui.adapter.SymbolRvAdapter
import com.cyxbs.pages.map.viewmodel.MapViewModel
import com.cyxbs.pages.map.widget.*
import com.cyxbs.components.base.operations.doIfLogin
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.utils.extensions.dp2px
import com.cyxbs.components.utils.extensions.invisible
import com.cyxbs.components.utils.extensions.launchByLifecycleScope
import com.cyxbs.components.utils.extensions.pressToZoomOut
import com.cyxbs.components.utils.extensions.visible


class MapViewFragment : BaseFragment() {
    private lateinit var viewModel: MapViewModel
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    // 两次点击按钮之间的点击间隔不能少于1000毫秒
    private val minClickDelayTime = 1000
    private var lastClickTime: Long = 0
    private val placeData = mutableListOf<PlaceItem>()

    private val mMapLayout by R.id.map_layout.view<MapLayout>()
    private val mIvLock by R.id.map_iv_lock.view<ClickView>()
    private val mBottomSheetContent by R.id.map_bottom_sheet_content.view<FrameLayout>()
    private val mRvSymbolPlaces by R.id.map_rv_symbol_places.view<RecyclerView>()
    private val mLlMapViewMyFavorite by R.id.map_ll_map_view_my_favorite.view<LinearLayout>()
    private val mIvVr by R.id.map_iv_vr.view<ClickView>()
    private val mRootMapView by R.id.map_root_map_view.view<FrameLayout>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.map_fragment_map_view, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(MapViewModel::class.java)
        /**
         * 初始化地图view
         */
        var openSiteId: String = ""
        viewModel.mapInfo.observe(viewLifecycleOwner, Observer { data ->
            placeData.clear()
            placeData.addAll(data.placeList)
            mMapLayout.setBackgroundColor(Color.parseColor(data.mapBackgroundColor))
            val list = data.placeList
            val iconList = mutableListOf<IconBean>()
            /**
             * 添加所有展示的icon
             */
            list.forEach { bean ->
                val buildingList = bean.buildingList
                buildingList.forEach { building ->
                    iconList.add(IconBean(bean.placeId.toInt(),
                            bean.placeCenterX.toFloat(),
                            bean.placeCenterY.toFloat(),
                            building.buildingLeft.toFloat(),
                            building.buildingRight.toFloat(),
                            building.buildingTop.toFloat(),
                            building.buildingBottom.toFloat(),
                            bean.tagLeft.toFloat(),
                            bean.tagRight.toFloat(),
                            bean.tagTop.toFloat(),
                            bean.tagBottom.toFloat()
                    ))
                }
            }
            mMapLayout.clearIcons()
            mMapLayout.addSomeIcons(iconList)
            openSiteId = data.openSiteId.toString()
            /**
             * 根据时间戳判断是否清除缓存重新加载
             */
            val version = DataSet.getPictureVersion()
            val fileExist = DataSet.mapImageFile.exists()
            if (fileExist) {
                if (data.pictureVersion == version) {
                    mMapLayout.loadCache()
                } else {
                    // 如果版本更新，并且地图存在，则弹出更新弹窗
                    UpdateMapDialog.show(requireContext(), requireContext().getString(R.string.map_update_title),
                        requireContext().getString(R.string.map_update_message),
                        object : OnUpdateSelectListener {
                            override fun onDeny() {
                                mMapLayout.loadCache()
                            }

                            override fun onPositive() {
                                DataSet.savePictureVersion(data.pictureVersion)
                                launchByLifecycleScope {
                                    mMapLayout.loadNewMap(data.mapUrl)
                                }
                            }
                        })
                }
            } else {
                // 地图不存在直接下载地图
                DataSet.savePictureVersion(data.pictureVersion)
                launchByLifecycleScope {
                    mMapLayout.loadNewMap(data.mapUrl)
                }
            }
        })
        /**
         * 监听进入地图要聚焦的地点
         */
        viewModel.openId.observe(viewLifecycleOwner, Observer {
            when (it) {
                MapViewModel.PLACE_SEARCH_500 -> {
                    toast(getString(R.string.map_open_site_id_null))
                    mMapLayout.setOpenSiteId(openSiteId)
                    mMapLayout.showIconWithoutAnim(openSiteId)
                }
                MapViewModel.PLACE_SEARCH_NULL -> {
                    mMapLayout.setOpenSiteId(openSiteId)
                    mMapLayout.showIconWithoutAnim(openSiteId)
                }
                else -> {
                    viewModel.openId.value?.let { it1 ->
                        mMapLayout.setOpenSiteId(it1)
                        mMapLayout.showIconWithoutAnim(it1)
                    }
                }
            }
        })
        /**
         * 加载失败时使用本地地图缓存
         */
        viewModel.loadFail.observe(viewLifecycleOwner, Observer {
            GlideProgressDialog.hide()
            if (mMapLayout.loadCache()) {
                toast(getString(R.string.map_use_local_map_data))
            } else {
                MapDialogTips.show(requireContext(), getString(R.string.map_map_load_failed_title_tip)
                    , getString(R.string.map_map_load_failed_message_tip)
                    , false, object : OnSelectListenerTips {
                        override fun onPositive() {
                            activity?.finish()
                        }
                    })
            }
        })

        /**
         * 设置地点大头针（水滴）点击事件
         */
        mMapLayout.setMyOnIconClickListener(object : MapLayout.OnIconClickListener {
            override fun onIconClick(v: View) {
                val bean = v.tag as IconBean
                mMapLayout.focusToPoint(bean.sx, bean.sy)
                viewModel.getPlaceDetails(bean.id.toString(), true)
                viewModel.unCheck.value = true
            }

        })
        /**
         * 监听点击到建筑区域的点击事件
         */
        mMapLayout.setMyOnPlaceClickListener(object : MapLayout.OnPlaceClickListener {
            override fun onPlaceClick(v: View) {
                val bean = v.tag as IconBean
                viewModel.getPlaceDetails(bean.id.toString(), false)
                viewModel.unCheck.value = true
            }

        })

        /**
         * 监听显示某一个地点
         */
        viewModel.showIconById.observe(viewLifecycleOwner, Observer {
            mMapLayout.closeAllIcon()
            mMapLayout.setOnCloseFinishListener(object : MapLayout.OnCloseFinishListener {
                override fun onCloseFinish() {
                    mMapLayout.showSomeIcons(listOf(it))
                    mMapLayout.focusToPoint(it)
                }
            })
        })

        /**
         * 监听点击到非建筑区域的点击事件
         */
        mMapLayout.setMyOnNoPlaceClickListener(object : MapLayout.OnNoPlaceClickListener {
            override fun onNoPlaceClick() {
                //通知隐藏底部栏
                if (viewModel.bottomSheetStatus.value == BottomSheetBehavior.STATE_EXPANDED) {
                    viewModel.bottomSheetStatus.value = BottomSheetBehavior.STATE_COLLAPSED
                }
                viewModel.unCheck.value = true
            }
        })

        /**
         * 当点击搜索，标签或收藏时自动解除锁定
         */
        viewModel.isClickSymbol.observe(viewLifecycleOwner, Observer {
            if (it && viewModel.isLock.value == true) {
                val animator = ValueAnimator.ofFloat(1f, 0.8f, 1.2f, 1f)
                animator.duration = 500
                animator.addUpdateListener { t ->
                    val currentValue: Float = t.animatedValue as Float
                    mIvLock.scaleX = currentValue
                    mIvLock.scaleY = currentValue
                }
                animator.start()
                mIvLock.setImageResource(R.drawable.map_ic_unlock)
                toast(getString(R.string.map_unlock))
                viewModel.isLock.value = false
                mMapLayout.setIsLock(false)
            }
        })

        /**
         * 监听锁定按钮
         */
        mIvLock.setOnClickListener {
            if (viewModel.isLock.value!!) {
                if (viewModel.isLock.value!!) {
                    val animator = ValueAnimator.ofFloat(1f, 0.8f, 1.2f, 1f)
                    animator.duration = 500
                    animator.addUpdateListener {
                        val currentValue: Float = it.animatedValue as Float
                        mIvLock.scaleX = currentValue
                        mIvLock.scaleY = currentValue
                    }
                    animator.start()
                    mIvLock.setImageResource(R.drawable.map_ic_unlock)
                    toast(getString(R.string.map_unlock))
                    viewModel.isLock.value = false
                    mMapLayout.setIsLock(false)
                }
            } else {
                val animator = ValueAnimator.ofFloat(1f, 1.2f, 0.8f, 1f)
                animator.duration = 500
                animator.addUpdateListener {
                    val currentValue: Float = it.animatedValue as Float
                    mIvLock.scaleX = currentValue
                    mIvLock.scaleY = currentValue
                }
                animator.start()
                mIvLock.setImageResource(R.drawable.map_ic_lock)
                toast(getString(R.string.map_lock))
                viewModel.isLock.value = true
                mMapLayout.setIsLock(true)
            }

        }

        /**
         * 初始化bottomSheet
         */
        bottomSheetBehavior = BottomSheetBehavior.from(mBottomSheetContent)
        mBottomSheetContent.invisible()
        childFragmentManager.beginTransaction().apply {
            add(R.id.map_bottom_sheet_content, PlaceDetailBottomSheetFragment())
            commit()
        }
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> viewModel.bottomSheetStatus.value = newState
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }
        }
        )

        /**
         * 初始化标签adapter（搜索框下方按钮）
         */
        val linearLayoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        mRvSymbolPlaces.layoutManager = linearLayoutManager
        val symbolRvAdapter = context?.let { SymbolRvAdapter(it, viewModel, mutableListOf(), viewLifecycleOwner) }
        mRvSymbolPlaces.adapter = symbolRvAdapter

        /**
         * 初始化我的收藏列表adapter
         * （弹窗）
         */

        val popView = View.inflate(context, R.layout.map_pop_window_favorite_list, null)
        val popupWindow = PopupWindow(popView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        popupWindow.isOutsideTouchable = true

        val mapFavoriteRecyclerView = popView.findViewById<RecyclerView>(R.id.map_rv_favorite_list)
        mapFavoriteRecyclerView.layoutManager = LinearLayoutManager(context)
        val favoriteListAdapter = context?.let { FavoriteListAdapter(it, viewModel, mutableListOf()) }
        mapFavoriteRecyclerView.adapter = favoriteListAdapter
        //设置“我的收藏”点击事件
        mLlMapViewMyFavorite.pressToZoomOut()
        mLlMapViewMyFavorite.setOnClickListener {
            doIfLogin("收藏") {
                if (isFastClick()) {
                    viewModel.unCheck.value = true
                    viewModel.showPopUpWindow.value = true
                    viewModel.isClickSymbol.value = true
                    if (viewModel.bottomSheetStatus.value == BottomSheetBehavior.STATE_EXPANDED) {
                        viewModel.bottomSheetStatus.postValue(BottomSheetBehavior.STATE_COLLAPSED)
                    }
                    viewModel.refreshCollectList(true)
                    if (!popupWindow.isShowing) {
                        popupWindow.showAsDropDown(mLlMapViewMyFavorite, mLlMapViewMyFavorite.width - 140f.dp2px, 15f.dp2px)
                        popupWindow.update()
                    }
                }
            }
        }
        /**
         * 监听是否关闭收藏弹窗
         */
        viewModel.showPopUpWindow.observe(viewLifecycleOwner, Observer {
            if (!it) {
                viewModel.isClickSymbol.value = false
                popupWindow.dismiss()
                viewModel.showPopUpWindow.value = true
            }
        })

        /**
         * 注册监听
         */
        viewModel.buttonInfo.observe(
                viewLifecycleOwner,
                Observer {
                    if (it.buttonInfo != null) {
                        symbolRvAdapter?.setList(it.buttonInfo!!)
                    }

                }
        )


        viewModel.bottomSheetStatus.observe(
                viewLifecycleOwner,
                Observer {
                    var i = it
                    if (viewModel.placeDetails.value?.placeName == "") {
                        i = BottomSheetBehavior.STATE_HIDDEN
                    }
                    when (i) {
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            //半隐藏底部栏
                            mBottomSheetContent.visible()
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        }

                        BottomSheetBehavior.STATE_EXPANDED -> {
                            //展开底部栏
                            mBottomSheetContent.visible()
                            //下面这两句，因为低版本依赖的bottomSheetBehavior，当内部view高度发生变化时，不会及时修正高度，故手动测量
                            //换成高版本依赖可以删除
                            mBottomSheetContent.requestLayout()
                            mBottomSheetContent.invalidate()
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                        }

                        BottomSheetBehavior.STATE_HIDDEN -> {
                            mBottomSheetContent.invisible()
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                        }

                    }
                }
        )
        viewModel.collectList.observe(
                viewLifecycleOwner,
                Observer {
                    //更新favoriteList数据
                    favoriteListAdapter?.setList(it)
                }
        )

        viewModel.showSomePlaceIconById.observe(viewLifecycleOwner, Observer {
            mMapLayout.closeAllIcon()
            mMapLayout.setOnCloseFinishListener(object : MapLayout.OnCloseFinishListener {
                override fun onCloseFinish() {
                    /**
                     * 关闭动画结束
                     */
                    mMapLayout?.showSomeIcons(it)
                }
            })

        })

        mMapLayout.setOnShowFinishListener(object : MapLayout.OnShowFinishListener {
            override fun onShowFinish() {
                /**
                 * 展示动画结束
                 */
            }
        })

        /**
         * VR按钮点击事件
         */
        mIvVr.setOnClickListener {
            val xc: Int = (mRootMapView.left + mRootMapView.right) / 2
            val yc: Int = (mRootMapView.top + mRootMapView.bottom) / 2
            val animator = ViewAnimationUtils.createCircularReveal(mRootMapView, xc, yc, mRootMapView.height.toFloat() + 100f, 0f)
            animator.interpolator = DecelerateInterpolator()
            animator.duration = 1000
            animator.addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(p0: Animator) {

                }

                override fun onAnimationEnd(p0: Animator) {
                    activity?.let { it1 -> VRActivity.startVRActivity(it1, viewModel.vrUrl) }
                }

                override fun onAnimationCancel(p0: Animator) {

                }

                override fun onAnimationStart(p0: Animator) {
                    viewModel.mapViewIsInAnimation.value = true
                    mRootMapView.animate().alpha(0f).duration = 1000
                }

            })
            animator.start()


        }
    }

    override fun onResume() {
        mRootMapView.animate().alpha(1f).duration = 1000
        viewModel.mapViewIsInAnimation.value = false
        super.onResume()
    }

    private fun isFastClick(): Boolean {
        var flag = false
        val curClickTime = System.currentTimeMillis()
        if (curClickTime - lastClickTime >= minClickDelayTime) {
            flag = true
        }
        lastClickTime = curClickTime
        return flag
    }

    override fun onDestroyView() {
        super.onDestroyView()
        GlideProgressDialog.hide()
    }
}