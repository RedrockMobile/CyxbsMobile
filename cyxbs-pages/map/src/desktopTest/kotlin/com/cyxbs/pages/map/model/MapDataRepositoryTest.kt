package com.cyxbs.pages.map.model

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.pages.map.model.bean.MapInfo
import io.github.vinceglb.filekit.FileKit
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.BeforeClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapDataRepositoryTest {
  companion object {
    private val directory = Files.createTempDirectory("map-json-test").toFile()
    private val jsonDirectory = directory.resolve("json_settings/map")

    @JvmStatic
    @BeforeClass
    fun initializeStorage() {
      FileKit.init(filesDir = directory, cacheDir = directory.resolve("cache"))
    }

    @JvmStatic
    @AfterClass
    fun cleanUp() {
      directory.deleteRecursively()
    }
  }

  private val original = MapInfo(
    "校园地图", emptyList(), "https://example.com/map.jpg",
    1000, 1000, "#ffffff", 1L, "",
  )

  @BeforeTest
  fun resetStorage() {
    directory.listFiles()?.forEach { it.deleteRecursively() }
  }

  @Test
  fun largeMapInfoIsSavedAndReplacedAsJson() = runTest {
    assertNull(MapDataRepository.getMapInfo())
    val largeMap = original.copy(hotWord = "校园地图".repeat(10000))
    assertTrue(MapDataRepository.saveMapInfo(largeMap))
    val file = jsonDirectory.resolve("map_info.json")
    assertEquals(largeMap, defaultJson.decodeFromString<MapInfo>(file.readText()))

    val updated = original.copy(hotWord = "新地图", pictureVersion = 2L)
    assertTrue(MapDataRepository.saveMapInfo(updated))
    assertEquals(updated, MapDataRepository.getMapInfo())
    assertFalse(jsonDirectory.resolve("map_info.json.tmp").exists())
  }

  @Test
  fun failedWriteReturnsFalseAndCanRetry() = runTest {
    val blocker = directory.resolve("json_settings")
    blocker.writeText("阻止创建缓存目录")

    assertFalse(MapDataRepository.saveMapInfo(original))
    assertNull(MapDataRepository.getMapInfo())

    assertTrue(blocker.delete())
    assertTrue(MapDataRepository.saveMapInfo(original))
    assertEquals(original, MapDataRepository.getMapInfo())
  }

  @Test
  fun corruptedJsonReturnsNullAndCanBeReplaced() = runTest {
    jsonDirectory.mkdirs()
    jsonDirectory.resolve("map_info.json").writeText("broken json")
    assertNull(MapDataRepository.getMapInfo())

    assertTrue(MapDataRepository.saveMapInfo(original))
    assertEquals(original, MapDataRepository.getMapInfo())
  }
}
