package com.cyxbs.pages.map.ui

import com.cyxbs.components.navigation.NAV_MAP
import com.cyxbs.components.navigation.NAV_MAP_PLACE_DETAIL
import com.cyxbs.pages.map.api.MapNavArgument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MapNavEntryTest {
  @Test
  fun mapAndDetailEntriesKeepIndependentStableContentKeys() {
    val mapEntry = MapNavEntry()
    val detailEntry = PlaceDetailNavEntry()

    assertEquals(NAV_MAP, mapEntry.getContentKey(MapNavArgument()))
    assertEquals(NAV_MAP, mapEntry.getContentKey(MapNavArgument("二教")))
    assertEquals(NAV_MAP_PLACE_DETAIL, detailEntry.getContentKey(PlaceDetailNavArgument("1")))
    assertEquals(
      NAV_MAP_PLACE_DETAIL,
      detailEntry.getContentKey(PlaceDetailNavArgument("2", expanded = true)),
    )
    assertNotEquals(NAV_MAP, NAV_MAP_PLACE_DETAIL)
  }
}
