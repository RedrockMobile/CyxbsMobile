package com.cyxbs.pages.map.model

import com.cyxbs.pages.map.util.MapImageDownloadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MapImageLoaderTest {
  @Test
  fun cachedImageWithMatchingVersionReturnsCachedBytesWithoutDownloading() = runTest {
    val cachedBytes = byteArrayOf(1, 2, 3)
    var downloadCount = 0
    val loader = MapImageLoader(
      readCachedImage = { cachedBytes },
      readCachedVersion = { 7L },
      downloadImage = { _, _ ->
        downloadCount++
        error("download should not run")
      },
    )

    val result = loader.load(MapImageLoadRequest("https://example.com/map.png", 7L, false), {}, {})

    val success = assertIs<MapImageLoadResult.Success>(result)
    assertContentEquals(cachedBytes, success.bytes)
    assertEquals(false, success.updateAvailable)
    assertEquals(0, downloadCount)
  }

  @Test
  fun cachedImageWithDifferentVersionReturnsCachedBytesAndSignalsUpdate() = runTest {
    val cachedBytes = byteArrayOf(4, 5, 6)
    var downloadCount = 0
    val loader = MapImageLoader(
      readCachedImage = { cachedBytes },
      readCachedVersion = { 7L },
      downloadImage = { _, _ ->
        downloadCount++
        error("download should not run")
      },
    )

    val result = loader.load(MapImageLoadRequest("https://example.com/map.png", 8L, false), {}, {})

    val success = assertIs<MapImageLoadResult.Success>(result)
    assertContentEquals(cachedBytes, success.bytes)
    assertEquals(true, success.updateAvailable)
    assertEquals(0, downloadCount)
  }

  @Test
  fun missingCachedImageDownloadsAndSavesVersionWhenImageIsCached() = runTest {
    val downloadedBytes = byteArrayOf(7, 8, 9)
    val progress = mutableListOf<Float>()
    var downloadStarted = 0
    var savedVersion: Long? = null
    val loader = MapImageLoader(
      readCachedImage = { null },
      readCachedVersion = { 2L },
      downloadImage = { _, listener ->
        listener(50L, 200L)
        MapImageDownloadResult(downloadedBytes, isCached = true)
      },
      saveVersion = { savedVersion = it },
    )

    val result = loader.load(
      request = MapImageLoadRequest("https://example.com/map.png", 3L, false),
      onDownloadStart = { downloadStarted++ },
      onProgress = { progress += it },
    )

    val success = assertIs<MapImageLoadResult.Success>(result)
    assertContentEquals(downloadedBytes, success.bytes)
    assertEquals(false, success.updateAvailable)
    assertEquals(1, downloadStarted)
    assertEquals(listOf(0.25f), progress)
    assertEquals(3L, savedVersion)
  }

  @Test
  fun missingCachedVersionDownloadsEvenWhenImageExists() = runTest {
    var downloadCount = 0
    val loader = MapImageLoader(
      readCachedImage = { byteArrayOf(1) },
      readCachedVersion = { null },
      downloadImage = { _, _ ->
        downloadCount++
        MapImageDownloadResult(byteArrayOf(2), isCached = true)
      },
    )

    val result = loader.load(MapImageLoadRequest("https://example.com/map.png", 3L, false), {}, {})

    val success = assertIs<MapImageLoadResult.Success>(result)
    assertContentEquals(byteArrayOf(2), success.bytes)
    assertEquals(1, downloadCount)
  }

  @Test
  fun forceDownloadIgnoresAvailableCache() = runTest {
    var downloadCount = 0
    val loader = MapImageLoader(
      readCachedImage = { byteArrayOf(1) },
      readCachedVersion = { 3L },
      downloadImage = { _, _ ->
        downloadCount++
        MapImageDownloadResult(byteArrayOf(2), isCached = true)
      },
    )

    val result = loader.load(MapImageLoadRequest("https://example.com/map.png", 3L, true), {}, {})

    val success = assertIs<MapImageLoadResult.Success>(result)
    assertContentEquals(byteArrayOf(2), success.bytes)
    assertEquals(1, downloadCount)
  }

  @Test
  fun downloadedImageWithoutCacheDoesNotSaveVersion() = runTest {
    var savedVersion: Long? = null
    val loader = MapImageLoader(
      readCachedImage = { null },
      readCachedVersion = { null },
      downloadImage = { _, _ -> MapImageDownloadResult(byteArrayOf(2), isCached = false) },
      saveVersion = { savedVersion = it },
    )

    loader.load(MapImageLoadRequest("https://example.com/map.png", 3L, false), {}, {})

    assertEquals(null, savedVersion)
  }

  @Test
  fun downloadExceptionReturnsFailure() = runTest {
    val cause = IllegalStateException("network unavailable")
    val loader = MapImageLoader(
      readCachedImage = { null },
      readCachedVersion = { null },
      downloadImage = { _, _ -> throw cause },
    )

    val result = loader.load(MapImageLoadRequest("https://example.com/map.png", 3L, false), {}, {})

    val failure = assertIs<MapImageLoadResult.Failure>(result)
    assertEquals(cause, failure.cause)
  }

  @Test
  fun downloadCancellationIsRethrown() = runTest {
    val cancellation = CancellationException("cancelled")
    val loader = MapImageLoader(
      readCachedImage = { null },
      readCachedVersion = { null },
      downloadImage = { _, _ -> throw cancellation },
    )

    val thrown = assertFailsWith<CancellationException> {
      loader.load(MapImageLoadRequest("https://example.com/map.png", 3L, false), {}, {})
    }

    assertEquals(cancellation, thrown)
  }
}
