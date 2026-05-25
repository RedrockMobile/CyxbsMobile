package com.cyxbs.pages.discover.pages.discover.adapter

import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.webView.WebViewActivity
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.navigation.AppScheme
import com.cyxbs.components.utils.extensions.dp2pxF
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.ClickEvent
import com.cyxbs.pages.discover.R
import com.cyxbs.pages.discover.network.RollerViewInfo
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Banner adapter
 *
 * @author 985892345
 * @date 2024/11/10
 */
class RollerViewInfoAdapter(
  val list: List<RollerViewInfo>
) : RecyclerView.Adapter<RollerViewInfoAdapter.VHolder>() {
  inner class VHolder(val iv: ShapeableImageView) : RecyclerView.ViewHolder(iv) {
    init {
      iv.setOnSingleClickListener {
        if (IAccountService::class.impl().isLogin()) {
          // banner位的点击埋点
          TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_BANNER_ENTRY)
        }

        val data = list[realPosition]
        var uri = data.pictureGotoUrl.toUri()
        if (data.pictureGotoUrl.startsWith("http")) {
          if (uri.getQueryParameters(WebViewActivity.ARG_DEFAULT_TITLE) == null) {
            // 兼容老逻辑，keyword 作为页面的兜底标题
            uri = uri.buildUpon().appendQueryParameter(WebViewActivity.ARG_DEFAULT_TITLE, data.keyword).build()
          }
        }
        AppScheme.jump(data.pictureGotoUrl)
      }
    }

    // 因为 banner 是循环的，所以这里要取余才行
    val realPosition: Int
      get() = layoutPosition % itemCount
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): VHolder {
    return VHolder(
      ShapeableImageView(parent.context).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        val radius = 8.dp2pxF
        shapeAppearanceModel = ShapeAppearanceModel.builder()
          .setTopLeftCornerSize(radius)
          .setTopRightCornerSize(radius)
          .setBottomLeftCornerSize(radius)
          .setBottomRightCornerSize(radius)
          .build()
        scaleType = ImageView.ScaleType.CENTER_CROP
      }
    )
  }

  override fun onBindViewHolder(
    holder: VHolder,
    position: Int // 这个 position 不对哈，因为循环，所以要使用 holder.realPosition
  ) {
    val data = list[holder.realPosition]
    Glide.with(holder.iv)
      .load(data.pictureUrl)
      .placeholder(R.drawable.discover_ic_cyxbsv6)
      .error(R.drawable.discover_ic_cyxbsv6)
      .into(holder.iv)
  }

  override fun getItemCount(): Int = list.size
}