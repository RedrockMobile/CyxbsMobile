package com.cyxbs.pages.noclass.page.ui.fragment


import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.noclass.R
import com.cyxbs.pages.noclass.bean.Student
import com.cyxbs.pages.noclass.page.adapter.NoClassTemporaryAdapter
import com.cyxbs.pages.noclass.page.ui.dialog.SearchAllDialog
import com.cyxbs.pages.noclass.page.ui.dialog.SearchNoExistDialog
import com.cyxbs.pages.noclass.page.viewmodel.fragment.TemporaryViewModel
import com.cyxbs.pages.noclass.page.viewmodel.other.CourseViewModel
import com.cyxbs.pages.noclass.util.alphaAnim

/**
 * 临时分组的fragment
 */
class NoClassTemporaryFragment : BaseFragment(R.layout.noclass_fragment_temporary) {

    /**
     * 用户名称
     */
    private lateinit var mUserName: String

    /**
     * 用户id
     */
    private lateinit var mUserId: String

    /**
     * 查询按钮
     */
    private val mBtnQuery: Button by R.id.noclass_temporary_btn_query.view()

    /**
     * 上方添加同学的编辑框
     */
    private val mEditTextView: EditText by R.id.noclass_temporary_et_add_classmate.view()

    /**
     * 下面得提示文字，试试左滑删除列表
     */
    private val mHintText : TextView by R.id.noclass_temporary_tv_hint.view()

    /**
     * 临时分组界面展示人员的Rv和adapter
     */
    private val mRecyclerView: RecyclerView by R.id.noclass_temporary_rv_show.view()
    private val mAdapter by lazy { NoClassTemporaryAdapter() }

    private val mViewModel by viewModels<TemporaryViewModel>()
    private val mCourseViewModel by activityViewModels<CourseViewModel>()

    /**
     * 是否重建
     */
    private var isRebuild = false

    /**
     * 设置两秒后消失得runnable和handler，注意及时释放
     */
    private var mRunnable : Runnable? = null
    private var mHandler : Handler? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isRebuild = savedInstanceState != null
        initUserInfo()
        initRv()
        initObserver()
        initHintText()
        initSearchEvent()
        initFindCourse()
    }

    /**
     * 初始化下面试试左滑删除列表，设置两秒后消失
     */
    private fun initHintText(content : String? = null) {
        mHintText.alpha = 1f
        mHintText.visible()
        content?.let { mHintText.text = it }
        mHandler = Handler(Looper.getMainLooper())
        mRunnable = Runnable {
            mHintText.alphaAnim(mHintText.alpha,0f,500).start()
        }
        mHandler!!.postDelayed(mRunnable!!,2000)
    }

    private fun initFindCourse() {
        //设置查询课表
        mBtnQuery.setOnSingleClickListener(interval = 1000) {
            doSearchCourse()
        }
    }

    /**
     * 初始化临时分组页面的rv
     */
    private fun initRv() {
        mRecyclerView.apply {
            val lm = LinearLayoutManager(requireContext())
            layoutManager = lm
            adapter = mAdapter.apply {
                //加入本人
                val list = listOf(Student("","","","",mUserName,mUserId,""))
                submitList(list)
                setOnItemDelete {
                    deleteMember(it)
                }
                //设置将上一个展开的item关闭的操作
                setOnItemSlideBack {curPosition ->
                    val curList = currentList.toMutableList()
                    rightSlideOpenLoc?.let { lastPosition ->
                        curList[curPosition].isOpen = true
                        curList[lastPosition].isOpen = false
                        submitList(curList)
                        notifyItemChanged(lastPosition)
                    }
                }
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener(){
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    with(mAdapter){
                        rightSlideOpenLoc?.let {
                            val list = currentList.toMutableList()
                            list[it].isOpen = false
                            submitList(list)
                            notifyItemChanged(it)
                            rightSlideOpenLoc = null
                        }
                    }
                }
            })
        }
    }

    /**
     * 初始化用户信息
     */
    private fun initUserInfo() {
        IAccountService::class.impl().userInfo.value?.apply {
            mUserName = username
            mUserId = stuNum
        }
    }


    /**
     * 执行查询空闲课程的操作
     */
    private fun doSearchCourse() {
        mCourseViewModel.getLessons(mAdapter.currentList.map { it.id }, mAdapter.currentList)
    }

    /**
     * 初始化观察者
     */
    private fun initObserver() {
        var searchAllDialog: SearchAllDialog?
        mViewModel.searchAll.observe(viewLifecycleOwner) {
            if (it != null){
                if (it.isSuccess()){
                    //搜索只要成功就清空搜索框框
                    mEditTextView.setText("")
                    if (it.data.types != null && it.data.types!!.isNotEmpty()) {
                        if (childFragmentManager.findFragmentByTag("SearchAllDialog") == null && !isRebuild){
                            searchAllDialog = SearchAllDialog.newInstance(it.data).apply {
                                setOnClickClass { cls ->
                                    val clsList = mAdapter.currentList.toMutableSet()
                                    val ids = clsList.map {stu -> stu.id }
                                    cls.members.forEach {stu ->
                                        if (stu.id !in ids){
                                            clsList.add(stu)
                                        }
                                    }
                                    mAdapter.submitList(clsList.toList())
                                }
                                setOnClickStudent { stu ->
                                    val stuList = mAdapter.currentList.toMutableSet()
                                    val ids = stuList.map {stu1 -> stu1.id }
                                    if (stu.id !in ids){
                                        stuList.add(stu)
                                    }
                                    mAdapter.submitList(stuList.toList())
                                }
                                setOnClickGroup { group ->
                                    val groupList = mAdapter.currentList.toMutableSet()
                                    val ids = groupList.map {stu -> stu.id }
                                    group.members.forEach {stu ->
                                        if (stu.id !in ids){
                                            groupList.add(stu)
                                        }
                                    }
                                    mAdapter.submitList(groupList.toList())
                                }
                            }
                            searchAllDialog!!.show(childFragmentManager, "SearchAllDialog")
                        }else{
                            isRebuild = false
                        }
                    } else {
                        SearchNoExistDialog(requireContext()).show()
                    }
                }else{
                    initHintText("网络异常请检查网络")
                }
            }
        }
    }

    /**
     * 搜索操作的初始化
     */
    private fun initSearchEvent() {
        //防止软键盘弹起导致视图错位
        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        //设置键盘上点击搜索的监听
        mEditTextView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearchStu()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }

    /**
     * 执行查找学生操作
     */
    private fun doSearchStu() {
        val content = mEditTextView.text.toString().trim()
        if (TextUtils.isEmpty(content)) {
            toast("输入为空")
            return
        }
        mViewModel.getSearchAllResult(content)
    }

    override fun onStop() {
        super.onStop()
        mRunnable?.let { mHandler?.removeCallbacks(it) }
        mHintText.gone()
    }


    companion object {
        @JvmStatic
        fun newInstance() = NoClassTemporaryFragment().apply {}
    }
}