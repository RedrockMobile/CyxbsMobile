package com.cyxbs.pages.discover.home.functions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.painter.Painter
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.api.FindCourseNavArgument
import com.cyxbs.pages.emptyroom.api.EmptyRoomNavArgument
import com.cyxbs.pages.map.api.MapNavArgument
import com.cyxbs.pages.schoolcar.api.SchoolCarNavArgument
import cyxbsmobile.cyxbs_pages.discover.generated.resources.Res
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_ic_bus_track
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_ic_empty_classroom
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_ic_map
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_ic_more_function
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_ic_my_exam
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_ic_no_class
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_ic_other_course
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_ic_school_calendar
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_ic_sport
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_ic_todo
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_more_function_notice_text
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_bus_track
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_empty_classroom
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_map
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_more_function
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_my_exam
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_no_class
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_other_course
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_school_calendar
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_sport
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_title_todo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * .
 *
 * @author 985892345
 * @date 2026/6/15
 */
expect object PlatformDiscoverFunctions : DiscoverFunctions

abstract class DiscoverFunctions {
  /** 功能按钮列表（id 用于持久化顺序，调整后不要随便改） */
  @Composable
  fun rememberFunctions(): List<DiscoverFunctionItem> {
    val moreFunctionToast = stringResource(Res.string.discover_more_function_notice_text)
    return listOf(
      DiscoverFunctionItem(
        id = "other_course",
        title = stringResource(Res.string.discover_title_other_course),
        painter = painterResource(Res.drawable.discover_ic_other_course),
        onClick = { clickFindCourse() },
      ),
      DiscoverFunctionItem(
        id = "map",
        title = stringResource(Res.string.discover_title_map),
        painter = painterResource(Res.drawable.discover_ic_map),
        onClick = { clickMap() },
      ),
      DiscoverFunctionItem(
        id = "no_class",
        title = stringResource(Res.string.discover_title_no_class),
        painter = painterResource(Res.drawable.discover_ic_no_class),
        loginPrompt = stringResource(Res.string.discover_title_no_class),
        onClick = { clickNoClass() },
      ),
      DiscoverFunctionItem(
        id = "bus_track",
        title = stringResource(Res.string.discover_title_bus_track),
        painter = painterResource(Res.drawable.discover_ic_bus_track),
        onClick = { clickSchoolCar() },
      ),
      DiscoverFunctionItem(
        id = "empty_classroom",
        title = stringResource(Res.string.discover_title_empty_classroom),
        painter = painterResource(Res.drawable.discover_ic_empty_classroom),
        onClick = { clickEmptyRoom() },
      ),
      DiscoverFunctionItem(
        id = "school_calendar",
        title = stringResource(Res.string.discover_title_school_calendar),
        painter = painterResource(Res.drawable.discover_ic_school_calendar),
        onClick = { clickSchoolCalendar() },
      ),
      DiscoverFunctionItem(
        id = "todo",
        title = stringResource(Res.string.discover_title_todo),
        painter = painterResource(Res.drawable.discover_ic_todo),
        onClick = { clickTodo() },
      ),
      DiscoverFunctionItem(
        id = "sport",
        title = stringResource(Res.string.discover_title_sport),
        painter = painterResource(Res.drawable.discover_ic_sport),
        loginPrompt = stringResource(Res.string.discover_title_sport),
        onClick = { clickSport() },
      ),
      DiscoverFunctionItem(
        id = "my_exam",
        title = stringResource(Res.string.discover_title_my_exam),
        painter = painterResource(Res.drawable.discover_ic_my_exam),
        loginPrompt = stringResource(Res.string.discover_title_my_exam),
        onClick = { clickExam() },
      ),
      DiscoverFunctionItem(
        id = "more_function",
        title = stringResource(Res.string.discover_title_more_function),
        painter = painterResource(Res.drawable.discover_ic_more_function),
        onClick = {
          toast(moreFunctionToast)
        },
      ),
    )
  }

  open fun clickFindCourse() {
    FindCourseNavArgument().navigate()
  }

  open fun clickMap() {
    MapNavArgument().navigate()
  }

  open fun clickNoClass() {
    toast("该平台未实现")
  }

  open fun clickSchoolCar() {
    SchoolCarNavArgument.navigate()
  }

  open fun clickEmptyRoom() {
    EmptyRoomNavArgument.navigate()
  }

  open fun clickSchoolCalendar() {
    toast("该平台未实现")
  }

  open fun clickTodo() {
    toast("该平台未实现")
  }

  open fun clickSport() {
    toast("该平台未实现")
  }

  open fun clickExam() {
    toast("该平台未实现")
  }
}

/**
 * 功能区一个按钮（横向滚动条目）
 */
@Stable
class DiscoverFunctionItem(
  /** 稳定 id，用于本地持久化顺序，新增按钮不要复用旧 id */
  val id: String,
  /** 标题（按钮下方文字） */
  val title: String,
  /** 图标 */
  val painter: Painter,
  /**
   * 点击行为。若 [loginPrompt] 非 null，调用前 commonMain 会先弹登录态对话框。
   */
  val onClick: () -> Unit,
  /**
   * 需要登录时弹出的「请登录解锁 {xxx}」文案；为 null 表示不需要登录态拦截。
   */
  val loginPrompt: String? = null,
)