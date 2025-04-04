package com.cyxbs.components.account.provider

import android.util.Base64
import com.cyxbs.components.utils.utils.secret.SerialAESEncryptor
import com.g985892345.provider.api.annotation.ImplProvider
import java.nio.charset.StandardCharsets

/**
 * .
 *
 * @author 985892345
 * @date 2025/4/4
 */
@ImplProvider
internal object SecretTransformerImpl : SecretTransformer {

  private val IsSupportEncrypt = try {
    SerialAESEncryptor.encrypt("abc".toByteArray(StandardCharsets.UTF_8))
    true
  } catch (e: Exception) {
    e.printStackTrace()
    false
  }

  override fun secretEncrypt(input: String): String {
    if (!IsSupportEncrypt) return input
    return try {
      Base64.encodeToString(
        SerialAESEncryptor.encrypt(input.toByteArray(StandardCharsets.UTF_8)),
        Base64.DEFAULT
      )
    } catch (e: Exception) {
      e.printStackTrace()
      input
    }
  }

  override fun secretDecrypt(input: String): String {
    if (!IsSupportEncrypt) return input
    return try {
      String(
        SerialAESEncryptor.decrypt(
          Base64.decode(
            input,
            Base64.DEFAULT
          )
        ), StandardCharsets.UTF_8
      )
    } catch (e: Exception) {
      e.printStackTrace()
      input
    }
  }
}