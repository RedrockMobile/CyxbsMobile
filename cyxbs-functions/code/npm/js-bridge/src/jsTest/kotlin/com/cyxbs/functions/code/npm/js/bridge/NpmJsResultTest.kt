package com.cyxbs.functions.code.npm.js.bridge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 验证自定义结果、跨 Runtime 信封与 Kotlin Result 的互转边界。 */
class NpmJsResultTest {

  /** Kotlin Result 与协议结果互转时应保留成功值和本地异常实例。 */
  @Test
  fun convertsToAndFromKotlinResult() {
    val success = Result.success(42).toNpmJsResult()
    val expectedFailure = IllegalStateException("local failure")
    val failure = Result.failure<Int>(expectedFailure).toNpmJsResult()

    assertEquals(42, success.toResult().getOrThrow())
    assertTrue(failure.toResult().isFailure)
    assertEquals(expectedFailure, failure.toResult().exceptionOrNull())
  }

  /** 失败分支允许返回 null，不能因此再次进入成功分支并抛出原异常。 */
  @Test
  fun foldKeepsNullableFailureResult() {
    val folded: String? = NpmJsResult.failure<Unit>(IllegalStateException("failed")).fold(
      onSuccess = { "success" },
      onFailure = { null },
    )

    assertEquals(null, folded)
  }

  /** 成功值和远端失败都应通过稳定信封往返，不能直接序列化 Throwable。 */
  @Test
  fun resultEnvelopeRoundTripsSuccessAndFailure() {
    val success = decodeNpmJsResult(
      envelopeJson = encodeNpmJsResult(NpmJsResult.success(42), ::JsonPrimitive),
      decodeValue = { element -> element.jsonPrimitive.int },
      failureFactory = { message -> NpmJsServiceInvocationException(message) },
    )
    val failure = decodeNpmJsResult<Int>(
      envelopeJson = encodeNpmJsResult<Int>(
        NpmJsResult.failure(IllegalStateException("remote failure")),
        ::JsonPrimitive,
      ),
      decodeValue = { element -> element.jsonPrimitive.int },
      failureFactory = { message -> NpmJsServiceInvocationException(message) },
    )

    assertEquals(42, success.getOrThrow())
    assertIs<NpmJsServiceInvocationException>(failure.exceptionOrNull())
    assertEquals("remote failure", failure.exceptionOrNull()?.message)
  }

  /** 普通实现异常必须进入 NpmJsResult.failure，供协议调用方显式处理。 */
  @Test
  fun catchesOrdinaryImplementationFailure() = runTest {
    val failure = npmJsCatching<Unit> {
      error("implementation failed")
    }

    assertTrue(failure.isFailure)
    assertEquals("implementation failed", failure.exceptionOrNull()?.message)
  }

  /** 本地协议异常可以映射为公开异常，而远端显式 NpmJsResult.failure 保持原有分类。 */
  @Test
  fun mapsOnlyThrownBoundaryFailure() = runTest {
    val mapped = npmJsResultCatching<Unit>(
      exceptionMapper = { throwable ->
        NpmJsServiceInvocationException("mapped", throwable)
      },
    ) {
      error("invalid envelope")
    }
    val explicit = npmJsResultCatching<Unit>(
      exceptionMapper = { throwable ->
        NpmJsServiceInvocationException("mapped", throwable)
      },
    ) {
      NpmJsResult.failure(NpmJsServiceMethodNotImplementedException("service", "newMethod"))
    }

    assertIs<NpmJsServiceInvocationException>(mapped.exceptionOrNull())
    assertIs<NpmJsServiceMethodNotImplementedException>(explicit.exceptionOrNull())
  }

  /** 成功值无法序列化时也必须返回失败信封，不能让编码异常越过 Runtime。 */
  @Test
  fun catchesSuccessValueEncodingFailure() = runTest {
    val envelope = encodeNpmJsResultCatching(
      encodeValue = { _: Int -> error("encoding failed") },
    ) {
      NpmJsResult.success(42)
    }
    val result = decodeNpmJsResult<Int>(
      envelopeJson = envelope,
      decodeValue = { element -> element.jsonPrimitive.int },
      failureFactory = { message -> NpmJsServiceInvocationException(message) },
    )

    assertEquals("encoding failed", result.exceptionOrNull()?.message)
  }

  /** 损坏的失败信封必须显式拒绝，不能伪造成没有原因的普通失败。 */
  @Test
  fun rejectsFailureEnvelopeWithoutMessage() {
    assertFailsWith<IllegalArgumentException> {
      decodeNpmJsResult<Unit>(
        envelopeJson = """{"isSuccess":false}""",
        decodeValue = { error("Failed envelope must not decode a success value.") },
        failureFactory = { message -> NpmJsServiceInvocationException(message) },
      )
    }
  }

  /** 协程取消既不能编码为远端失败，也不能由任一 catching helper 吞掉。 */
  @Test
  fun cancellationAlwaysEscapesResultBoundary() = runTest {
    assertFailsWith<CancellationException> {
      npmJsCatching<Unit> { throw CancellationException("cancel") }
    }
    assertFailsWith<CancellationException> {
      npmJsResultCatching<Unit> { throw CancellationException("cancel") }
    }
    assertFailsWith<CancellationException> {
      encodeNpmJsResult<Unit>(
        NpmJsResult.failure(CancellationException("cancel")),
        { JsonPrimitive("unit") },
      )
    }
    assertFailsWith<CancellationException> {
      encodeNpmJsResultCatching<Unit>(
        encodeValue = { JsonPrimitive("unit") },
      ) {
        throw CancellationException("cancel")
      }
    }
  }
}
