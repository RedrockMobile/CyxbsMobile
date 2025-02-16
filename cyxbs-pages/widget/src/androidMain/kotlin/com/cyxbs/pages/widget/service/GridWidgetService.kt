package com.cyxbs.pages.widget.service

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.cyxbs.components.utils.utils.get.Num2CN
import com.cyxbs.pages.course.api.utils.parseClassRoom
import com.cyxbs.pages.widget.R
import com.cyxbs.pages.widget.repo.bean.AffairEntity
import com.cyxbs.pages.widget.repo.bean.LessonEntity
import com.cyxbs.pages.widget.util.CLICK_AFFAIR
import com.cyxbs.pages.widget.util.CLICK_LESSON
import com.cyxbs.pages.widget.util.ClassRoomParse
import com.cyxbs.pages.widget.util.POSITION
import com.cyxbs.pages.widget.util.SchoolCalendar
import com.cyxbs.pages.widget.util.defaultSp
import com.cyxbs.pages.widget.util.getAffairs
import com.cyxbs.pages.widget.util.getMyLessons
import com.cyxbs.pages.widget.util.getOthersStuNum
import com.cyxbs.pages.widget.util.gson
import kotlin.concurrent.thread

/**
 * author : Watermelon02
 * email : 1446157077@qq.com
 */
class GridWidgetService : RemoteViewsService() {

    companion object {
        private lateinit var myLessons: List<LessonEntity>
        private lateinit var otherLessons: Array<Array<LessonEntity?>>
        private lateinit var affairs: Array<Array<AffairEntity?>>


        //中午，下午的period
        const val SPECIAL_PERIOD = 1

        private fun makeSchedules(): Array<Array<LessonEntity?>> {
            val weekOfTerm = SchoolCalendar().weekOfTerm
            myLessons = getMyLessons(weekOfTerm)
            otherLessons = getOthersStuNum(weekOfTerm).schedule()
            affairs = Array(8) { arrayOfNulls<AffairEntity?>(8) }
            getAffairs(weekOfTerm).splitAffairs().schedule()
            return myLessons.schedule()
        }

        private fun List<LessonEntity>.schedule(): Array<Array<LessonEntity?>> {
            val mScheduleLessons = Array(8) { arrayOfNulls<LessonEntity>(8) }
            for (i in 0 until size) {
                get(i).let {
                    val row = if (it.beginLesson < 4)
                        it.beginLesson / 2
                    else if (it.beginLesson < 8)
                        it.beginLesson / 2 + 1
                    else
                        it.beginLesson / 2 + 2
                    val column = it.hashDay + 1
                    mScheduleLessons[row][column] = it
                    if (it.period == 3) {
                        mScheduleLessons[row + 1][column] = it.copy(period = 1)
                    } else if (it.period == 4) {
                        mScheduleLessons[row + 1][column] = it
                    }
                }
            }
            return mScheduleLessons
        }

        private fun List<AffairEntity>.splitAffairs(): List<AffairEntity> {
            val splitAffairList = arrayListOf<AffairEntity>()
            for (i in 0 until size) {
                get(i).let {
                    //扣除中午，下午period后的affair
                    splitAffairList.addAll(splitAtSpecialAffairs(it))
                }
            }
            return splitAffairList
        }

        private fun List<AffairEntity>.schedule(): Array<Array<AffairEntity?>> {
            for (i in 0 until size) {
                get(i).let {
                    //处理beginLesson特殊的情况
                    val column = it.day + 1
                    if (it.beginLesson % 2 == 0 && it.beginLesson != -2) {//从偶数节课开始
                        val evenAffair = it.copy(period = 1, beginLesson = it.beginLesson)
                        var row = it.beginLesson / 2 - 1
                        row +=
                            if (it.period + it.beginLesson in 6..9) 1 else if (it.beginLesson + it.period > 9) 2 else 0
                        affairs[row][column] = evenAffair
                        addNormalAffair(it.copy(period = it.period - 1,
                            beginLesson = it.beginLesson + 1))
                    } else if (it.beginLesson == -1) {//中午
                        affairs[2][column] =
                            it.copy(period = SPECIAL_PERIOD, beginLesson = 5)
                        addNormalAffair(it.copy(period = it.period - SPECIAL_PERIOD,
                            beginLesson = 5))
                    } else if (it.beginLesson == -2) {//晚上
                        affairs[5][column] =
                            it.copy(period = SPECIAL_PERIOD, beginLesson = 9)
                        addNormalAffair(it.copy(period = it.period - SPECIAL_PERIOD,
                            beginLesson = 9))
                    } else {
                        addNormalAffair(it)
                    }
                }
            }
            return affairs
        }

        //处理跨越中午，下午的事务
        private fun splitAtSpecialAffairs(
            affairEntity: AffairEntity,
        ): ArrayList<AffairEntity> {
            //如果跨越了中午，下午，则以中午、下午为界限拆成多个事务,放入这个list中
            val splitAffairList = arrayListOf<AffairEntity>()
            if (affairEntity.beginLesson in 1..4) {
                if (affairEntity.period + affairEntity.beginLesson in 6..9) {//跨越中午
                    affairs[2][affairEntity.day + 1] =
                        affairEntity.copy(period = 1, beginLesson = -1)
                    splitAffairList.add(affairEntity.copy(period = 5 - affairEntity.beginLesson))
                    splitAffairList.add(affairEntity.copy(period = affairEntity.period - SPECIAL_PERIOD - (5 - affairEntity.beginLesson),
                        beginLesson = 5))
                } else if (affairEntity.period + affairEntity.beginLesson > 9) {//跨越中午和下午
                    affairs[2][affairEntity.day + 1] =
                        affairEntity.copy(period = 1, beginLesson = -1)
                    affairs[5][affairEntity.day + 1] =
                        affairEntity.copy(period = 1, beginLesson = -2)
                    splitAffairList.add(affairEntity.copy(period = 5 - affairEntity.beginLesson))
                    splitAffairList.add(affairEntity.copy(period = 4, beginLesson = 5))
                    splitAffairList.add(affairEntity.copy(period = affairEntity.period - 4 - (5 - affairEntity.beginLesson) - (2 * SPECIAL_PERIOD),
                        beginLesson = 9))
                } else splitAffairList.add(affairEntity)//1-4的普通affair
            } else if (affairEntity.beginLesson in 5..8) {
                if (affairEntity.period + affairEntity.beginLesson > 9) {
                    affairs[5][affairEntity.day + 1] =
                        affairEntity.copy(period = 5, beginLesson = -2)
                    splitAffairList.add(affairEntity.copy(period = 9 - affairEntity.beginLesson))
                    splitAffairList.add(affairEntity.copy(period = affairEntity.period - SPECIAL_PERIOD - (9 - affairEntity.beginLesson)))
                } else splitAffairList.add(affairEntity)//5-8的普通affair
            } else {
                splitAffairList.add(affairEntity)//9-12的普通affair
            }
            return splitAffairList
        }

        //添加在1-4,5-8,9-12节课上的事务
        private fun addNormalAffair(
            affairEntity: AffairEntity,
        ) {
            var row =
                if (affairEntity.beginLesson % 2 == 1)
                    affairEntity.beginLesson / 2
                else
                    affairEntity.beginLesson / 2 - 1
            row +=
                if (affairEntity.period + affairEntity.beginLesson in 6..9) 1 else if (affairEntity.beginLesson + affairEntity.period > 9) 2 else 0
            val column = affairEntity.day + 1
            if (affairEntity.period == 1) {
                affairs[row][column] = affairEntity
            } else if (affairEntity.period == 2) {
                affairs[row][column] = affairEntity
            } else if (affairEntity.period == 3) {
                affairs[row][column] = affairEntity.copy(period = 2)
                affairs[row + 1][column] =
                    affairEntity.copy(beginLesson = affairEntity.beginLesson + 2, period = 1)
            } else if (affairEntity.period == 4) {
                affairs[row][column] = affairEntity.copy(period = 2)
                affairs[row + 1][column] =
                    affairEntity.copy(period = 2,
                        beginLesson = affairEntity.beginLesson + 2)
            }
        }
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return GridRemoteViewsFactory(this, intent)
    }

    private inner class GridRemoteViewsFactory(val mContext: Context, val intent: Intent) :
        RemoteViewsFactory {

        private var mScheduleLessons: Array<Array<LessonEntity?>>? = null

        override fun getViewAt(position: Int): RemoteViews? {
            val lastClickPosition =
                mContext.getSharedPreferences("module_widget", Context.MODE_PRIVATE)
                    .getInt(POSITION, -1)
            if (position == 0) //显示月份
                return RemoteViews(mContext.packageName,
                    R.layout.widget_grid_view_month).apply {
                    setTextViewText(R.id.tv_month,
                        SchoolCalendar().month.toString() + "月")
                }
            //如果等于0，说明是第1列的，该显示课程是第几节
            if (position % 8 == 0)
                return getLessonNumRv(position)
            //如果是第1到7个说明是显示今天是星期几
            if (position in 1..7) return getWeekDayRv(position)
            if (position in 24..31 || position in 48..55) return getHalfAffairRv(position)
            val remoteViews = mScheduleLessons?.let { mSchedulesArray ->
                val mPosition = position - 8
                val lesson = mSchedulesArray[mPosition / 8][mPosition % 8]
                if (lesson == null) {
                    if (otherLessons[mPosition / 8][mPosition % 8] != null)
                    /** 如果有同学的课，设置other_course的背景*/
                        getOtherLessonRV(mPosition, position)
                    else if (affairs[mPosition / 8][mPosition % 8] != null)
                    /** 如果是事务，设置事务的背景*/
                        getAffairRv(mPosition)
                    else
                    /** 无事务也无课*/
                    {
                        RemoteViews(mContext.packageName,
                            R.layout.widget_grid_view_item_blank)
                    }
                } else {
                    val remoteViews: RemoteViews =
                        when {
                            /**根据早上，下午，晚上的课设置不同背景*/
                            mPosition < 16 && lesson.period == 1 -> RemoteViews(mContext.packageName,
                                R.layout.widget_grid_view_item_half_moring)
                            mPosition < 16 -> RemoteViews(mContext.packageName,
                                R.layout.widget_grid_view_moring_item)
                            mPosition < 40 && lesson.period == 1 -> RemoteViews(mContext.packageName,
                                R.layout.widget_grid_view_item_half_afternoon)
                            mPosition < 40 -> RemoteViews(mContext.packageName,
                                R.layout.widget_grid_view_afternoon_item)
                            lesson.period == 1 -> RemoteViews(mContext.packageName,
                                R.layout.widget_grid_view_item_half_night)
                            else -> RemoteViews(mContext.packageName,
                                R.layout.widget_grid_view_night_item)
                        }
                    if (lesson.period == 1) decoratedSingleLessonWithAffair(remoteViews,
                        mPosition) else decoratedLessonWithAffair(remoteViews, mPosition)
                    decoratedLessonWithOtherLesson(remoteViews, mPosition)
                    remoteViews.setTextViewText(R.id.top, lesson.course)
                    remoteViews.setTextViewText(R.id.bottom,
                        ClassRoomParse.parseClassRoom(lesson.classroom))
                    remoteViews.setOnClickFillInIntent(R.id.background,
                        Intent().putExtra(CLICK_LESSON, gson.toJson(lesson)))
                    return remoteViews
                }
            }
            return remoteViews
        }

        /**如果有单节课，且有事务，根据事务的beginLesson和在课程上面添加装饰*/
        private fun decoratedSingleLessonWithAffair(remoteViews: RemoteViews, mPosition: Int) {
            val affair = affairs[mPosition / 8][mPosition % 8]
            affair?.let {
                if (affair.period == 2) {
                    remoteViews.setViewVisibility(R.id.affair, View.VISIBLE)
                    remoteViews.setViewVisibility(R.id.half_affair_title, View.VISIBLE)
                    remoteViews.setTextViewText(R.id.half_affair_title, affair.title)
                } else if (affair.beginLesson % 2 == 0) {
                    remoteViews.setViewVisibility(R.id.half_affair_title, View.VISIBLE)
                    remoteViews.setTextViewText(R.id.half_affair_title, affair.title)
                } else if (affair.beginLesson % 2 == 1) {
                    remoteViews.setViewVisibility(R.id.affair, View.VISIBLE)
                }
            }
        }

        /**如果有关联他人的课表，则在课程上面添加装饰*/
        private fun decoratedLessonWithOtherLesson(remoteViews: RemoteViews, mPosition: Int) {
            //这一行是必须的，不然在课程上有事务时，所有的课程都会有装饰
            remoteViews.setViewVisibility(R.id.other_lesson, View.GONE)
            if (otherLessons[mPosition / 8][mPosition % 8] != null) {
                remoteViews.setViewVisibility(R.id.other_lesson, View.VISIBLE)
            }
        }

        /**如果有事务，则在上面添加小红点*/
        private fun decoratedLessonWithAffair(remoteViews: RemoteViews, mPosition: Int) {
            remoteViews.setViewVisibility(R.id.affair, View.GONE)
            if (affairs[mPosition / 8][mPosition % 8] != null) {
                remoteViews.setViewVisibility(R.id.affair, View.VISIBLE)
            }
        }

        /**生成事务rv*/
        private fun getAffairRv(mPosition: Int): RemoteViews {
            val affair = affairs[mPosition / 8][mPosition % 8]
            return (if (affair!!.period == 1) {
                if (affair.beginLesson % 2 == 1) RemoteViews(mContext.packageName,
                    R.layout.widget_grid_view_item_half_affair).apply {
                    setTextViewText(R.id.half_affair_title, affair.title)
                } else RemoteViews(mContext.packageName,
                    R.layout.widget_grid_view_item_half_bottom_affair).apply {
                    setTextViewText(R.id.half_affair_title, affair.title)
                }
            } else RemoteViews(mContext.packageName, R.layout.widget_grid_view_item_affair).apply {
                setTextViewText(R.id.top, affair.title)
                setTextViewText(R.id.bottom, parseClassRoom(affair.content))
            }).apply {
                setOnClickFillInIntent(R.id.background,
                    Intent().putExtra(CLICK_AFFAIR,
                        gson.toJson(affairs[mPosition / 8][mPosition % 8])))
            }
        }


        /**生成中午，下午的半长事务或者空白rv*/
        private fun getHalfAffairRv(position: Int): RemoteViews {
            val affairEntity = affairs[(position - 8) / 8][(position - 8) % 8]
            return if (affairEntity != null) RemoteViews(mContext.packageName,
                R.layout.widget_grid_view_item_half_affair).apply {
                setTextViewText(R.id.half_affair_title, affairEntity.title)
            } else {
                RemoteViews(mContext.packageName,
                    R.layout.widget_grid_view_item_half_blank)
            }
        }


        /**生成显示星期几的rv*/
        private fun getWeekDayRv(position: Int): RemoteViews {
            val time = defaultSp.getInt("time", 0)
            //获取当前item是否应该高亮
            val id = if (time == position - 1)
                R.layout.widget_grid_view_day_of_week_selected_item
            else R.layout.widget_grid_view_day_of_week_item
            //返回对应的item
            return RemoteViews(mContext.packageName, id).apply {
                setTextViewText(R.id.tv_day_of_week, "周${
                    if (position != 7) Num2CN.number2ChineseNumber(position) else "日"
                }")
            }
        }

        /**生成课表显示第几节课以及中午、下午的rv*/
        private fun getLessonNumRv(position: Int): RemoteViews {
            return if (position == 8 || position == 16) RemoteViews(mContext.packageName,
                R.layout.widget_grid_view_item_lesson_num).apply {
                setTextViewText(R.id.lesson_num_top, (position / 8 * 2 - 1).toString())
                setTextViewText(R.id.lesson_num_bottom, (position / 8 * 2).toString())
            }
            else if (position == 24) //显示中午
                RemoteViews(mContext.packageName,
                    R.layout.widget_grid_view_item_lesson_num_afternoon)
            else if (position == 32 || position == 40)
                RemoteViews(mContext.packageName,
                    R.layout.widget_grid_view_item_lesson_num).apply {
                    setTextViewText(R.id.lesson_num_top,
                        ((position - 8) / 8 * 2 - 1).toString())
                    setTextViewText(R.id.lesson_num_bottom, ((position - 8) / 8 * 2).toString())
                }
            else if (position == 48) //显示晚上
                RemoteViews(mContext.packageName,
                    R.layout.widget_grid_view_item_lesson_num_night)
            else RemoteViews(mContext.packageName,
                R.layout.widget_grid_view_item_lesson_num).apply {
                setTextViewText(R.id.lesson_num_top, ((position - 16) / 8 * 2 - 1).toString())
                setTextViewText(R.id.lesson_num_bottom, ((position - 16) / 8 * 2).toString())
            }
        }

        /**生成同学课表对应的RemoteView
         * */
        private fun getOtherLessonRV(mPosition: Int, position: Int): RemoteViews {
            val remoteViews =
                RemoteViews(mContext.packageName, R.layout.widget_grid_view_item_other_course)
            val lesson = otherLessons[mPosition / 8][mPosition % 8]
            remoteViews.setTextViewText(R.id.top, lesson!!.course)
            remoteViews.setTextViewText(R.id.bottom,
                ClassRoomParse.parseClassRoom(lesson.classroom))
            decoratedLessonWithAffair(remoteViews, mPosition)
            remoteViews.setOnClickFillInIntent(R.id.background,
                Intent().putExtra(CLICK_LESSON, gson.toJson(lesson)))
            return remoteViews
        }

        override fun onCreate() {
            /**初始化GridView的数据,设计数据库存储，所以开了个线程*/
            thread { mScheduleLessons = makeSchedules() }
        }

        override fun getCount(): Int { // 返回“集合视图”中的数据的总数
            return 8 * 9
        }

        override fun getItemId(position: Int): Long { // 返回当前项在“集合视图”中的位置
            return position.toLong()
        }

        override fun getLoadingView() =
            RemoteViews(mContext.packageName, R.layout.widget_grid_view_item_blank)

        override fun getViewTypeCount() = 30

        override fun hasStableIds() = true

        override fun onDataSetChanged() {
            thread { mScheduleLessons = makeSchedules() }
        }

        override fun onDestroy() {}
    }
}