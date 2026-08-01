# JavaScript 加载与执行

该模块将远端或教学编辑器产生的 JavaScript 源码作为长期主数据，在端上按当前 QuickJS
版本编译并缓存字节码。Android、iOS 与 Desktop 共用 `noWebMain` 实现。

## 核心对象

- `JsSourcePackage`：可签名、可序列化的源码包，支持单文件和 ES Module 文件图。
- `JsRuntimeBundle`：可在多个业务间复用的预置模块与 Kotlin 宿主能力。
- `JsExecutionPolicy`：区分内部、教学场景的内存、源码大小、模块数和能力白名单。
- `JsExecutionEnvironment`：把策略、Bundle 和来源校验器组合成一次业务环境。
- `JsProgramClient`：统一安装、加载、编译缓存和执行入口。
- `JsDiagnostic`：把执行异常转换为编辑器可直接展示的分类、源码位置和 JavaScript 堆栈。
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
    JsTeachingConsoleCapability { message ->
      teachingOutput.tryEmit(message)
    },
    JsTeachingInputCapability(
      input = { prompt -> awaitTeachingInput(prompt) },
    ),
  ),
)

val teachingEnvironment = JsExecutionEnvironment.forTeaching(
  bundle = teachingBundle,
  policy = JsExecutionPolicy.teaching(
    allowedCapabilityIds = setOf(
      JsTeachingConsoleCapability.ID,
      JsTeachingInputCapability.ID,
    ),
  ),
)

val sourcePackage = JsSourcePackage.create(
  packageId = "teaching.user-code",
  version = "1",
  mode = JsProgramMode.MODULE,
  files = mapOf(
    "main.js" to "console.log('Hello', await readLine('Name?'));",
  ),
  requiredCapabilities = setOf(
    JsTeachingConsoleCapability.ID,
    JsTeachingInputCapability.ID,
  ),
)

val result = client.installAndExecute<Any?>(
  sourcePackage = sourcePackage,
  environment = teachingEnvironment,
)
```

`JsTeachingConsoleCapability` 提供 `console.log/info/warn/error` 和 `print`；输出回调位于 QuickJS
同步 binding 边界，只应投递消息，不能阻塞或重入 Runtime。`JsTeachingInputCapability` 提供需要
`await` 的 `readLine`。取消 `execute()` 所在协程会取消输入等待并中断正在运行的 JavaScript，
可直接作为教学页面“停止运行”按钮的底层行为。

QuickJS 原生提供标准 `JSON.parse()` 和 `JSON.stringify()`，不需要额外开放宿主能力。教学控制台
会把普通对象、数组和嵌套值编码成合法 JSON 文本，顶层字符串仍按 `console.log` 习惯直接显示。
Kotlin 与 JavaScript 需要交换复杂业务数据时，当前推荐通过宿主函数传递 JSON 字符串，并在脚本
侧显式解析；这样协议可序列化、可记录，也不会把 Runtime 内对象泄漏到执行生命周期之外。

网络、数据保存和动态 UI 等高权限桥必须分别建成独立 `JsHostCapability`，不要把一个全能对象暴露
给教学 Bundle。

## 错误诊断

`execute()` 保持 QuickJS、协程和宿主能力的原始异常语义。教学编辑器只在展示错误时调用
`toJsDiagnostic()`，即可获得稳定分类以及 QuickJS 能够提供的文件、行列和 JavaScript 堆栈：

```kotlin
try {
  client.execute<Any?>(reference, teachingEnvironment)
} catch (throwable: Throwable) {
  val diagnostic = throwable.toJsDiagnostic()
  editor.showDiagnostic(
    message = diagnostic.message,
    fileName = diagnostic.fileName,
    line = diagnostic.lineNumber,
    column = diagnostic.columnNumber,
  )
}
```

语法错误、Module 解析错误和普通运行错误使用独立分类；协程取消映射为 `CANCELLED`。QuickJS
无法区分执行超时与调用方主动调用 `interruptEvaluation()`，两者统一映射为 `INTERRUPTED`。
非 QuickJS 异常统一归为 `HOST_ERROR`，业务仍可在转换前按自己的存储、验签或网络异常细分。

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

`JsProgramClient` 会为入口、静态依赖和动态 `import()` 依赖分别建立缓存项。缓存键包含 Module
所有者、名称、源码哈希、QuickJS 版本、策略与 Bundle 环境，因此同一 `packageId` 发布新版本时，
未变化的 Module 可以继续复用，只有实际依赖路径上源码发生变化的 Module 会重新编译。

静态依赖会在执行顶层代码前通过 `resolveModuleGraph()` 解析；遇到不可解析缓存时，Client 会使用
全新 Runtime 从源码安全重建。动态依赖只能在执行到 `import()` 时发现，若其缓存导致执行失败，
Client 会删除本次新加载的动态缓存，但不会自动重跑可能已经产生宿主副作用的入口代码。业务可从
`JsExecutionResult.compiledModules` 和 `cachedModules` 查看本次实际编译与命中的 Module。

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
