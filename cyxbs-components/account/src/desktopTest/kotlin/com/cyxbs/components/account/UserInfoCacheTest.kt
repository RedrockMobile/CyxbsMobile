package com.cyxbs.components.account

import com.cyxbs.components.account.api.UserInfo
import com.cyxbs.components.account.provider.SecretTransformer
import com.cyxbs.components.account.provider.UserInfoProvider
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.defaultSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 验证启动恢复资料时的归属校验，同时覆盖内存缓存及旧版持久化键。 */
class UserInfoCacheTest {
  @BeforeTest
  fun setUp() = UserInfoProvider.clear()

  @AfterTest
  fun tearDown() = UserInfoProvider.clear()

  /** 同一学号的资料直接恢复，不删除已有缓存。 */
  @Test
  fun matchingAccountKeepsCachedProfile() {
    val info = seedCache("20260001")
    assertSame(info, UserInfoProvider.loadForAccount("20260001"))
    assertSame(info, UserInfoProvider.value)
    assertTrue(defaultSettings.hasKey("cyxbsmobile_user_info"))
  }

  /** 旧版本遗留的其他账户资料不能进入当前账户，内存和磁盘缓存同时清空。 */
  @Test
  fun differentAccountClearsProfileAndPersistentCache() {
    seedCache("20260002")
    assertNull(UserInfoProvider.loadForAccount("20260001"))
    assertNull(UserInfoProvider.value)
    assertNull(defaultSettings.getStringOrNull("cyxbsmobile_user_info"))
  }

  /** 没有缓存时返回 null，由账户初始化流程发起资料请求。 */
  @Test
  fun missingCacheReturnsNull() {
    assertNull(UserInfoProvider.loadForAccount("20260001"))
  }

  /** 仅 JVM 测试通过反射模拟进程启动时已加载的缓存，避免暴露生产资料写入接口。 */
  private fun seedCache(stuNum: String): UserInfo {
    val info = UserInfo("男", "", stuNum, "测试用户", "测试昵称", "测试学院")
    defaultSettings.putString(
      "cyxbsmobile_user_info",
      SecretTransformer.impl.secretEncrypt(defaultJson.encodeToString(info)),
    )
    UserInfoProvider::class.java.getDeclaredField("value").apply { isAccessible = true }.set(null, info)
    return info
  }
}
