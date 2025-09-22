package com.cyxbs.components.base.crash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isGone
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.base.R
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.utils.network.getBaseUrl

/**
 * 在崩溃后查看简单网络请求的 Activity
 *
 * 因为崩溃是进程崩溃，所以 Pandora 的数据会全丢失，只能自己手动记录一遍了
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/8/28 23:39
 */
class NetworkApiResultActivity : BaseActivity() {
  
  companion object {
    
    fun start(result: ArrayList<CrashActivity.Companion.NetworkApiResult>) {
      appContext.startActivity(
        Intent(appContext, NetworkApiResultActivity::class.java)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          .putExtra(NetworkApiResultActivity::mNetworkResult.name, result)
      )
    }
  }
  
  private val mNetworkResult by intent<ArrayList<CrashActivity.Companion.NetworkApiResult>>()
  
  private val mRecyclerView by R.id.debug_rv_api_result.view<RecyclerView>()
  private val mLayoutDetail by R.id.debug_cl_result_detail.view<ViewGroup>()
  
  private val mTvRequest by R.id.app_tv_result_request.view<TextView>()
  private val mTvResponse by R.id.debug_tv_result_response.view<TextView>()
  private val mTvError by R.id.debug_tv_result_error.view<TextView>()
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.base_activity_network_result)
    mRecyclerView.layoutManager = LinearLayoutManager(this)
    mRecyclerView.adapter = RvAdapter()
    
    mRecyclerView.visible()
    mLayoutDetail.gone()
    
    if (mNetworkResult.isEmpty()) {
      toastLong("数据为空！")
    }
  }
  
  override fun onBackPressed() {
    if (mRecyclerView.isGone) {
      mRecyclerView.visible()
      mLayoutDetail.gone()
    } else {
      super.onBackPressed()
    }
  }
  
  private inner class RvAdapter : RecyclerView.Adapter<RvAdapter.VH>() {
    
    abstract inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView)
    
    inner class ApiVH(itemView: View) : VH(itemView) {
      val tvUrl: TextView = itemView.findViewById(R.id.debug_tv_item_url)
      val tvError: TextView = itemView.findViewById(R.id.debug_tv_item_error)
      
      init {
        itemView.setOnClickListener {
          mRecyclerView.gone()
          mLayoutDetail.visible()
          val result = mNetworkResult[layoutPosition]
          mTvRequest.text = result.request
          mTvResponse.text = result.response
          mTvError.text = result.throwable?.stackTraceToString()
        }
      }
    }
  
    inner class LogVH(itemView: View) : VH(itemView) {
      val tvLog: TextView = itemView.findViewById(R.id.debug_tv_item_log)
    }
  
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
      return when (viewType) {
        ApiVH::class.hashCode() -> {
          ApiVH(
            LayoutInflater.from(parent.context)
              .inflate(R.layout.base_rv_item_api_result, parent, false)
          )
        }
        LogVH::class.hashCode() -> {
          LogVH(
            LayoutInflater.from(parent.context)
              .inflate(R.layout.base_rv_item_log_result, parent, false)
          )
        }
        else -> error("")
      }
    }
  
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: VH, position: Int) {
      when (getItemViewType(position)) {
        ApiVH::class.hashCode() -> {
          holder as ApiVH
          val result = mNetworkResult[position]
          val url = result.request.substringAfter("url=").substringBefore(", ")
          holder.tvUrl.text = "Url: ${url.substringAfter(getBaseUrl())}"
          holder.tvError.text = "Error: ${result.throwable?.stackTraceToString()?.substringBefore(":")}"
        }
        LogVH::class.hashCode() -> {
          holder as LogVH
          // 之前写的所有日志，发现日志有点难定位问题，就取消了，但又不知道写什么，就暂时空着吧
        }
      }
    }
  
    override fun getItemViewType(position: Int): Int {
      return if (position < mNetworkResult.size) ApiVH::class.hashCode() else LogVH::class.hashCode()
    }
  
    override fun getItemCount(): Int {
      return mNetworkResult.size + 1
    }
  }
}