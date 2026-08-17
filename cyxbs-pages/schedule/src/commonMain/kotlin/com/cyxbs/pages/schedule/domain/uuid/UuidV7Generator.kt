@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.cyxbs.pages.schedule.domain.uuid

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** 注入 [UuidV7Generator] 的毫秒时钟，用于确定性测试并隔离平台时间 API。 */
fun interface UuidV7Clock {
  /** 返回 Unix 纪元毫秒；UUIDv7 时间戳只能容纳非负值的低 48 位。 */
  fun nowEpochMilliseconds(): Long
}

/** 提供 UUIDv7 使用的 74 位随机载荷。 */
fun interface UuidV7Random {
  /** 返回随机 `(randA, randB)`；生成器会分别截取为 12 位与 62 位。 */
  fun nextBits(): UuidV7RandomBits
}

/** RFC 9562 5.7 节定义的随机字段，尚未插入版本位与变体位。 */
data class UuidV7RandomBits(val randA: Int, val randB: ULong)

/** 基于 Kotlin 多平台时钟 API 的生产时钟。 */
object SystemUuidV7Clock : UuidV7Clock {
  override fun nowEpochMilliseconds(): Long = Clock.System.now().toEpochMilliseconds()
}

/**
 * 基于 [Uuid.random] 的生产随机源。
 *
 * UUIDv4 自带的版本位和变体位会被丢弃，其余随机材料仍来自 Kotlin 平台安全实现，再重排为 UUIDv7
 * 字段；这样不会把 v4 固定位误当随机数，也不会污染 v7 的位布局。
 */
object KotlinUuidV7Random : UuidV7Random {
  override fun nextBits(): UuidV7RandomBits = Uuid.random().toULongs { high, low ->
    UuidV7RandomBits(randA = (high and 0xFFFu).toInt(), randB = low and RAND_B_MASK)
  }
}

/**
 * 协程安全的 RFC 9562 UUIDv7 生成器。
 *
 * UUID 排序依赖共享逻辑时钟，因此调用必须串行化。同一毫秒内递增 74 位随机载荷，使结果严格单调且
 * 唯一；系统时钟回拨时保留上一逻辑毫秒。若载荷全部溢出，则推进逻辑时间一毫秒，而不是阻塞协程等待
 * 墙上时钟，以维持并发调用的活性和排序保证。
 */
class UuidV7Generator(
  private val clock: UuidV7Clock = SystemUuidV7Clock,
  private val random: UuidV7Random = KotlinUuidV7Random,
) {
  private val mutex = Mutex()
  private var lastTimestamp = -1L
  private var randA = 0
  private var randB = 0uL

  /** 生成小写规范 UUIDv7；该调用会更新生成器的单调状态。 */
  suspend fun nextString(): String = mutex.withLock {
    val wallTimestamp = clock.nowEpochMilliseconds()
    require(wallTimestamp in 0..MAX_TIMESTAMP) { "UUIDv7 timestamp must fit unsigned 48 bits" }

    if (wallTimestamp > lastTimestamp) {
      lastTimestamp = wallTimestamp
      reseed()
    } else {
      incrementMonotonicPayload()
    }
    format(lastTimestamp, randA, randB)
  }

  /** 初始化两个随机字段并掩码，确保随机位不会泄漏到版本位或变体位。 */
  private fun reseed() {
    val bits = random.nextBits()
    randA = bits.randA and 0xFFF
    randB = bits.randB and RAND_B_MASK
  }

  /** 递增载荷：先在 randB 内进位，再进位至 randA，全部溢出时推进逻辑时间。 */
  private fun incrementMonotonicPayload() {
    if (randB < RAND_B_MASK) {
      randB++
    } else if (randA < 0xFFF) {
      randA++
      randB = 0uL
    } else {
      check(lastTimestamp < MAX_TIMESTAMP) { "UUIDv7 timestamp and monotonic payload exhausted" }
      lastTimestamp++
      randA = 0
      randB = 0uL
    }
  }

  /** 按网络字节序放置 48 位时间戳、版本 7、RFC 变体及随机字段。 */
  private fun format(timestamp: Long, randA: Int, randB: ULong): String {
    val high = (timestamp.toULong() shl 16) or (0x7uL shl 12) or randA.toULong()
    val low = (0x2uL shl 62) or randB
    return Uuid.fromULongs(high, low).toString()
  }

  private companion object {
    const val MAX_TIMESTAMP: Long = 0xFFFF_FFFF_FFFFL
    const val RAND_B_MASK: ULong = 0x3FFF_FFFF_FFFF_FFFFuL
  }
}

private const val RAND_B_MASK: ULong = 0x3FFF_FFFF_FFFF_FFFFuL
