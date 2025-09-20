package com.cyxbs.pages.news.ui.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.Menu
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.cyxbs.components.base.pages.PhotoViewerActivity
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.DISCOVER_NEWS_ITEM
import com.cyxbs.components.view.ui.JToolbar
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.showFile
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.news.R
import com.cyxbs.pages.news.bean.NewsAttachment
import com.cyxbs.pages.news.utils.FileTypeHelper
import com.cyxbs.pages.news.utils.TimeFormatHelper
import com.cyxbs.pages.news.viewmodel.NewsItemViewModel
import com.g985892345.provider.api.annotation.KClassProvider
import com.tbruyelle.rxpermissions3.RxPermissions
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import java.util.regex.Matcher
import java.util.regex.Pattern


@KClassProvider(clazz = Activity::class, name = DISCOVER_NEWS_ITEM)
class NewsItemActivity : BaseActivity(), NewsItemViewModel.NewsDownloadListener {

    private val viewModel by viewModels<NewsItemViewModel>()

    private val uris = mutableListOf<Uri>()
    private var downloadNeedSize = 0
    private var downloadEndSize = 0

    private val tv_title by R.id.tv_title.view<TextView>()
    private val tv_time by R.id.tv_time.view<TextView>()
    private val tv_detail by R.id.tv_detail.view<TextView>()
    private val ll_content by R.id.ll_content.view<LinearLayout>()
    private val common_toolbar by com.cyxbs.components.view.R.id.toolbar.view<JToolbar>()

    private val permissionDialog by lazy {
        AlertDialog.Builder(this)
                .setTitle("权限遭拒")
                .setMessage("没有「存储空间」权限就无法下载哦。\n请轻触「马上去设置」按钮，然后选择「权限」，并给掌上重邮授予「存储空间」权限。")
                .setPositiveButton("马上去设置") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        data = Uri.fromParts("package", packageName, null)
                    })
                }
                .setNegativeButton("放弃") { _, _ -> }
                .create()
    }


    private fun showOpenFileDialog() {
        MaterialDialog(this).show {
            title(text = "下载完成，打开附件")
            positiveButton(text = "确定")
            negativeButton(text = "取消")
            listItemsSingleChoice(items = uris.map { this@NewsItemActivity.showFile(it)!!.name }) { dialog, index, text ->
                if (index != -1) {
                    val uri = uris[index]
                    if (this@NewsItemActivity.showFile(uri)?.exists() == true) {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW)
                                    .addFlags(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    } else {
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                    })
                                    .setDataAndType(uri, FileTypeHelper.getMIMEType(this@NewsItemActivity.showFile(uri)!!)))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            cornerRadius(16F)

        }
    }

    override fun onDownloadStart() {
        toastLong("下载开始，请等待")
    }

    override fun onProgress(id: Int, currentBytes: Long, contentLength: Long) {
        //todo 如果需要进度回调
    }

    @Synchronized
    override fun onDownloadEnd(id: Int, uri: Uri?, e: Throwable?) {
        if (uri != null) {
            uris.add(uri)
        } else {
            e?.printStackTrace()
            AndroidSchedulers.mainThread().scheduleDirect {
                when (e?.message) {
                    "permission deny" -> permissionDialog.show()
                    else -> toast(R.string.news_download_error)
                }
            }
        }
        downloadEndSize++
        if (downloadEndSize == downloadNeedSize) {
            if (uris.size < 1) {
                AndroidSchedulers.mainThread().scheduleDirect {
                    toast("下载失败了，请稍候重试或反馈一下")
                }
                return
            }
            AndroidSchedulers.mainThread().scheduleDirect {
                toast("文件保存于系统\"Download\"文件夹下哦")
                showOpenFileDialog()
            }
        }
    }



    private lateinit var rxPermissions: RxPermissions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.news_activity_detail)

        common_toolbar.init(this, "详情")

        rxPermissions = RxPermissions(this)

        viewModel.news.observe(this, Observer {
            it ?: return@Observer

            tv_title.text = it.title
            tv_time.text = TimeFormatHelper.format(it.date)
            //tv_detail.text =
            if (it.content.length < 10 && it.content.contains("(见附件)")) {
                tv_detail.text = intent.getStringExtra("title")
            } else {
                val s = "\\\$\\{(.*?)\\}\n"
                val m: Matcher = Pattern.compile(s).matcher(it.content)
                val list = mutableListOf<Bitmap>()
                val originStreamList = mutableListOf<String>()
                while (m.find()) {
                    var str = m.group(1)
                    val source = m.group(1)
                    if (str.startsWith("data:image/png;base64")) {
                        str = str.removePrefix("data:image/png;base64")
                    }
                    try {
                        //涉及到base64解码，异常
                        val bitmapArray = Base64.decode(str, Base64.DEFAULT)
                        list.add(BitmapFactory.decodeByteArray(bitmapArray, 0, bitmapArray.size))
                        originStreamList.add(source)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                }
                val texts = it.content.split(Regex(s))
                for ((index, value) in texts.withIndex()) {
                    if (index == 0) {
                        tv_detail.apply {
                            text = value
                            textSize = 15F
                        }
                    } else {
                        val textView = TextView(this).apply {
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            setTextColor(ContextCompat.getColor(this@NewsItemActivity, com.cyxbs.components.config.R.color.config_level_two_font_color))
                            textSize = 15F
                            text = value
                        }
                        ll_content.addView(textView)
                    }
                    if (index >= list.size) continue
                    val imageView = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        setImageBitmap(list[index])
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setOnSingleClickListener {
                            PhotoViewerActivity.start(this@NewsItemActivity, listOf(originStreamList[index]))
                        }
                    }
                    ll_content.addView(imageView)
                }
            }
        })

        intent.getStringExtra("newId").let {
            if (it.isNullOrBlank()) {
                toast(R.string.news_init_error)
                finish()
            } else {
                viewModel.getNews(it)
            }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.news_menu, menu)
        menu.getItem(0)?.setOnMenuItemClickListener { _ ->
            val items = viewModel.news.value?.files
            if (items == null) {
                toast(R.string.news_init)
                return@setOnMenuItemClickListener false
            }
            if (items.isEmpty()) {
                toast(R.string.news_no_download)
                return@setOnMenuItemClickListener false
            }
            MaterialDialog(this).show {
                title(text = "下载附件")
                listItemsMultiChoice(items = items.map { it.name }) { dialog, indices, _ ->
                    val list = mutableListOf<NewsAttachment>()
                    indices.forEach {
                        list.add(items[it])
                    }
                    if (list.isNotEmpty()) {
                        downloadNeedSize = list.size
                        viewModel.download(rxPermissions, list, this@NewsItemActivity)
                    }
                }
                positiveButton(text = "确定")
                cornerRadius(16F)

            }
            return@setOnMenuItemClickListener false
        }
        return super.onCreateOptionsMenu(menu)
    }

}
