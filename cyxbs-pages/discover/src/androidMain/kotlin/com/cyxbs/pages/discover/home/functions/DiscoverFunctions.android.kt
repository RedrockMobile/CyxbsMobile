package com.cyxbs.pages.discover.home.functions

import com.cyxbs.components.config.route.DISCOVER_CALENDAR
import com.cyxbs.components.config.route.DISCOVER_GRADES
import com.cyxbs.components.config.route.DISCOVER_NO_CLASS
import com.cyxbs.components.config.route.DISCOVER_SPORT
import com.cyxbs.components.config.route.DISCOVER_TODO_MAIN
import com.cyxbs.components.config.service.startActivity
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.ClickEvent

actual object PlatformDiscoverFunctions : DiscoverFunctions() {

  override fun clickFindCourse() {
    super.clickFindCourse()
    TrackingUtils.trackClickEvent2(ClickEvent.CLICK_KBCX_ENTRY)
  }

  override fun clickMap() {
    super.clickMap()
    TrackingUtils.trackClickEvent2(ClickEvent.CLICK_CYDT_ENTRY)
  }

  override fun clickNoClass() {
    startActivity(DISCOVER_NO_CLASS)
    TrackingUtils.trackClickEvent2(ClickEvent.CLICK_MKY_ENTRY)
  }

  override fun clickSchoolCar() {
    super.clickSchoolCar()
    TrackingUtils.trackClickEvent2(ClickEvent.CLICK_XCGJ_ENTRY)
  }

  override fun clickEmptyRoom() {
    super.clickEmptyRoom()
    TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_KJS_ENTRY)
  }

  override fun clickSchoolCalendar() {
    startActivity(DISCOVER_CALENDAR)
    TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_XL_ENTRY)
  }

  override fun clickTodo() {
    startActivity(DISCOVER_TODO_MAIN)
    TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YZQD_ENTRY)
  }

  override fun clickSport() {
    startActivity(DISCOVER_SPORT)
    TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_TYDK_ENTRY)
  }

  override fun clickExam() {
    startActivity(DISCOVER_GRADES)
    TrackingUtils.trackClickEvent2(ClickEvent.CLICK_YLC_WDKS_ENTRY)
  }
}