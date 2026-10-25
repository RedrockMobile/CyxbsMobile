package com.cyxbs.pages.map.viewmodel

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapNavEventTest {
  @Test
  fun eventsBeforeSubscriptionAreNotReplayed() = runTest {
    MapComposeViewModel.emitNavEvent(MapNavEvent.HidePlaceDetail)
    val received = mutableListOf<MapNavEvent>()
    backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
      MapComposeViewModel.navEvents.collect { received.add(it) }
    }
    assertTrue(received.isEmpty())
    assertTrue(MapComposeViewModel.navEvents.replayCache.isEmpty())
    MapComposeViewModel.emitNavEvent(MapNavEvent.CollapsePlaceDetail)
    assertEquals(listOf<MapNavEvent>(MapNavEvent.CollapsePlaceDetail), received)
  }

  @Test
  fun commandsReachAllActiveCollectorsInOrderWithoutDropping() = runTest {
    val first = mutableListOf<MapNavEvent>()
    val second = mutableListOf<MapNavEvent>()
    backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
      MapComposeViewModel.navEvents.collect { first.add(it) }
    }
    backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
      MapComposeViewModel.navEvents.collect { second.add(it) }
    }
    val events = listOf(
      MapNavEvent.ShowPlaceDetail("1", true),
      MapNavEvent.CollapseSearch,
      MapNavEvent.CollapsePlaceDetail,
      MapNavEvent.HidePlaceDetail,
      MapNavEvent.CollectionsChanged,
      MapNavEvent.RequestPhotoUpload("1"),
    )
    events.forEach { MapComposeViewModel.emitNavEvent(it) }
    assertEquals(events, first)
    assertEquals(events, second)
  }

  @Test
  fun cancelledCollectorDoesNotReceiveFurtherEvents() = runTest {
    val received = mutableListOf<MapNavEvent>()
    val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
      MapComposeViewModel.navEvents.collect { received.add(it) }
    }
    MapComposeViewModel.emitNavEvent(MapNavEvent.CollapseSearch)
    collector.cancel()
    collector.join()
    MapComposeViewModel.emitNavEvent(MapNavEvent.HidePlaceDetail)
    assertEquals(listOf<MapNavEvent>(MapNavEvent.CollapseSearch), received)
    assertTrue(MapComposeViewModel.navEvents.replayCache.isEmpty())
  }
}
