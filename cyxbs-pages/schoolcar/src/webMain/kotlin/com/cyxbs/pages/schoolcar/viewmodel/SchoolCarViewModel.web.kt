package com.cyxbs.pages.schoolcar.viewmodel

import com.cyxbs.pages.schoolcar.bean.CarStation

actual class SchoolCarViewModel : CommonSchoolCarViewModel() {
	actual override fun getClosedSite(): CarStation? {
		return null
	}
}