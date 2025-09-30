package com.cyxbs.pages.map.ui.fragment.inner

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.operations.doIfLogin
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.pages.map.R
import com.cyxbs.pages.map.bean.FavoritePlace
import com.cyxbs.pages.map.component.BannerIndicator
import com.cyxbs.pages.map.component.BannerView
import com.cyxbs.pages.map.ui.adapter.BannerViewAdapter
import com.cyxbs.pages.map.ui.adapter.DetailAttributeRvAdapter
import com.cyxbs.pages.map.ui.adapter.DetailTagRvAdapter
import com.cyxbs.pages.map.util.BannerPageTransformer
import com.cyxbs.pages.map.viewmodel.MapViewModel
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.net.URLEncoder


class PlaceDetailBottomSheetFragment : BaseFragment() {
    private lateinit var viewModel: MapViewModel
    private var isFavoritePlace = false

    private val mBannerDetailImage by R.id.map_banner_detail_image.view<BannerView>()
    private val mRvDetailAboutList by R.id.map_rv_detail_about_list.view<RecyclerView>()
    private val mRvDetailPlaceAttribute by R.id.map_rv_detail_place_attribute.view<RecyclerView>()
    private val mIndicatorDetail by R.id.map_indicator_detail.view<BannerIndicator>()
    private val mIvDetailFavorite by R.id.map_iv_detail_favorite.view<ImageView>()
    private val mTvDetailPlaceNickname by R.id.map_tv_detail_place_nickname.view<TextView>()
    private val mTvDetailMore by R.id.map_tv_detail_more.view<TextView>()
    private val mTvDetailShare by R.id.map_tv_detail_share.view<TextView>()
    private val mapTvDetailAboutText by R.id.map_tv_detail_about_text.view<TextView>()
    private val mapTvDetailPlaceName by R.id.map_tv_detail_place_name.view<TextView>()
    private val mapBtnDetailPlaceNavigation by R.id.map_btn_detail_place_navigation.view<AppCompatButton>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.map_fragment_place_detail_container, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(MapViewModel::class.java)


        /**
         * 初始化adapter和layoutManager
         */

        val bannerViewAdapter = context?.let { BannerViewAdapter(it, mutableListOf()) }
        mBannerDetailImage.adapter = bannerViewAdapter
        mBannerDetailImage.offscreenPageLimit = 3
        mBannerDetailImage.pageMargin = 15
        mBannerDetailImage.setPageTransformer(true, BannerPageTransformer())
        mBannerDetailImage.start()


        val flexBoxManager = FlexboxLayoutManager(context)
        flexBoxManager.flexWrap = FlexWrap.WRAP
        mRvDetailAboutList.layoutManager = flexBoxManager
        val tagAdapter = DetailTagRvAdapter()
        mRvDetailAboutList.adapter = tagAdapter

        val linearLayoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        mRvDetailPlaceAttribute.layoutManager = linearLayoutManager
        val attributeAdapter = context?.let { DetailAttributeRvAdapter(it, mutableListOf()) }
        mRvDetailPlaceAttribute.adapter = attributeAdapter


        /**
         * 对要显示的内容监听
         */
        viewModel.placeDetails.observe(
                viewLifecycleOwner,
                Observer { t ->
                    mapTvDetailPlaceName.text = t.placeName
                    //数据传给adapter
                    if (bannerViewAdapter != null) {
                        if (t.images != null) {
                            bannerViewAdapter.setList(t.images!!)
                            bannerViewAdapter.notifyDataSetChanged()
                            mBannerDetailImage.adapter = bannerViewAdapter
                            mIndicatorDetail.setupWithViewPager(mBannerDetailImage)
                        } else {
                            bannerViewAdapter.setList(listOf())
                            bannerViewAdapter.notifyDataSetChanged()
                            mBannerDetailImage.adapter = bannerViewAdapter
                            mIndicatorDetail.setupWithViewPager(mBannerDetailImage)
                        }
                    }
                    if (t.tags != null) {
                        mapTvDetailAboutText.visible()
                        mRvDetailAboutList.visible()
                        tagAdapter.submitList(t.tags ?: emptyList())
                    } else {
                        mapTvDetailAboutText.gone()
                        mRvDetailAboutList.gone()
                    }
                    
                    if (attributeAdapter != null) {
                        if (t.placeAttribute != null) {
                            attributeAdapter.setList(t.placeAttribute!!)
                            attributeAdapter.notifyDataSetChanged()
                        } else {
                            attributeAdapter.setList(listOf())
                            attributeAdapter.notifyDataSetChanged()
                        }

                    }
                    setNickName()

                }
        )
        viewModel.collectList.observe(viewLifecycleOwner, Observer {
            setNickName()
        })
        /**
         * 添加点击事件
         */
        val onClickListener = View.OnClickListener {
            doIfLogin("收藏") {
                //不再打开收藏编辑页面，故注释
                //viewModel.fragmentFavoriteEditIsShowing.value = true
                if (isFavoritePlace) {
                    viewModel.deleteCollect(viewModel.showingPlaceId)
                    mIvDetailFavorite.setImageResource(R.drawable.map_ic_no_like)
                } else {
                    viewModel.addCollect(viewModel.placeDetails.value?.placeName
                            ?: "我的收藏", viewModel.showingPlaceId)
                    mIvDetailFavorite.setImageResource(R.drawable.map_ic_like)
                }
                viewModel.bottomSheetStatus.postValue(BottomSheetBehavior.STATE_COLLAPSED)
            }
        }
        mIvDetailFavorite.setOnClickListener(onClickListener)
        mTvDetailPlaceNickname.setOnClickListener(onClickListener)
        mTvDetailMore.setOnClickListener {
            viewModel.fragmentAllPictureIsShowing.value = true
        }

        mTvDetailShare.setOnClickListener {
            context?.let { it1 -> viewModel.sharePicture(it1, this) }

        }
        mapBtnDetailPlaceNavigation.setOnClickListener {
            val endPlace = "重庆邮电大学" + mapTvDetailPlaceName.text.toString().removePrefix("name:")
            jumpToNavigation(endPlace)
        }
    }
    private fun jumpToNavigation(endPlace: String) {
        try {

            val uri = ("baidumap://map/direction?" +
                    "destination=name:$endPlace" +
                    "&mode=walking").toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }catch (e: Exception){
            // 未安装百度地图App，跳转网页版
            // 对终点进行URL编码，处理特殊字符
            val encodedEndPlace = URLEncoder.encode(endPlace, "UTF-8")
            val webUrl = "http://api.map.baidu.com/geocoder?address=$encodedEndPlace&output=html&src=webapp.baidu.openAPIdemo"
            val browserIntent = Intent(Intent.ACTION_VIEW, webUrl.toUri())
            startActivity(browserIntent)
        }
    }


    private fun setNickName() {
        //判断是否收藏过该地点，如果收藏了则显示出收藏的nickname
        viewModel.collectList.value?.let {
            var isFavor: String? = null
            for (favoritePlace: FavoritePlace in it) {
                if (viewModel.showingPlaceId == favoritePlace.placeId) {
                    isFavor = favoritePlace.placeNickname
                }
            }
            if (isFavor != null) {
                mIvDetailFavorite.setImageResource(R.drawable.map_ic_like)
                mTvDetailPlaceNickname.visible()
                isFavoritePlace = true
                //不显示地点备注名了，故注释掉
                //map_tv_detail_place_nickname.text = isFavor
            } else {
                mIvDetailFavorite.setImageResource(R.drawable.map_ic_no_like)
                mTvDetailPlaceNickname.gone()
                isFavoritePlace = false
            }
        }
    }



}