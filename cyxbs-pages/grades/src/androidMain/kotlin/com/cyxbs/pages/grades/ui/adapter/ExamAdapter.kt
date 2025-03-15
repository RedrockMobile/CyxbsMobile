package com.cyxbs.pages.grades.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.isDigitsOnly
import androidx.recyclerview.widget.RecyclerView
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.pages.grades.R
import com.cyxbs.pages.grades.bean.Exam
import com.cyxbs.pages.grades.widget.DashGapLine
import java.util.*

/**
 * @CreateBy: FxyMine4ever
 *
 * @CreateAt:2018/9/16
 */
class ExamAdapter(val data: MutableList<Exam>) : RecyclerView.Adapter<ExamAdapter.ExamVH>() {
    
    sealed class ExamVH(itemView: View) : RecyclerView.ViewHolder(itemView)
    
    class HeadVH(itemView: View) : ExamVH(itemView)
    
    class OtherVH(itemView: View) : ExamVH(itemView) {
        val mTvExamMonth: TextView = itemView.findViewById(R.id.tv_exam_month)
        val mTvExamLocation:TextView = itemView.findViewById(R.id.tv_exam_location)
        val mTvGradesDistance:TextView = itemView.findViewById(R.id.grades_distance)
        val mTvExamKind:TextView = itemView.findViewById(R.id.tv_exam_kind)
        val mTvExamDayOfWeek:TextView = itemView.findViewById(R.id.tv_exam_day_of_week)
        val mTvExamDayOfMonth:TextView = itemView.findViewById(R.id.tv_exam_day_of_month)
        val mTvExamName:TextView = itemView.findViewById(R.id.tv_exam_name)
        val mTvExamLocationNumber:TextView = itemView.findViewById(R.id.tv_exam_location_number)
        val mTvExamTime:TextView = itemView.findViewById(R.id.tv_exam_time)
        val mIvExamCircle:DashGapLine = itemView.findViewById(R.id.iv_exam_circle)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamVH {
        return when (viewType) {
            HeadVH::class.hashCode() -> HeadVH(
                LayoutInflater.from(parent.context).inflate(R.layout.grades_item_head, parent, false)
            )
            OtherVH::class.hashCode() -> OtherVH(
                LayoutInflater.from(parent.context).inflate(R.layout.grades_item_exam, parent, false)
            )
            else -> error("")
        }
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (position == 0) HeadVH::class.hashCode() else OtherVH::class.hashCode()
    }
    
    override fun onBindViewHolder(holder: ExamVH, position: Int) {
        val context = holder.itemView.context
        if (holder is OtherVH) {
            data[position-1].let { it ->
                it.let {
                    if (it.week?.toInt() != 0) {
                        val drawableTime = ResourcesCompat.getDrawable(context.resources, R.drawable.grades_time, null)
                        drawableTime?.setBounds(0, 0, 30, 30)
                        holder.mTvExamMonth.setCompoundDrawables(drawableTime, null, null, null)
                        
                        val drawableLocation = ResourcesCompat.getDrawable(context.resources, R.drawable.grades_exam_location, null)
                        drawableLocation?.setBounds(0, 0, 30, 30)
                        holder.mTvExamLocation.setCompoundDrawables(drawableLocation, null, null, null)
                        var schoolCalendar: Calendar? = null
                        it.week?.let { week ->
                            it.weekday?.let { weekday ->
                                schoolCalendar = SchoolCalendar.getFirstMonDayTimestamp()?.let {
                                    Calendar.getInstance().apply { timeInMillis = it }
                                }?.also {
                                    it.add(Calendar.DATE, (week.toInt() - 1) * 7)
                                    it.add(Calendar.DATE, weekday.toInt() - 1 - (it.get(Calendar.DAY_OF_WEEK) + 5) % 7)
                                }
                            }
                        }
                        val isSuccess = ExamDataHelper.tryModifyData(it)
                        if (isSuccess) {
                            val distance = getDay(it.date)
                            
                            when {
                                distance == 0 -> {
                                    holder.mTvGradesDistance.text = "今天考试"
                                    holder.mTvGradesDistance.setTextColor(Color.parseColor("#3A39D3"))
                                }
                                distance > 0 -> {
                                    holder.mTvGradesDistance.text = "还剩${distance}天考试"
                                    holder.mTvGradesDistance.setTextColor(Color.parseColor("#3A39D3"))
                                }
                                distance == -1 -> {
                                    holder.mTvGradesDistance.text = "考试已结束"
                                    holder.mTvGradesDistance.setTextColor(Color.parseColor("#2A4E84"))
                                }
                                distance == -2 -> {
                                    holder.mTvGradesDistance.text = "暂时无法获取到考试天数"
                                    holder.mTvGradesDistance.setTextColor(Color.parseColor("#2A4E84"))
                                }
                            }
                            holder.mTvExamKind.text = it.type ?: ""
                            it.week?.let { week ->
                                holder.mTvExamDayOfWeek.text = String.format("%s周周%s", getChineseWeek(week.toInt() - 1), it.chineseWeekday
                                    ?: "--")
                            }
                            holder.mTvExamDayOfMonth.text = String.format("%s" + "号", schoolCalendar?.get(Calendar.DATE)
                                ?: "--")
                            holder.mTvExamMonth.text = String.format("%s月", schoolCalendar?.get(Calendar.MONTH)?.plus(1)
                                ?: "--")
                        } else {
                            holder.mTvExamDayOfWeek.text = String.format("%s周周%s", "-", "-")
                            holder.mTvExamDayOfMonth.text = String.format("%s" + "号", "-")
                            holder.mTvExamMonth.text = String.format("%s月", "-")
                        }
                    }
                    
                    holder.mTvExamName.text = it.course
                    var seat = it.seat ?: "--"
                    
                    if (seat.length < 2) seat = "0$seat"
                    seat = "${seat}号"
                    
                    holder.mTvExamLocation.text = "${it.classroom}场"
                    holder.mTvExamLocationNumber.text = seat
                    
                    if (it.begin_time != null && it.end_time != null) {
                        holder.mTvExamTime.text = String.format("%s - %s", it.begin_time, it.end_time)
                    } else {
                        holder.mTvExamTime.text = it.time
                    }
                }
            }
            
            //要去除header的占位
            if (position - 1 == data.size - 1) {
                holder.mIvExamCircle.setLineVisible(false)
            } else {
                holder.mIvExamCircle.setLineVisible(true)
            }
        }
    }
    
    /**
     * 需要做一个防止数组越界的处理
     */
    private fun getChineseWeek(week: Int): String {
        val array = listOf("第一", "第二", "第三", "第四", "第五", "第六", "第七", "第八",
                           "第九", "第十", "十一", "十二", "十三", "十四", "十五",
                           "十六", "十七", "十八", "十九", "二十", "二十一",
                           "二十二", "二十三", "二十四", "二十五", "二十六", "二十七")
        if (week in 0..26) {
            return array[week]
        } else {
            return "第--周"
        }
        
    }
    
    
    private object ExamDataHelper {
        private val numArray = arrayOf("1", "2", "3", "4", "5", "6", "7")
        private val numChineseArray = arrayOf("一", "二", "三", "四", "五", "六", "日")
        
        fun tryModifyData(exam: Exam): Boolean {
            if (exam.weekday.equals("0") || exam.week.equals("0")) {
                return false
            } else {
                toChineseNum(exam)
            }
            return true
        }
        
        private fun toChineseNum(exam: Exam) {
            for (i in numArray.indices) {
                if (exam.weekday.equals(numArray[i])) {
                    // 这里不应该改变原有的值
                    //it.weekday = numChineseArray[i];
                    exam.chineseWeekday = numChineseArray[i]
                }
            }
        }
    }
    
    //传入一个2019-11-24格式的时间
    private fun getDay(time: String?): Int {
        time?.let { time ->
            val now = Date()
            val examDate = Calendar.getInstance()
            
            val array = time.split("-")//分割为2019，11，24
            //由于后端补考接口date字段可能会返回"月日"，因此需要做个处理防止NumberFormatException
            for (item in array) {
                if (!item.isDigitsOnly()) {
                    return -2
                }
            }
            examDate.set(array[0].toInt(), array[1].toInt() - 1, array[2].toInt())
            val diff = examDate.time.time - now.time
            val day = 1000 * 24 * 60 * 60
            return if (diff >= 0) {
                (diff / day).toInt()
            } else {
                -1//-1表示考试已结束
            }
        }
        return -2//-2表示time非法
    }
    
    override fun getItemCount(): Int {
        return data.size + 1 // 加一是为了头布局
    }
}