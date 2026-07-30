package com.cyxbs.functions.code.js

/**
 * JavaScript 的业务执行场景。
 */
enum class JsExecutionScene {
  /** 由团队维护并签名下发的内部脚本。 */
  INTERNAL,

  /** 用户在教学编辑器中编写的受限脚本。 */
  TEACHING,
}

/**
 * JavaScript 执行策略与资源上限。
 *
 * @param id 策略稳定标识，也会参与字节码缓存隔离。
 * @param scene 业务场景。
 * @param runtimeConfig QuickJS 内存与栈限制。
 * @param maxPackageSourceBytes 单个源码包最大 UTF-8 字节数。
 * @param maxPackageFiles 单个源码包最大文件数。
 * @param maxBundleSourceBytes Bundle 预置模块最大 UTF-8 字节数。
 * @param maxBundleModules Bundle 最大模块数。
 * @param maxBundleCapabilities Bundle 最大宿主能力数。
 * @param allowedCapabilityIds 允许安装的能力 ID；null 表示允许 Bundle 内全部能力。
 */
data class JsExecutionPolicy(
  val id: String,
  val scene: JsExecutionScene,
  val runtimeConfig: QuickJsRuntimeConfig,
  val maxPackageSourceBytes: Long,
  val maxPackageFiles: Int,
  val maxBundleSourceBytes: Long,
  val maxBundleModules: Int,
  val maxBundleCapabilities: Int,
  val allowedCapabilityIds: Set<String>?,
) {

  init {
    require(id.isNotBlank()) { "Policy id must not be blank." }
    require(maxPackageSourceBytes > 0) { "maxPackageSourceBytes must be greater than 0." }
    require(maxPackageFiles > 0) { "maxPackageFiles must be greater than 0." }
    require(maxBundleSourceBytes >= 0) { "maxBundleSourceBytes must not be negative." }
    require(maxBundleModules >= 0) { "maxBundleModules must not be negative." }
    require(maxBundleCapabilities >= 0) { "maxBundleCapabilities must not be negative." }
  }

  /**
   * 在创建 Runtime 前校验源码包与 Bundle，避免超限内容进入编译阶段。
   */
  internal fun validate(
    sourcePackage: JsSourcePackage,
    bundle: JsRuntimeBundle,
  ) {
    checkPolicy(sourcePackage.sourceSizeBytes <= maxPackageSourceBytes) {
      "Source package uses ${sourcePackage.sourceSizeBytes} bytes, limit is $maxPackageSourceBytes."
    }
    checkPolicy(sourcePackage.files.size <= maxPackageFiles) {
      "Source package contains ${sourcePackage.files.size} files, limit is $maxPackageFiles."
    }
    checkPolicy(bundle.sourceSizeBytes <= maxBundleSourceBytes) {
      "Bundle uses ${bundle.sourceSizeBytes} source bytes, limit is $maxBundleSourceBytes."
    }
    checkPolicy(bundle.modules.size <= maxBundleModules) {
      "Bundle contains ${bundle.modules.size} modules, limit is $maxBundleModules."
    }
    checkPolicy(bundle.capabilities.size <= maxBundleCapabilities) {
      "Bundle contains ${bundle.capabilities.size} capabilities, limit is $maxBundleCapabilities."
    }
    checkPolicy(sourcePackage.requiredHostApiVersion == bundle.hostApiVersion) {
      "Source package requires host API ${sourcePackage.requiredHostApiVersion}, " +
        "but bundle provides ${bundle.hostApiVersion}."
    }
    checkPolicy(bundle.capabilityIds.containsAll(sourcePackage.requiredCapabilities)) {
      "Bundle does not provide all capabilities required by the source package."
    }
    allowedCapabilityIds?.let { allowed ->
      checkPolicy(allowed.containsAll(bundle.capabilityIds)) {
        "Bundle contains capabilities that are not allowed by policy '$id'."
      }
    }

    val packageModules = sourcePackage.files.keys - sourcePackage.entry
    checkPolicy(packageModules.intersect(bundle.modules.keys).isEmpty()) {
      "Source package modules must not override bundle modules."
    }
  }

  companion object {
    /**
     * 创建内部脚本默认策略。
     *
     * 内部脚本允许复用完整 Bundle，但远端来源仍必须由环境中的校验器验签。
     */
    fun internal(
      id: String = "internal",
      runtimeConfig: QuickJsRuntimeConfig = QuickJsRuntimeConfig(
        memoryLimitBytes = 64L * 1024L * 1024L,
        maxStackSizeBytes = 512L * 1024L,
      ),
      maxPackageSourceBytes: Long = 4L * 1024L * 1024L,
      maxPackageFiles: Int = 128,
      maxBundleSourceBytes: Long = 4L * 1024L * 1024L,
      maxBundleModules: Int = 128,
      maxBundleCapabilities: Int = 64,
    ): JsExecutionPolicy = JsExecutionPolicy(
      id = id,
      scene = JsExecutionScene.INTERNAL,
      runtimeConfig = runtimeConfig,
      maxPackageSourceBytes = maxPackageSourceBytes,
      maxPackageFiles = maxPackageFiles,
      maxBundleSourceBytes = maxBundleSourceBytes,
      maxBundleModules = maxBundleModules,
      maxBundleCapabilities = maxBundleCapabilities,
      allowedCapabilityIds = null,
    )

    /**
     * 创建教学脚本默认策略。
     *
     * 教学场景默认不允许任何宿主能力，业务必须通过 [allowedCapabilityIds] 显式开放白名单。
     */
    fun teaching(
      id: String = "teaching",
      allowedCapabilityIds: Set<String> = emptySet(),
      runtimeConfig: QuickJsRuntimeConfig = QuickJsRuntimeConfig(
        memoryLimitBytes = 16L * 1024L * 1024L,
        maxStackSizeBytes = 256L * 1024L,
      ),
      maxPackageSourceBytes: Long = 256L * 1024L,
      maxPackageFiles: Int = 16,
      maxBundleSourceBytes: Long = 256L * 1024L,
      maxBundleModules: Int = 16,
      maxBundleCapabilities: Int = 8,
    ): JsExecutionPolicy = JsExecutionPolicy(
      id = id,
      scene = JsExecutionScene.TEACHING,
      runtimeConfig = runtimeConfig,
      maxPackageSourceBytes = maxPackageSourceBytes,
      maxPackageFiles = maxPackageFiles,
      maxBundleSourceBytes = maxBundleSourceBytes,
      maxBundleModules = maxBundleModules,
      maxBundleCapabilities = maxBundleCapabilities,
      allowedCapabilityIds = allowedCapabilityIds.toSet(),
    )

    /**
     * 在策略不满足时抛出包含明确原因的异常。
     */
    private inline fun checkPolicy(
      condition: Boolean,
      lazyMessage: () -> String,
    ) {
      if (!condition) {
        throw JsPolicyViolationException(lazyMessage())
      }
    }
  }
}

/**
 * 源码包或 Bundle 不满足当前业务场景策略。
 */
class JsPolicyViolationException(message: String) : IllegalArgumentException(message)

/**
 * 一次执行所需的策略、共享 Bundle 与来源校验器。
 *
 * @param policy 资源和能力限制。
 * @param bundle 可复用但不会共享 JS 全局状态的运行时 Bundle。
 * @param sourceVerifier 源码来源校验器。
 */
class JsExecutionEnvironment private constructor(
  val policy: JsExecutionPolicy,
  val bundle: JsRuntimeBundle,
  val sourceVerifier: JsSourcePackageVerifier,
) {

  companion object {
    /**
     * 创建内部脚本环境；调用方必须提供真正的远端签名校验器。
     */
    fun forInternal(
      bundle: JsRuntimeBundle,
      sourceVerifier: JsSourcePackageVerifier,
      policy: JsExecutionPolicy = JsExecutionPolicy.internal(),
    ): JsExecutionEnvironment {
      require(policy.scene == JsExecutionScene.INTERNAL) {
        "Internal environment requires an INTERNAL policy."
      }
      return JsExecutionEnvironment(
        policy = policy,
        bundle = bundle,
        sourceVerifier = sourceVerifier,
      )
    }

    /**
     * 创建教学脚本环境；默认只信任本机编辑器产生的源码。
     */
    fun forTeaching(
      bundle: JsRuntimeBundle = JsRuntimeBundle.EMPTY,
      policy: JsExecutionPolicy = JsExecutionPolicy.teaching(),
      sourceVerifier: JsSourcePackageVerifier = TrustLocalJsSourceVerifier,
    ): JsExecutionEnvironment {
      require(policy.scene == JsExecutionScene.TEACHING) {
        "Teaching environment requires a TEACHING policy."
      }
      return JsExecutionEnvironment(
        policy = policy,
        bundle = bundle,
        sourceVerifier = sourceVerifier,
      )
    }
  }
}
