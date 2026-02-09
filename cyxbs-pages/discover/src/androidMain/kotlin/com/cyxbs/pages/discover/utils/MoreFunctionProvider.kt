package com.cyxbs.pages.discover.utils

import com.cyxbs.components.base.operations.doIfLogin
import com.cyxbs.components.base.ui.BaseUi
import com.cyxbs.components.config.navigation.NAV_MAP
import com.cyxbs.components.config.route.DISCOVER_CALENDAR
import com.cyxbs.components.config.route.DISCOVER_EMPTY_ROOM
import com.cyxbs.components.config.route.DISCOVER_GRADES
import com.cyxbs.components.config.route.DISCOVER_MAP
import com.cyxbs.components.config.route.DISCOVER_NO_CLASS
import com.cyxbs.components.config.route.DISCOVER_OTHER_COURSE
import com.cyxbs.components.config.route.DISCOVER_SCHOOL_CAR
import com.cyxbs.components.config.route.DISCOVER_SPORT
import com.cyxbs.components.config.route.DISCOVER_TODO_MAIN
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.components.utils.logger.event.ClickEvent
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.init.MainNavController
import com.cyxbs.pages.discover.R
import com.cyxbs.pages.map.api.MapNavArgument
import java.lang.ref.SoftReference
import kotlin.reflect.KClass

/**
 * @author zixuan
 * 2019/11/20
 */
object MoreFunctionProvider {
    const val HOME_PAGE_FUNCTION_1 = "homePageFunction1"
    const val HOME_PAGE_FUNCTION_2 = "homePageFunction2"
    const val HOME_PAGE_FUNCTION_3 = "homePageFunction3"
    private var homeFunctions: SoftReference<MutableList<Function>> = SoftReference(mutableListOf())
    val functions = listOf(
            Function(R.drawable.discover_ic_other_course, R.string.discover_title_other_course, R.string.discover_detail_other_course, StartActivityAfterLogin("同学课表", DISCOVER_OTHER_COURSE), ClickEvent.CLICK_KBCX_ENTRY),
            Function(R.drawable.discover_ic_map, R.string.discover_title_map, R.string.discover_detail_map, StartNavDestination(MapNavArgument(null)), ClickEvent.CLICK_CYDT_ENTRY),
            Function(R.drawable.discover_ic_no_class, R.string.discover_title_no_class, R.string.discover_detail_no_class, StartActivityAfterLogin("没课约", DISCOVER_NO_CLASS), ClickEvent.CLICK_MKY_ENTRY),
            Function(R.drawable.discover_ic_bus_track, R.string.discover_title_bus_track, R.string.discover_detail_bus_track, StartActivityImpl(DISCOVER_SCHOOL_CAR), ClickEvent.CLICK_XCGJ_ENTRY),
            Function(R.drawable.discover_ic_empty_classroom, R.string.discover_title_empty_classroom, R.string.discover_detail_empty_classroom, StartActivityImpl(DISCOVER_EMPTY_ROOM), ClickEvent.CLICK_YLC_KJS_ENTRY),
            Function(R.drawable.discover_ic_school_calendar, R.string.discover_title_school_calendar, R.string.discover_detail_school_calendar, StartActivityImpl(DISCOVER_CALENDAR), ClickEvent.CLICK_YLC_XL_ENTRY),
            Function(R.drawable.discover_ic_todo,R.string.discover_title_todo, R.string.discover_detail_todo, StartActivityImpl(DISCOVER_TODO_MAIN), ClickEvent.CLICK_YZQD_ENTRY),
            Function(R.drawable.discover_ic_sport, R.string.discover_title_sport, R.string.discover_detail_sport, StartActivityAfterLogin("体育打卡", DISCOVER_SPORT), ClickEvent.CLICK_YLC_TYDK_ENTRY),
            Function(R.drawable.discover_ic_my_exam, R.string.discover_title_my_exam, R.string.discover_detail_my_exam, StartActivityAfterLogin("我的考试", DISCOVER_GRADES), ClickEvent.CLICK_YLC_WDKS_ENTRY),
            Function(R.drawable.discover_ic_more_function, R.string.discover_title_more_function, R.string.discover_detail_more_function, null))

    //当有缓存时直接从缓存中获取，没有时从sp中拿
    fun getHomePageFunctions(): List<Function> {
        var func = homeFunctions.get()
        if (func == null) {
            homeFunctions = SoftReference(mutableListOf())
            func = homeFunctions.get()
        }
        if (func != null && func.size != 3) {
            val indexes: List<Int> = getHomePageFunctionsFromSp()
            for (index in indexes) {
                func.add(this.functions[index])
            }
            return func
        } else {
            if (func?.size == 3)
                return func
        }

        return listOf()
    }

    private fun getHomePageFunctionsFromSp(): List<Int> {
        val list = mutableListOf<Int>()
        defaultSp.apply {
            list.add(getInt(HOME_PAGE_FUNCTION_1, 2))
            list.add(getInt(HOME_PAGE_FUNCTION_2, 1))
            list.add(getInt(HOME_PAGE_FUNCTION_3, 4))
        }
        list.add(functions.lastIndex)
        return list
    }


    class Function(var resource: Int, val title: Int, val detail: Int, val activityStarter: StartActivityAble?, val clickEvent: ClickEvent? = null)
    interface StartActivityAble {
        fun startActivity(baseUi: BaseUi)
    }

    class StartActivityImpl(private val routing: String) : StartActivityAble {
        override fun startActivity(baseUi: BaseUi) {
            startActivity(routing)
        }
    }

    class StartNavDestination<T : Any>(private val route: T) : StartActivityAble {
        override fun startActivity(baseUi: BaseUi) {
            MainNavController.navigate(route)
        }

    }

    class StartActivityAfterLogin(private val msg: String, private val routing: String) : StartActivityAble {
        override fun startActivity(baseUi: BaseUi) {
            baseUi.doIfLogin(msg) {
                startActivity(routing)
            }
        }

    }

}