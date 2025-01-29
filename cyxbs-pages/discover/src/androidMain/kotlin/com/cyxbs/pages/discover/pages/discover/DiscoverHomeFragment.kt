package com.cyxbs.pages.discover.pages.discover

import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.base.operations.doIfLogin
import com.cyxbs.components.base.ui.BaseFragment
import com.cyxbs.components.config.config.SchoolCalendar
import com.cyxbs.components.config.route.DISCOVER_ENTRY
import com.cyxbs.components.config.route.DISCOVER_NEWS
import com.cyxbs.components.config.route.DISCOVER_NEWS_ITEM
import com.cyxbs.components.config.route.MINE_CHECK_IN
import com.cyxbs.components.config.route.NOTIFICATION_HOME
import com.cyxbs.components.utils.extensions.dp2px
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.components.utils.extensions.visible
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.service.impl
import com.cyxbs.components.utils.service.startActivity
import com.cyxbs.components.utils.utils.get.Num2CN
import com.cyxbs.pages.discover.R
import com.cyxbs.pages.discover.pages.discover.adapter.DiscoverMoreFunctionRvAdapter
import com.cyxbs.pages.discover.pages.discover.adapter.RollerViewInfoAdapter
import com.cyxbs.pages.discover.utils.IS_SWITCH1_SELECT
import com.cyxbs.pages.discover.utils.MoreFunctionProvider
import com.cyxbs.pages.discover.utils.NotificationSp
import com.cyxbs.pages.discover.utils.SpacesHorizontalItemDecoration
import com.cyxbs.pages.discover.widget.IndicatorView
import com.cyxbs.pages.electricity.api.IElectricityService
import com.cyxbs.pages.sport.api.ISportService
import com.cyxbs.pages.todo.api.ITodoService
import com.g985892345.provider.api.annotation.ImplProvider
import com.ndhzs.slideshow.SlideShow
import com.ndhzs.slideshow.viewpager.transformer.ScaleInTransformer
import kotlinx.coroutines.launch
import java.util.Calendar


/**
 * @author zixuan
 * 2019/11/20
 */

@ImplProvider(clazz = Fragment::class, name = DISCOVER_ENTRY)
class DiscoverHomeFragment : BaseFragment() {

    private val viewModel by viewModels<DiscoverHomeViewModel>()

    private val fl_discover_home_jwnews by R.id.fl_discover_home_jwnews.view<FrameLayout>()
    private val tv_day by R.id.tv_day.view<AppCompatTextView>()
    private val iv_discover_msg by R.id.iv_discover_msg.view<ImageView>()
    private val iv_discover_msg_red_dot by R.id.iv_discover_msg_red_dot.view<ImageView>()
    private val rv_discover_more_function by R.id.rv_discover_more_function.view<RecyclerView>()
    private val ll_discover_feeds by R.id.ll_discover_feeds.view<LinearLayoutCompat>()
    private val indicator_view_discover by R.id.indicator_view_discover.view<IndicatorView>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.discover_home_fragment, container, false)
    }

    private val mVfDetail by R.id.vf_jwzx_detail.view<ViewFlipper>()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 偏移状态栏
        // 因为外层是 Compose 会默认消耗 OnApplyWindowInsets，所以这里只能单独获取状态栏高度
        requireActivity().window.decorView.doOnAttach {
            val statusBarsInsets = WindowInsetsCompat.toWindowInsetsCompat(it.rootWindowInsets)
                .getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(statusBarsInsets.left, statusBarsInsets.top, statusBarsInsets.right, statusBarsInsets.bottom)
        }

        if (savedInstanceState == null) {
            initFeeds()
        }
        initTvDay()
        initJwNews(fl_discover_home_jwnews)
        initBanner()
        initHasUnread()
        view.findViewById<View>(R.id.iv_check_in).setOnSingleClickListener {
            doIfLogin("签到") {
                startActivity(MINE_CHECK_IN)
            }
        }
    }
    
    /**
     * 从老课表那里移过来的代码
     */
    private fun initTvDay() {
        if (!IAccountService::class.impl().isLogin()) {
            tv_day.text = "登录解锁更多功能~"
        } else {
            val nowWeek = SchoolCalendar.getWeekOfTerm()
            if (nowWeek != null) {
                //这个用来判断是不是可能处于是暑假的那段时间除非大变动应该暑假绝对是6，7，8，9月当中
                val summerVacation = listOf(6, 7, 8, 9)
                val now = Calendar.getInstance()
                tv_day.text = when {
                    nowWeek > 0 ->
                        "第${Num2CN.number2ChineseNumber(nowWeek.toLong())}周 " +
                          "周${if (now[Calendar.DAY_OF_WEEK] != 1)
                              Num2CN.number2ChineseNumber(now[Calendar.DAY_OF_WEEK] - 1.toLong())
                              else "日"}"
                    //8，9月欢迎新同学
                    (now[Calendar.MONTH] + 1 == 8 || now[Calendar.MONTH] + 1 == 9) -> "欢迎新同学～"
                    nowWeek !in 1 .. 21 && summerVacation.contains(now[Calendar.MONTH] + 1) -> "暑假快乐鸭"
                    nowWeek !in 1 .. 21 && !summerVacation.contains(now[Calendar.MONTH] + 1) -> "寒假快乐鸭"
                    else -> ""
                }
            }
        }
    }

    private fun initHasUnread() {
        //将msg View设置为没有消息的状态
        iv_discover_msg.setBackgroundResource(R.drawable.discover_ic_home_msg)
        doIfLogin {
            iv_discover_msg.setOnClickListener {
                startActivity(NOTIFICATION_HOME)
            }
        }
        viewModel.hasUnread.observe {
            val shouldShowRedDots = requireActivity().NotificationSp.getBoolean(IS_SWITCH1_SELECT,true)
            if (it == true && shouldShowRedDots) {
                //将msg View设置为有消息的状态
                iv_discover_msg_red_dot.visible()
                /*
                iv_discover_msg.setBackgroundResource(R.drawable.discover_ic_home_has_msg)
                */
            } else {
                //将msg View设置为没有消息的状态
                iv_discover_msg_red_dot.gone()
                /*
                iv_discover_msg.setBackgroundResource(R.drawable.discover_ic_home_msg)
                 */
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.getNotificationUnReadStatus()
    }
    override fun onResume() {
        super.onResume()
        initFunctions()
        if (viewModel.functionRvState != null) {
            rv_discover_more_function.layoutManager?.onRestoreInstanceState(viewModel.functionRvState)
        }
    }
    
    private val mSlideShow by R.id.discover_ss_banner.view<SlideShow>()
    private val mIvBannerBg by R.id.discover_iv_banner_bg.view<ImageView>()

    private fun initBanner() {
        viewModel.viewPagerInfo.observe { list ->
            if (list.isEmpty()) return@observe
            mSlideShow.visible()
            mIvBannerBg.animate()
                .alpha(0F)
                .duration = 600
            mSlideShow.alpha = 0F
            mSlideShow.animate()
                .alpha(1F)
                .duration = 600
            mSlideShow.addTransformer(ScaleInTransformer())
                .setAutoSlideTime(1200, 6000)
                .setTimeInterpolator(DecelerateInterpolator())
                .apply { offscreenPageLimit = 1 }
                .setAdapter(RollerViewInfoAdapter(list))
            // setImgAdapter 不可用，其中 ImageViewAdapter.Builder 类型推断在 Kt2 上有问题
            // 所以使用普通 adapter 代替
        }
    }

    private fun initJwNews(frameLayout: FrameLayout) {
        viewModel.jwNews.observe {
            if (it != null) {
                mVfDetail.removeAllViews()
                for (item in it) {
                    mVfDetail.addView(getTextView(item.title, item.id))
                }
                mVfDetail.startFlipping()
            }
        }
    
        mVfDetail.setOnSingleClickListener {
            startActivity(DISCOVER_NEWS_ITEM) {
                putExtra("newId", mVfDetail.focusedChild.tag as String)
            }
        }
    
        mVfDetail.flipInterval = 6000
        mVfDetail.setInAnimation(context, R.anim.discover_text_in_anim)
        mVfDetail.setOutAnimation(context, R.anim.discover_text_out_anim)

        frameLayout.setOnSingleClickListener {
            startActivity(DISCOVER_NEWS)
        }
    }

    private fun getTextView(info: String, id: String): TextView {
        return TextView(context).apply {
            text = info
            maxLines = 1
            overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS

            setTextColor(ContextCompat.getColor(context, R.color.discover_menu_font_color_found))
            textSize = 15f
            setOnSingleClickListener {
                startActivity(DISCOVER_NEWS_ITEM) {
                    putExtra("newId", id)
                }
            }
        }
    }

    //加载发现首页中跳转按钮
    private fun initFunctions() {
        val functions = MoreFunctionProvider.functions
        val picUrls = functions.map { it.resource }
        val texts = functions.map { getString(it.title) }
        rv_discover_more_function.apply {
            SpacesHorizontalItemDecoration(50F.dp2px).attach(this)
            layoutManager = LinearLayoutManager(context).apply {
                orientation = LinearLayoutManager.HORIZONTAL
            }
            adapter = DiscoverMoreFunctionRvAdapter(picUrls, texts) {
                if (it == functions.size - 1) {
                    getString(R.string.discover_more_function_notice_text).toast()
                } else {
                    if (IAccountService::class.impl().isLogin()) {
                        // 发现首页横排按钮点击埋点
                        functions[it].clickEvent?.let {  clickEvent ->
                            appCoroutineScope.launch {
                                TrackingUtils.trackClickEvent(clickEvent)
                            }
                        }
                    }

                    functions[it].activityStarter?.startActivity(this@DiscoverHomeFragment)
                }
            }
            this.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val offset = computeHorizontalScrollOffset().toFloat()
                    val range = computeHorizontalScrollRange().toFloat()
                    val extent = computeHorizontalScrollExtent().toFloat()
                    indicator_view_discover.doMove(offset / (range - extent))
                }
            })
        }
    }

    private fun initFeeds() {
        addFeedFragment(ISportService::class.impl().getSportFeed())
        addFeedFragment(ITodoService::class.impl().getTodoFeed())
        addFeedFragment(IElectricityService::class.impl().getElectricityFeed())
        // 临时关闭服务，待后续网校使用正规渠道拿到数据后再开启
//        addFeedFragment(IVolunteerService::class.impl().getVolunteerFeed())
        //处理手机屏幕过长导致feed无法填充满下方的情况
        ll_discover_feeds.post {
            context?.let {
                val point = Point()
                (it.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getSize(point)
                ll_discover_feeds.minimumHeight = point.y - ll_discover_feeds.top
            }
        }
    }

    private fun addFeedFragment(fragment: Fragment) {
        childFragmentManager.beginTransaction().add(R.id.ll_discover_feeds, fragment).commit()
    }

    override fun onPause() {
        super.onPause()
        mVfDetail.stopFlipping()
        viewModel.functionRvState = rv_discover_more_function.layoutManager?.onSaveInstanceState()
    }
}
