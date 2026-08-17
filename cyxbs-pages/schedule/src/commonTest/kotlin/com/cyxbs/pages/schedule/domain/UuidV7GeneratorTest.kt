package com.cyxbs.pages.schedule.domain

import com.cyxbs.pages.schedule.domain.uuid.UuidV7Clock
import com.cyxbs.pages.schedule.domain.uuid.UuidV7Generator
import com.cyxbs.pages.schedule.domain.uuid.UuidV7Random
import com.cyxbs.pages.schedule.domain.uuid.UuidV7RandomBits
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UuidV7GeneratorTest {
  @Test
  fun formatContainsVersionVariantAndTimestamp() = runTest {
    val timestamp = 1_721_000_123_456L
    val value = UuidV7Generator(
      clock = UuidV7Clock { timestamp },
      random = UuidV7Random { UuidV7RandomBits(0xABC, 0x1234uL) },
    ).nextString()

    assertTrue(CANONICAL.matches(value), value)
    assertEquals('7', value[14])
    assertTrue(value[19] in "89ab")
    assertEquals(timestamp, timestampOf(value))
  }

  @Test
  fun sameMillisecondIsStrictlyMonotonic() = runTest {
    val generator = generator(clockValues = listOf(100L, 100L, 100L))
    val values = List(3) { generator.nextString() }
    assertEquals(3, values.toSet().size)
    assertEquals(values.sorted(), values)
  }

  @Test
  fun clockRollbackRetainsLogicalTimestampAndMonotonicOrder() = runTest {
    val generator = generator(clockValues = listOf(100L, 99L, 50L))
    val values = List(3) { generator.nextString() }
    assertEquals(listOf(100L, 100L, 100L), values.map(::timestampOf))
    assertEquals(values.sorted(), values)
  }

  @Test
  fun payloadOverflowAdvancesLogicalMillisecond() = runTest {
    val generator = UuidV7Generator(
      clock = UuidV7Clock { 100L },
      random = UuidV7Random { UuidV7RandomBits(0xFFF, 0x3FFF_FFFF_FFFF_FFFFuL) },
    )
    val first = generator.nextString()
    val second = generator.nextString()
    assertEquals(100L, timestampOf(first))
    assertEquals(101L, timestampOf(second))
    assertTrue(first < second)
  }

  @Test
  fun concurrentGenerationIsUnique() = runTest {
    val generator = generator(clockValues = listOf(200L))
    val values = List(2_000) { async { generator.nextString() } }.awaitAll()
    assertEquals(values.size, values.toSet().size)
  }

  private fun generator(clockValues: List<Long>): UuidV7Generator {
    var index = 0
    return UuidV7Generator(
      clock = UuidV7Clock {
        clockValues[index.coerceAtMost(clockValues.lastIndex)].also { index++ }
      },
      random = UuidV7Random { UuidV7RandomBits(1, 1uL) },
    )
  }

  private fun timestampOf(value: String): Long = value.replace("-", "").take(12).toLong(16)

  private companion object {
    val CANONICAL = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
  }
}
