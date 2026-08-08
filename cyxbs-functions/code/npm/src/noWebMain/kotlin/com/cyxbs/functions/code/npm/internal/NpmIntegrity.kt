package com.cyxbs.functions.code.npm.internal

import com.cyxbs.functions.code.npm.NpmResolutionException
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/** 已解析并校验格式的 Subresource Integrity。 */
internal class NpmIntegrity private constructor(
  val encoded: String,
  private val algorithm: Algorithm,
  private val expectedDigest: ByteString,
) {

  /** 校验实际 tarball 字节是否与 registry 元数据声明的摘要一致。 */
  fun matches(bytes: ByteArray): Boolean {
    val actual = when (algorithm) {
      Algorithm.SHA256 -> bytes.toByteString().sha256()
      Algorithm.SHA512 -> bytes.toByteString().sha512()
    }
    return actual == expectedDigest
  }

  internal companion object {

    /**
     * 解析 npm 的 SRI 文本。
     *
     * 当前只接受单个 SHA-256 或 SHA-512 token，避免多摘要降级和 SHA-1。npm registry 通常提供
     * SHA-512 integrity。
     */
    @Throws(NpmResolutionException::class)
    fun parse(value: String, packageName: String): NpmIntegrity {
      val token = value.trim()
      if (token.isEmpty() || token.any(Char::isWhitespace)) {
        throw NpmResolutionException("Package '$packageName' integrity must contain one SRI token.")
      }
      val separator = token.indexOf('-')
      if (separator <= 0 || separator == token.lastIndex) {
        throw NpmResolutionException("Package '$packageName' integrity is not a valid SRI token.")
      }
      val algorithm = when (token.substring(0, separator)) {
        "sha256" -> Algorithm.SHA256
        "sha512" -> Algorithm.SHA512
        else -> throw NpmResolutionException(
          "Package '$packageName' integrity must use SHA-256 or SHA-512.",
        )
      }
      val digest = token.substring(separator + 1).decodeBase64()
        ?: throw NpmResolutionException("Package '$packageName' integrity digest is not Base64.")
      val expectedSize = when (algorithm) {
        Algorithm.SHA256 -> 32
        Algorithm.SHA512 -> 64
      }
      if (digest.size != expectedSize) {
        throw NpmResolutionException(
          "Package '$packageName' integrity digest has an invalid size.",
        )
      }
      return NpmIntegrity(encoded = token, algorithm = algorithm, expectedDigest = digest)
    }
  }

  private enum class Algorithm {
    SHA256,
    SHA512,
  }
}
