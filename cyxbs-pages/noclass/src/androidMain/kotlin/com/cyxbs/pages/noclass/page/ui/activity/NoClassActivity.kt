package com.cyxbs.pages.noclass.page.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.DISCOVER_NO_CLASS
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.components.utils.adapter.FragmentVpAdapter
import com.cyxbs.components.utils.coroutine.appCoroutineScope
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.NewClickEvent
import com.cyxbs.pages.noclass.R
import com.cyxbs.pages.noclass.bean.NoClassSpareTime
import com.cyxbs.pages.noclass.page.ui.dialog.CreateGroupDialog
import com.cyxbs.pages.noclass.page.ui.dialog.IsCreateSolidDialog
import com.cyxbs.pages.noclass.page.ui.fragment.NoClassCourseVpFragment
import com.cyxbs.pages.noclass.page.ui.fragment.NoClassSolidFragment
import com.cyxbs.pages.noclass.page.ui.fragment.NoClassTemporaryFragment
import com.cyxbs.pages.noclass.page.viewmodel.activity.NoClassViewModel
import com.cyxbs.pages.noclass.page.viewmodel.other.CourseViewModel
import com.g985892345.provider.api.annotation.KClassProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

/**
 *
 * @ProjectName:    CyxbsMobile_Android
 * @Package:        com.cyxbs.pages.noclass.page.ui
 * @ClassName:      NoClassActivity
 * @Author:         Yan
 * @CreateDate:     2022年08月11日 16:07:00
 * @UpdateRemark:   更新说明： 主页不需要网络删除成员
 * @Version:        1.1
 * @Description:    没课约主界面
 */

@KClassProvider(clazz = Activity::class, name = DISCOVER_NO_CLASS)
class NoClassActivity : BaseActivity() {

    // 点击批量添加之后和固定分组fragment通信(3G)
    private val mViewModel by viewModels<NoClassViewModel>()

    //由于课表不止这一个界面要显示，所以做成了单独的ViewModel，方便调用
    private val mCourseViewModel by viewModels<CourseViewModel>()


    /**
     * 返回图标
     */
    private val mImgReturn: ImageView by R.id.iv_noclass_return.view()

    /**
     * 没课越文字
     */
    private val mTvNoClass : TextView by R.id.iv_noclass_tv_noclass.view()

    /**
     * 批量添加文字
     */
    private val mTvBulkAdditions by R.id.no_class_tv_bulk_additions.view<LinearLayout>()

    /**
     * 底部查询fragment的container
     */
    private val mCourseContainer: FrameLayout by R.id.noclass_course_bottom_sheet_container.view()

    private lateinit var mCourseSheetBehavior: BottomSheetBehavior<FrameLayout>

    /**
     * 首页的tabLayout
     */
    private val mTabLayout: TabLayout by R.id.noclass_main_tab.view()

    /**
     * 首页的viewpager2
     */
    private val mViewPager: ViewPager2 by R.id.noclass_main_vp.view()

    /**
     * 首页vp的adapter
     */
    private val mAdapter: FragmentVpAdapter = FragmentVpAdapter(this)

    /**
     * 储存临时分组学号的列表
     */
    private var mStuList: MutableSet<String>? = null

    /**
     * activity是否处于重建状态
     */
    private var isRebuild : Boolean = false

    /**
     * 获取批量添加界面的返回值
     */
    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data
                val haveExtra = intent?.getSerializableExtra("BulkAdditions") as Boolean?
                if (haveExtra != null && haveExtra) {
                    mViewModel.getAllGroup()
                }
            }
        }

    /**
     * 取消状态栏
     */
    override val enableEdgeToEdge: Boolean
        get() = true

    private var enterTime: Long = 0

    override fun onResume() {
        super.onResume()
        enterTime = System.currentTimeMillis()
        appCoroutineScope.launch {
            TrackingUtils.trackExposureEvent(NewClickEvent.EXPOSURE_MOBILE_ZSCY_MKY_HOMEPAGE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val exitTime = System.currentTimeMillis()
        val duration = exitTime - enterTime
        appCoroutineScope.launch {
            TrackingUtils.trackStayEvent(NewClickEvent.TIME_MOBILE_ZSCY_MKY,duration,enterTime)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isRebuild = savedInstanceState != null
        setContentView(R.layout.noclass_activity_no_class)
        initBack()
        initObserve()
        initQueryEvent()
        initCourse()
        initViewPage()
        initTabLayout()
        initClick()
    }


    /**
     * 由于onBackPress已经废弃，所以改用OnBackPressedDispatcher
     */
    private fun initBack() {
        val backCallBack = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mCourseSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                    mCourseSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }else{
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallBack)
    }

    private fun initClick() {
        //批量添加的点击事件
        mTvBulkAdditions.setOnClickListener {
            startForResult.launch(
                Intent(
                    this@NoClassActivity, BatchAdditionActivity::class.java
                )
            )
        }
        // 返回的点击事件
        mImgReturn.setOnClickListener {
            finish()
        }
        mTvNoClass.setOnClickListener {
            finish()
        }
    }

    /**
     * 初始化viewpager的
     */
    private fun initViewPage() {
        // 给adapter中增加fragment
        mAdapter.add(NoClassTemporaryFragment::class.java)
            .add(NoClassSolidFragment::class.java)
        mViewPager.apply {
            adapter = mAdapter
            isUserInputEnabled = false //true:滑动，false：禁止滑动
        }
    }

    /**
     * 初始化tabLayout的设置
     */
    private fun initTabLayout() {
        // 取消tabLayout的长按点击事件
        mTabLayout.isLongClickable = false
        // api 26 以上 设置空text
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            mTabLayout.tooltipText = ""
        }
        //和viewpager联动起来
        val titles = listOf("临时分组", "固定分组")
        TabLayoutMediator(mTabLayout, mViewPager) { tab, position ->
            tab.text = titles[position]
        }.attach()

    }

    private fun initCourse() {
        //先将课表替换为空课表
        replaceFragment(R.id.noclass_course_bottom_sheet_container) {
            NoClassCourseVpFragment.newInstance(NoClassSpareTime.EMPTY_PAGE)
        }
        mCourseSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    /**
     * 搜索操作的初始化
     */
    private fun initQueryEvent() {
        mCourseSheetBehavior = BottomSheetBehavior.from(mCourseContainer)
        mCourseSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> { //展开操作
                        //下次不再提醒，默认为false，也就是提醒,但是为false，进不去条件判断，这里取反，提醒之后的弹窗展示
                        if (!defaultSp.getBoolean("NeverRemindNextOnNoClass", false)) {
                            IsCreateSolidDialog(this@NoClassActivity).apply {
                                //等待弹窗的选择，然后进入下一个弹窗
                                setOnReturnClick { dialog, isRemind ->
                                    if (isRemind) {
                                        dialog.dismiss()
                                        defaultSp.edit()
                                            .putBoolean("NeverRemindNextOnNoClass", true).apply()
                                    }
                                    dialog.dismiss()
                                }
                                setOnContinueClick { dialog, isRemind ->
                                    if (isRemind) {
                                        dialog.dismiss()
                                        defaultSp.edit()
                                            .putBoolean("NeverRemindNextOnNoClass", true).apply()
                                    }
                                    dialog.dismiss()
                                    CreateGroupDialog {
                                        // 创建成功之后的操作
                                        getAllGroup()
                                    }.apply {
                                        setExtraMembers(mStuList!!.toList())
                                    }.show(
                                        supportFragmentManager,
                                        "CreateGroupDialogFragment"
                                    )
                                }
                            }.show()
                        }
                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> { //折叠操作
                    }
                    BottomSheetBehavior.STATE_HIDDEN -> { //每次隐藏清空查询列表中的数据
                    }

                    else -> {}
                }
            }
        })
    }

    private fun getAllGroup() {
        mViewModel.getAllGroup()
    }

    private fun initObserve() {
        //在滑动下拉课表容器中添加整个课表,等待fragment中请求数据
        mCourseViewModel.noclassData.observe(this) {
            if (!isRebuild){
                mStuList = it[0]!!.mIdToNameMap.keys
                mCourseSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }else{
                isRebuild = false
            }
        }
    }

}