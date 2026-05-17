package com.cyxbs.pages.map.ui.activity

import android.app.Activity
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.COURSE_POS_TO_MAP
import com.cyxbs.components.config.route.DISCOVER_MAP
import com.cyxbs.components.utils.extensions.launchPickImages
import com.cyxbs.components.utils.extensions.registerForPickMultipleImages
import com.cyxbs.pages.map.R
import com.cyxbs.pages.map.model.DataSet
import com.cyxbs.pages.map.ui.fragment.AllPictureFragment
import com.cyxbs.pages.map.ui.fragment.FavoriteEditFragment
import com.cyxbs.pages.map.ui.fragment.MainFragment
import com.cyxbs.pages.map.util.KeyboardController
import com.cyxbs.pages.map.viewmodel.MapViewModel
import com.cyxbs.pages.map.widget.GlideProgressDialog
import com.cyxbs.pages.map.widget.ProgressDialog
import com.g985892345.provider.api.annotation.KClassProvider

/**
 * 单activity模式，所有fragment在此activity下，能拿到同一个viewModel实例
 * Fragment不能继承BaseViewModelFragment，因为获得的viewModel是不同实例，必须：
 * ViewModelProvider(requireActivity()).get(MapViewModel::class.java)来获得实例
 */


@KClassProvider(clazz = Activity::class, name = DISCOVER_MAP)
class MapActivity : BaseActivity() {

    private val viewModel by viewModels<MapViewModel>()

    /**
     * 注册新的图片选择器（最多9张）
     */
    private val pickImagesLauncher = registerForPickMultipleImages(9) { uris ->
        if (uris.isNotEmpty()) {
            // 将 URI 转换为路径
            val pictureListPath = ArrayList<String>()
            uris.forEach { uri ->
                pictureListPath.add(uri.getAbsolutePath(this))
            }
            
            // 上传图片
            ProgressDialog.show(this, getString(R.string.map_upload_picture_running), getString(R.string.map_please_a_moment_text), false)
            viewModel.uploadPicture(pictureListPath, this)
        }
    }

    private val fragmentManager = supportFragmentManager
    private var mainFragment = MainFragment()
    private var favoriteEditFragment = FavoriteEditFragment()
    private var allPictureFragment = AllPictureFragment()

    // 返回按钮回调
    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            mainFragment.closeSearchFragment()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.map_activity_map)

        // 注册返回按钮回调
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        viewModel.searchShowState.observe {
            onBackPressedCallback.isEnabled = it
        }
        
        // 监听图片选择事件
        viewModel.triggerImagePicker.observe(this) {
            pickImagesLauncher.launchPickImages()
        }

        val openString = intent.getStringExtra(COURSE_POS_TO_MAP)
        /**
         * 如果有保存路径且地图存在，则不展示dialog
         */
        if (!DataSet.mapImageFile.exists()) {
            GlideProgressDialog.show(this, getString(R.string.map_download_title), getString(R.string.map_download_message), false)
        }

        //初始化viewModel
        viewModel.init()
        /**
         * 获取MapInfo后进行地点搜索请求
         */
        viewModel.mapInfo.observe(this, Observer {
            viewModel.getPlaceSearch(openString)
        })
        fragmentManager.beginTransaction().add(R.id.map_fl_main_fragment, mainFragment).show(mainFragment).commit()


        //控制收藏页面是否显示
        viewModel.fragmentFavoriteEditIsShowing.observe(
                this@MapActivity,
                Observer<Boolean> { t ->
                    val map_fl_main_fragment = findViewById<FrameLayout>(R.id.map_fl_main_fragment)
                    if (t == true) {
                        val transaction = fragmentManager.beginTransaction()
                        transaction.setCustomAnimations(R.animator.map_slide_from_right, R.animator.map_slide_to_left, R.animator.map_slide_from_left, R.animator.map_slide_to_right)
                        transaction.hide(mainFragment)
                        if (!favoriteEditFragment.isAdded) {
                            transaction.add(R.id.map_fl_main_fragment, favoriteEditFragment)
                        }
                        transaction
                                .show(favoriteEditFragment)
                                .addToBackStack("favorite_edit")
                                .commit()
                    } else {
                        //隐藏键盘再返回，防止发生布局变形
                        KeyboardController.hideInputKeyboard(map_fl_main_fragment)
                        fragmentManager.beginTransaction()
                                .setCustomAnimations(R.animator.map_slide_from_left, R.animator.map_slide_to_right, R.animator.map_slide_from_right, R.animator.map_slide_to_left)
                                .hide(favoriteEditFragment)
                                .show(mainFragment)
                                .commit()
                        fragmentManager.popBackStack()

                    }
                }
        )

        //控制全部图片页面是否显示
        viewModel.fragmentAllPictureIsShowing.observe(
                this@MapActivity,
                Observer<Boolean> { t ->
                    if (t == true) {
                        val transaction = fragmentManager.beginTransaction()
                        transaction.setCustomAnimations(R.animator.map_slide_from_right, R.animator.map_slide_to_left, R.animator.map_slide_from_left, R.animator.map_slide_to_right)
                        transaction.hide(mainFragment)
                        if (!allPictureFragment.isAdded) {
                            transaction.add(R.id.map_fl_main_fragment, allPictureFragment)
                        }
                        transaction
                                .show(allPictureFragment)
                                .addToBackStack("all_picture")
                                .commit()
                    } else {
                        fragmentManager.beginTransaction()
                                .setCustomAnimations(R.animator.map_slide_from_left, R.animator.map_slide_to_right, R.animator.map_slide_from_right, R.animator.map_slide_to_left)
                                .hide(allPictureFragment)
                                .show(mainFragment)
                                .commit()
                        fragmentManager.popBackStack()

                    }
                }
        )

    }



    override fun onDestroy() {
        super.onDestroy()
        ProgressDialog.hide()
        GlideProgressDialog.hide()
    }

  private fun Uri.getAbsolutePath(context: Context): String{
    val selectedImage = this
    val filePathColumn = arrayOf(MediaStore.Images.Media.DATA)
    val cursor: Cursor? =
      selectedImage.let {
        context.contentResolver?.query(
          it,
          filePathColumn,
          null,
          null,
          null)
      }
    cursor?.moveToFirst()
    val columnIndex = cursor?.getColumnIndex(filePathColumn[0])
    val imgPath = columnIndex?.let { cursor.getString(it) }
    cursor?.close()
    return imgPath?: ""
  }

}
