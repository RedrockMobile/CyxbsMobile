package com.cyxbs.pages.volunteer.widget

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import com.cyxbs.pages.volunteer.R
import com.cyxbs.pages.volunteer.bean.VolunteerAffair
import com.cyxbs.pages.volunteer.utils.DateUtils.stamp2Date
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.cyxbs.components.view.text.MarqueeTextView

/**
 * Created by yyfbe, Date on 2020/9/5.
 */
class VolunteerAffairBottomSheetDialog(
    context: Context
) : BottomSheetDialog(context, com.cyxbs.components.config.R.style.ConfigBottomSheetDialogTheme) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.volunteer_layout_volunteer_affair_detail)
    }

    fun refresh(volunteerAffair: VolunteerAffair) {
        findViewById<MarqueeTextView>(R.id.tv_volunteer_affair_detail_title)?.text =
            volunteerAffair.name
        findViewById<TextView>(R.id.tv_volunteer_affair_detail_description)?.text =
            volunteerAffair.description
        findViewById<TextView>(R.id.tv_volunteer_affair_detail_sign_up_end_time)?.text =
            stamp2Date(volunteerAffair.lastDate)
        findViewById<TextView>(R.id.tv_volunteer_affair_detail_service_time)?.text =
            stamp2Date(volunteerAffair.date)
        findViewById<TextView>(R.id.tv_volunteer_affair_detail_time_value)?.text =
            volunteerAffair.hour
    }
}