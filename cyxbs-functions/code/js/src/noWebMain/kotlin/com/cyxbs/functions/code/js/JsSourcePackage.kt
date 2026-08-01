package com.cyxbs.functions.code.js

import com.cyxbs.functions.code.js.runtime.QuickJsRuntime
import kotlinx.serialization.Serializable
import okio.Buffer
import okio.ByteString.Companion.toByteString

/**
 * JavaScript 入口的编译方式。
 */
@Serializable
enum class JsProgramMode {
  /** 按普通脚本执行，允许直接返回表达式结果。 */
  SCRIPT,

  /** 按 ES Module 执行，支持 import、export 与顶层 await。 */
  MODULE,
}

/**
 * 已安装源码包的稳定引用。
 *
 * @param packageId 业务侧定义的包标识。
 * @param version 业务版本；同一个包可以保存多个版本用于灰度或回滚。
 */
@Serializable
data class JsProgramRef(
  val packageId: String,
  val version: String,
)

/**
 * 远端下发或教学编辑器生成的 JavaScript 源码包。
 *
 * 源码是唯一需要长期保存的原始产物，QuickJS 字节码只作为可失效缓存。构造时会重新计算
 * [contentHash]，避免存储、传输或反序列化后继续使用内容不一致的包。
 *
 * @param packageId 包标识，只允许字母、数字、点、下划线和短横线。
 * @param version 业务版本。
 * @param entry 入口文件名，必须存在于 [files] 中。
 * @param mode 入口编译方式。
 * @param files 文件名到源码的映射；除入口外的文件会注册为 ES Module。
 * @param requiredHostApiVersion 脚本要求的宿主桥协议版本。
 * @param requiredCapabilities 脚本声明需要的宿主能力 ID。
 * @param metadata 参与完整性校验的业务元数据。
 * @param contentHash 对执行相关内容计算得到的 SHA-256。
 * @param signature 业务侧签名文本；具体编码与验签算法由 [JsSourcePackageVerifier] 决定。
 */
@Serializable
data class JsSourcePackage(
  val packageId: String,
  val version: String,
  val entry: String,
  val mode: JsProgramMode,
  val files: Map<String, String>,
  val requiredHostApiVersion: Int,
  val requiredCapabilities: Set<String> = emptySet(),
  val metadata: Map<String, String> = emptyMap(),
  val contentHash: String,
  val signature: String? = null,
) {

  init {
    require(PACKAGE_ID_REGEX.matches(packageId)) {
      "packageId must match ${PACKAGE_ID_REGEX.pattern}."
    }
    require(version.isNotBlank() && version.length <= MAX_VERSION_LENGTH) {
      "version must contain 1..$MAX_VERSION_LENGTH characters."
    }
    require(files.isNotEmpty()) { "files must not be empty." }
    require(entry in files) { "entry '$entry' does not exist in files." }
    require(requiredHostApiVersion > 0) { "requiredHostApiVersion must be greater than 0." }
    files.keys.forEach(::validateModuleName)
    requiredCapabilities.forEach(::validateCapabilityId)
    require(contentHash == calculateContentHash(
      packageId = packageId,
      version = version,
      entry = entry,
      mode = mode,
      files = files,
      requiredHostApiVersion = requiredHostApiVersion,
      requiredCapabilities = requiredCapabilities,
      metadata = metadata,
    )) {
      "contentHash does not match the source package content."
    }
  }

  /**
   * 当前包的稳定引用。
   */
  val reference: JsProgramRef
    get() = JsProgramRef(packageId = packageId, version = version)

  /**
   * 所有源码的 UTF-8 字节数，用于执行策略的包体限制。
   */
  val sourceSizeBytes: Long
    get() = files.values.sumOf { it.encodeToByteArray().size.toLong() }

  /**
   * 返回入口源码。
   */
  fun entrySource(): String = files.getValue(entry)

  companion object {
    private const val FORMAT_VERSION = 1
    private const val MAX_VERSION_LENGTH = 64
    private val PACKAGE_ID_REGEX = Regex("[A-Za-z0-9._-]{1,128}")
    private val CAPABILITY_ID_REGEX = Regex("[A-Za-z0-9._-]{1,128}")

    /**
     * 创建源码包并自动计算内容哈希。
     *
     * 调用方如需签名，应先用该方法得到 [contentHash]，对约定的签名载荷签名后，再通过
     * [copy] 写入 [signature]。
     */
    fun create(
      packageId: String,
      version: String,
      entry: String = QuickJsRuntime.DEFAULT_FILENAME,
      mode: JsProgramMode = JsProgramMode.SCRIPT,
      files: Map<String, String>,
      requiredHostApiVersion: Int = 1,
      requiredCapabilities: Set<String> = emptySet(),
      metadata: Map<String, String> = emptyMap(),
      signature: String? = null,
    ): JsSourcePackage {
      val contentHash = calculateContentHash(
        packageId = packageId,
        version = version,
        entry = entry,
        mode = mode,
        files = files,
        requiredHostApiVersion = requiredHostApiVersion,
        requiredCapabilities = requiredCapabilities,
        metadata = metadata,
      )
      return JsSourcePackage(
        packageId = packageId,
        version = version,
        entry = entry,
        mode = mode,
        files = files,
        requiredHostApiVersion = requiredHostApiVersion,
        requiredCapabilities = requiredCapabilities,
        metadata = metadata,
        contentHash = contentHash,
        signature = signature,
      )
    }

    /**
     * 按固定字段顺序计算跨平台稳定的 SHA-256。
     *
     * 字符串使用“UTF-8 长度 + 内容”编码，避免简单拼接造成边界碰撞；集合和 Map 会排序，
     * 从而保证 Android、iOS 与 Desktop 生成相同结果。
     */
    fun calculateContentHash(
      packageId: String,
      version: String,
      entry: String,
      mode: JsProgramMode,
      files: Map<String, String>,
      requiredHostApiVersion: Int,
      requiredCapabilities: Set<String>,
      metadata: Map<String, String>,
    ): String {
      val buffer = Buffer()
      buffer.writeInt(FORMAT_VERSION)
      buffer.writeStableString(packageId)
      buffer.writeStableString(version)
      buffer.writeStableString(entry)
      buffer.writeStableString(mode.name)
      buffer.writeInt(requiredHostApiVersion)
      buffer.writeInt(files.size)
      files.entries.sortedBy { it.key }.forEach { (name, source) ->
        buffer.writeStableString(name)
        buffer.writeStableString(source)
      }
      buffer.writeInt(requiredCapabilities.size)
      requiredCapabilities.sorted().forEach { capabilityId ->
        buffer.writeStableString(capabilityId)
      }
      buffer.writeInt(metadata.size)
      metadata.entries.sortedBy { it.key }.forEach { (key, value) ->
        buffer.writeStableString(key)
        buffer.writeStableString(value)
      }
      return buffer.readByteArray().toByteString().sha256().hex()
    }

    /**
     * 校验模块名，禁止空名称和 NUL 字符；模块名本身不会直接用作本地文件路径。
     */
    private fun validateModuleName(name: String) {
      require(name.isNotBlank() && '\u0000' !in name) {
        "Module names must not be blank or contain NUL characters."
      }
    }

    /**
     * 校验宿主能力 ID，保证它可以稳定参与白名单和缓存键计算。
     */
    private fun validateCapabilityId(id: String) {
      require(CAPABILITY_ID_REGEX.matches(id)) {
        "Capability id must match ${CAPABILITY_ID_REGEX.pattern}."
      }
    }

    /**
     * 向哈希缓冲区写入带长度的 UTF-8 字符串。
     */
    private fun Buffer.writeStableString(value: String) {
      val bytes = value.encodeToByteArray()
      writeInt(bytes.size)
      write(bytes)
    }
  }
}

/**
 * 校验源码包的来源与签名。
 *
 * 实现应在校验失败时抛出 [JsSourceVerificationException]；内部远端场景必须使用真实验签实现。
 */
fun interface JsSourcePackageVerifier {

  /**
   * 校验 [sourcePackage]，成功时正常返回，失败时抛出异常。
   */
  suspend fun verify(sourcePackage: JsSourcePackage)
}

/**
 * 明确信任本机产生源码的校验器，仅用于教学编辑器等本地输入场景。
 *
 * 它不验证远端身份，不能用于内部动态下发。
 */
object TrustLocalJsSourceVerifier : JsSourcePackageVerifier {

  override suspend fun verify(sourcePackage: JsSourcePackage) = Unit
}

/**
 * 源码包签名或来源校验失败。
 */
class JsSourceVerificationException(
  message: String,
  cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
