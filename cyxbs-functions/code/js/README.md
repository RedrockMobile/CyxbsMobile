# JavaScript 加载与执行

该模块将远端或教学编辑器产生的 JavaScript 源码作为长期主数据，在端上按当前 QuickJS
版本编译并缓存字节码。Android、iOS 与 Desktop 共用 `noWebMain` 实现。

## 核心对象

- `JsSourcePackage`：可签名、可序列化的源码包，支持单文件和 ES Module 文件图。
- `JsRuntimeBundle`：可在多个业务间复用的预置模块与 Kotlin 宿主能力。
- `JsExecutionPolicy`：区分内部、教学场景的内存、源码大小、模块数和能力白名单。
- `JsExecutionEnvironment`：把策略、Bundle 和来源校验器组合成一次业务环境。
- `JsProgramClient`：统一安装、加载、编译缓存和执行入口。
- `OkioJsProgramStorage`：Android、iOS、Desktop 共用的源码与字节码持久化。

每次 `execute()` 都会创建独立 QuickJS Runtime。内部业务复用的是不可变 Bundle，不会共享
`globalThis`、Promise 队列或脚本可变状态。

## 内部动态脚本

内部远端源码必须使用业务真实签名校验器，不能使用 `TrustLocalJsSourceVerifier`。

```kotlin
val storage = OkioJsProgramStorage(rootDirectory = appPrivatePath)
val client = JsProgramClient(
  sourceStore = storage,
  bytecodeCache = storage,
)

val internalBundle = JsRuntimeBundle(
  id = "internal-common",
  version = 1,
  hostApiVersion = 1,
  capabilities = listOf(
    JsAsyncFunctionCapability(
      id = "network.request",
      functionName = "request",
    ) { args ->
      requestThroughAppBridge(args)
    },
  ),
)

val environment = JsExecutionEnvironment.forInternal(
  bundle = internalBundle,
  sourceVerifier = { sourcePackage ->
    if (!verifySignature(sourcePackage)) {
      throw JsSourceVerificationException("Invalid source package signature.")
    }
  },
)

val reference = client.install(
  sourcePackage = downloadedSourcePackage,
  environment = environment,
)
val result = client.execute<Any?>(reference, environment)
```

修改宿主函数、预置模块或其语义时必须提升 `JsRuntimeBundle.version`，让旧字节码缓存自然失效。

## 教学脚本

教学环境默认不开放任何宿主能力，并使用更低的内存、源码大小和 Bundle 上限。

```kotlin
val teachingBundle = JsRuntimeBundle(
  id = "teaching-basic",
  version = 1,
  hostApiVersion = 1,
  capabilities = listOf(
    JsSyncFunctionCapability(
      id = "teaching.output",
      functionName = "print",
    ) { args ->
      appendTeachingOutput(args.joinToString())
      null
    },
  ),
)

val teachingEnvironment = JsExecutionEnvironment.forTeaching(
  bundle = teachingBundle,
  policy = JsExecutionPolicy.teaching(
    allowedCapabilityIds = setOf("teaching.output"),
  ),
)

val sourcePackage = JsSourcePackage.create(
  packageId = "teaching.user-code",
  version = "1",
  files = mapOf("main.js" to "print(40 + 2); 42"),
  requiredCapabilities = setOf("teaching.output"),
)

val result = client.installAndExecute<Int>(
  sourcePackage = sourcePackage,
  environment = teachingEnvironment,
)
```

网络、数据保存和动态 UI 等高权限桥必须分别建成独立 `JsHostCapability`，不要把一个全能对象暴露
给教学 Bundle。

## 字节码策略

字节码缓存键包含源码哈希、QuickJS 版本、执行策略、Bundle ID、Bundle 版本和宿主 API 版本。
任一兼容条件变化都会重新编译。字节码文件自身还带有 SHA-256，损坏缓存不会交给 QuickJS。

当前使用的 QuickJS-kt 本地验证版本已修复多 Module 字节码加载，并提供 Runtime 级
`ModuleLoader`。业务层通过 `JsModuleLoader.load()` 按名称返回有效缓存
`JsModuleContent.Bytecode`，缓存未命中时返回 `JsModuleContent.Source`；源码编译完成后，
`JsModuleLoader.onCompiled()` 会立即返回该 Module 的新字节码。

`onCompiled()` 在 QuickJS 解析 Module 的同步边界内调用，必须快速返回且不得重入同一个
`QuickJsRuntime`。需要写磁盘或数据库时，应在回调中把字节码投递给业务自己的异步队列，
持久化错误也由业务处理。静态依赖可以在执行前通过 `resolveModuleGraph()` 解析和收集，动态
`import()` 则在实际执行到对应语句时通过同一个回调返回。

`JsProgramClient` 的持久化协议目前仍以一个程序缓存键保存一份入口字节码，尚未保存每个 Module
各自的源码哈希和字节码。因此 Client 当前仍只缓存单文件 `SCRIPT`，Module 文件图继续执行源码；
这属于业务缓存层的后续工作，不再是引擎加载限制。需要自行探索增量缓存的业务可以在创建
`QuickJsRuntime` 时传入 `JsModuleLoader`，先编译入口 Module，再调用
`resolveModuleGraph()` 收集静态依赖字节码，最后执行入口以继续处理动态 import。

字节码缓存是可删除数据，可以调用：

```kotlin
client.clearBytecodeCache()
```

该操作不会删除已安装源码包。

## 当前安全边界

- 内部远端源码必须验签，并保存在 App 私有目录。
- 教学脚本只能使用白名单 Bundle。
- 每次执行使用独立 Runtime，并应用内存和栈限制。
- 单次执行默认限制为 5 秒，超时或主动取消会通过 QuickJS-kt 1.0.8 的原生中断机制终止无限循环；
  业务可以通过 `QuickJsRuntimeConfig` 调整限制，设置为 0 时必须自行承担无法自动超时的风险。
- 普通 JavaScript 运行异常不会自动回退并重复执行源码，避免网络、存储等宿主副作用发生两次。
