package com.cyxbs.pages.widget.service

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import androidx.core.content.edit
import com.cyxbs.components.config.config.SchoolCalendar
import com.cyxbs.components.init.InitialManager
import com.cyxbs.components.init.InitialService
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.unsafeSubscribeBy
import com.cyxbs.components.utils.service.impl
import com.cyxbs.pages.affair.api.IAffairService
import com.cyxbs.pages.course.api.ILessonService
import com.cyxbs.pages.course.api.utils.getStartRow
import com.cyxbs.pages.widget.repo.database.AffairDatabase
import com.cyxbs.pages.widget.repo.database.LessonDatabase
import com.cyxbs.pages.widget.repo.database.LessonDatabase.Companion.MY_STU_NUM
import com.cyxbs.pages.widget.repo.database.LessonDatabase.Companion.OTHERS_STU_NUM
import com.cyxbs.pages.widget.util.defaultSp
import com.cyxbs.pages.widget.util.getMyLessons
import com.g985892345.provider.api.annotation.ImplProvider
import com.ndhzs.widget.CourseWidget
import com.ndhzs.widget.data.IWidgetItem
import com.ndhzs.widget.data.IWidgetRank
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * description ： IWidgetService接口的实现类，通过发送延时广播通知小组件刷新
 * author : Watermelon02
 * email : 1446157077@qq.com
 * date : 2022/8/3 15:22
 */
@ImplProvider(clazz = InitialService::class, name = "WidgetInitialService")
object WidgetInitialService : InitialService {

    override fun onMainProcess(manager: InitialManager) {
        super.onMainProcess(manager)
        val lessonService = ILessonService::class.impl()
        val affairService = IAffairService::class.impl()
        Observable.combineLatest(
            lessonService.observeSelfLesson(), // 自己课的观察流
            lessonService.observeLinkLesson(), // 关联人课的观察流
            affairService.observeSelfAffair(), // 事务的观察流
        ) { self, link, affair ->
            // 装换为 data 数据类
            notifyWidgetRefresh(self, link, affair)
        }.unsafeSubscribeBy()
    }

    private fun notifyWidgetRefresh(
        myLessons: List<ILessonService.Lesson>,
        otherStuLessons: List<ILessonService.Lesson>,
        affairs: List<IAffairService.Affair>,
    ) {
        LessonDatabase.INSTANCE.getLessonDao().deleteAllLessons()
        AffairDatabase.INSTANCE.getAffairDao().deleteAllAffair()
        //设置两者的学号，用于数据库查询
        if (myLessons.isNotEmpty()) {
            myLessons[0].stuNum.let {
                defaultSp.edit { putString(MY_STU_NUM, it) }
            }
        }
        if (otherStuLessons.isNotEmpty()) {
            defaultSp.edit {
                putString(OTHERS_STU_NUM, otherStuLessons[0].stuNum)
            }
        }
        Observable.create<Int> {
            //将传入的来自api模块数据转化为该模块的对应数据并存入数据库
            getMyLessons(3).size
            LessonDatabase.INSTANCE.getLessonDao()
                .insertLessons(com.cyxbs.pages.widget.repo.bean.LessonEntity.convertFromApi(
                    myLessons))
            LessonDatabase.INSTANCE.getLessonDao()
                .insertLessons(com.cyxbs.pages.widget.repo.bean.LessonEntity.convertFromApi(
                    otherStuLessons))
            AffairDatabase.INSTANCE.getAffairDao()
                .insertAffairs(com.cyxbs.pages.widget.repo.bean.AffairEntity.convert(affairs))
            it.onNext(1)
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            //延迟100ms,确保发送广播时已经将数据插入数据库
            .subscribe {
                widgetList.forEach { pkg ->
                    appContext.sendBroadcast(Intent(actionFlush).apply {
                        component = ComponentName(appContext, pkg)
                    })
                }
            }

        refreshCourseSingleWidget(myLessons)
    }

    private var refreshDispose: Disposable? = null

    /**
     * 那个最小且透明的小组件由 985892345 的 CQUPTCourseWidget 开源库提供
     * 
     * 这是很久前写的了，目前没精力继续维护，但能用，后续你们看到起让学弟重构吧
     * —— @985892345 24/10/20
     */
    private fun refreshCourseSingleWidget(
        myLessons: List<ILessonService.Lesson>,
    ) {
        refreshDispose?.dispose()
        refreshDispose = null
        val weekOfTerm = SchoolCalendar.getWeekOfTerm()
        if (weekOfTerm != null) {
            CourseWidget.setData(
                appContext,
                weekOfTerm,
                mapOf(
                    WidgetRankImpl(0) to myLessons.map { LessonWidgetItem(it) },
                )
            )
        } else {
            // 只有第一次使用掌邮才会出现 weekOfTerm 为 null
            refreshDispose = SchoolCalendar.observeWeekOfTerm()
                .firstElement()
                .observeOn(Schedulers.io())
                .unsafeSubscribeBy {
                    refreshCourseSingleWidget(myLessons)
                }
        }
    }

    private class WidgetRankImpl(
        override val rank: Int,
        override val bgColor: Int = Color.TRANSPARENT, // 暂时未使用，因为没写完
        override val tvColor: Int = Color.TRANSPARENT, // 暂时未使用，因为没写完
    ) : IWidgetRank

    private class LessonWidgetItem(
        override val title: String,
        override val content: String,
        override val week: Int,
        override val start: IWidgetItem.Start,
        override val period: Int,
        override val weekNum: IWidgetItem.WeekNum
    ) : IWidgetItem {
        constructor(lesson: ILessonService.Lesson) : this(
            lesson.course,
            lesson.classroom,
            lesson.week,
            IWidgetItem.Start.values()[getStartRow(lesson.beginLesson)],
            lesson.period,
            IWidgetItem.WeekNum.values()[lesson.hashDay]
        )
    }
}

const val actionFlush = "flush"
const val littleWidgetPkg = "com.cyxbs.pages.widget.widget.little.LittleWidget"
const val littleWidgetTransPkg = "com.cyxbs.pages.widget.widget.little.LittleTransWidget"
const val normalWidget = "com.cyxbs.pages.widget.widget.normal.NormalWidget"
const val oversizedAppWidget = "com.cyxbs.pages.widget.widget.oversize.OversizedAppWidget"
val widgetList = listOf(
    littleWidgetPkg,
    littleWidgetTransPkg,
    normalWidget,
    oversizedAppWidget
)