package com.cyxbs.pages.volunteer.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.volunteer.R
import com.mredrock.cyxbs.common.ui.BaseFeedFragment

class VolunteerFeedUnbindAdapter : BaseFeedFragment.Adapter() {
    override fun onCreateView(context: Context, parent: ViewGroup): View =
        LayoutInflater.from(context)
            .inflate(R.layout.volunteer_discover_feed_unbound, parent, false).apply {
            if (!IAccountService::class.impl().isLogin()) {
                findViewById<AppCompatTextView>(R.id.tv_volunteer_no_account).text =
                    context.getString(R.string.volunteer_ask_login_string)
            }
        }

    fun refresh(text: String) {
        view?.findViewById<AppCompatTextView>(R.id.tv_volunteer_no_account)?.text = text
    }


}