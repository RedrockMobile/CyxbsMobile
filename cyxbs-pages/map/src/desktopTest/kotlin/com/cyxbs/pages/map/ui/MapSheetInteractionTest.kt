package com.cyxbs.pages.map.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.compose.theme.AppColor
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetCompose
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetState
import com.cyxbs.components.view.ui.bottomsheet.BottomSheetAnchor
import com.cyxbs.components.view.ui.bottomsheet.LocalBottomSheetScope
import com.cyxbs.pages.map.widget.PlaceDetailSheetFrame
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MapSheetInteractionTest {
  @get:Rule
  val compose = createComposeRule()

  private val sheetState = BottomSheetState(hideable = true, requestDismissOnDrag = false)
  private val contentHeight = mutableStateOf<Int?>(180)
  private var mapClicks = 0

  private fun showSheet() {
    compose.setContent {
      CompositionLocalProvider(LocalAppColors provides AppColor()) {
        Box(Modifier.requiredSize(900.dp, 600.dp)) {
          Box(Modifier.fillMaxSize().testTag("map").clickable { mapClicks++ })
          BottomSheetCompose(
            bottomSheetState = sheetState,
            modifier = mapSheetModifier(),
            peekHeight = 112.dp,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            scrimColor = Color.Transparent,
          ) {
            CompositionLocalProvider(LocalBottomSheetScope provides this) {
              PlaceDetailSheetFrame {
                Box(Modifier.fillMaxSize().testTag("sheet")) {
                  val height = contentHeight.value
                  if (height == null) Text("正在加载地点信息…")
                  else Box(Modifier.height(height.dp))
                }
              }
            }
          }
        }
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun expandedAndCollapsedSheetsOnlyBlockClicksInsideTheCard() {
    showSheet()
    compose.runOnIdle { sheetState.expandAsync() }
    compose.waitForIdle()
    compose.runOnIdle { assertTrue(sheetState.isSettledAt(BottomSheetAnchor.Expanded)) }

    val map = compose.onNodeWithTag("map")
    val mapBounds = map.fetchSemanticsNode().boundsInRoot
    val card = compose.onNodeWithTag("sheet", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue(card.right < mapBounds.right * 0.75f)
    val outside = Offset(mapBounds.width * 0.9f, mapBounds.height * 0.9f)
    map.performMouseInput { click(outside) }
    compose.runOnIdle { assertEquals(1, mapClicks) }

    val inside = card.center - mapBounds.topLeft
    map.performMouseInput { click(inside) }
    compose.runOnIdle { assertEquals(1, mapClicks) }

    compose.runOnIdle { sheetState.collapseAsync() }
    compose.waitForIdle()
    map.performMouseInput { click(outside) }
    compose.runOnIdle { assertEquals(2, mapClicks) }
  }

  @Test
  fun loadingAndChangingDetailsKeepExpandedBounds() {
    showSheet()
    compose.runOnIdle { sheetState.expandAsync() }
    compose.waitForIdle()
    val sheet = compose.onNodeWithTag("sheet", useUnmergedTree = true)
    val expandedBounds = sheet.fetchSemanticsNode().boundsInRoot

    for (height in listOf(null, 450, 120)) {
      compose.runOnIdle { contentHeight.value = height }
      compose.waitForIdle()
      assertEquals(expandedBounds, sheet.fetchSemanticsNode().boundsInRoot)
      compose.runOnIdle { assertTrue(sheetState.isSettledAt(BottomSheetAnchor.Expanded)) }
    }
  }
}
