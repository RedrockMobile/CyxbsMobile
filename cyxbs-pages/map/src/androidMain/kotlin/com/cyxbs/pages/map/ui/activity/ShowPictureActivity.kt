package com.cyxbs.pages.map.ui.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.cyxbs.pages.map.R
import com.cyxbs.pages.map.ui.adapter.MyImageAdapter
import com.cyxbs.pages.map.util.PhotoViewPager
import com.cyxbs.pages.map.widget.MapDialog
import com.cyxbs.pages.map.widget.OnSelectListener
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.utils.extensions.doPermissionAction
import com.cyxbs.components.utils.extensions.launchByLifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream


class ShowPictureActivity : BaseActivity() {

    companion object {
        fun activityStart(context: Context?, images: ArrayList<String>, position: Int) {
            context?.startActivity(
                Intent(context, ShowPictureActivity::class.java)
                    .putExtra("images", images)
                    .putExtra("picturePosition", position)
            )
        }
    }

    private var picturePosition = 0
    private val imageData = mutableListOf<String>()
    private lateinit var adapter: MyImageAdapter
    private val mVpShowPicture by R.id.map_vp_show_picture.view<PhotoViewPager>()
    private val mTvShowPicture by R.id.map_tv_show_picture.view<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.map_activity_show_picture)

        /**
         * 获取点击第几张图片
         */
        picturePosition = intent.getIntExtra("picturePosition", 0)

        adapter = MyImageAdapter(imageData, this)

        /**
         * 单击图片退出
         */
        adapter.setMyOnPhotoClickListener(object : MyImageAdapter.OnPhotoClickListener {
            override fun onPhotoClick() {
                finish()
            }

        })

        /**
         * 长按保存图片
         */
        adapter.setMyOnPhotoLongClickListener(object : MyImageAdapter.OnPhotoLongClickListener {
            override fun onPhotoLongClick(url: String) {
                MapDialog.show(this@ShowPictureActivity,
                    getString(R.string.map_show_picture_save),
                    getString(R.string.map_show_picture_content),
                    object : OnSelectListener {
                        override fun onDeny() {
                        }

                        override fun onPositive() {
                            doPermissionAction(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) {
                                doAfterGranted {
                                    launchByLifecycleScope(Dispatchers.IO) {
                                        val bitmap = suspendCancellableCoroutine {
                                            val future = Glide.with(this@ShowPictureActivity)
                                                .asBitmap()
                                                .load(url)
                                                .submit()
                                            it.invokeOnCancellation {
                                                future.cancel(true)
                                            }
                                            it.resumeWith(runCatching { future.get() })
                                        }
                                        saveImage(bitmap)
                                    }
                                }
                            }
                        }
                    })
            }

        })

        /**
         * 获取图片urlList数据
         */
        val images = intent.getStringArrayListExtra("images")
        if (images != null) {
            imageData.clear()
            imageData.addAll(images)
            adapter.setList(images)
            adapter.notifyDataSetChanged()
        }
        mVpShowPicture.adapter = adapter

        /**
         * 设置当前展示的是点击的图片
         */
        mVpShowPicture.setCurrentItem(picturePosition, false)

        /** 图片上方页数 */
        val s = "${picturePosition + 1}/${imageData.size}"
        mTvShowPicture.text = s
        mVpShowPicture.addOnPageChangeListener(object :
            ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                picturePosition = position
                val content = "${picturePosition + 1}/${imageData.size}"
                mTvShowPicture.text = content
            }
        })
    }

    private fun saveImage(resource: Bitmap) {
        val file = File(
            externalMediaDirs[0]?.absolutePath
                    + File.separator
                    + "Map"
                    + File.separator
                    + System.currentTimeMillis()
                    + ".jpg"
        )
        val parentDir = file.parentFile
        if (parentDir != null) {
            if (parentDir.exists()) parentDir.delete()
            parentDir.mkdir()
        }
        file.createNewFile()
        val fos = FileOutputStream(file)
        resource.compress(Bitmap.CompressFormat.JPEG, 100, fos)
        fos.close()
        galleryAddPic(file.path)
        toast("图片已保存在" + file.absolutePath)
    }

    //更新相册
    private fun galleryAddPic(imagePath: String) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        val f = File(imagePath)
        val contentUri = Uri.fromFile(f)
        mediaScanIntent.data = contentUri
        sendBroadcast(mediaScanIntent)
    }

}