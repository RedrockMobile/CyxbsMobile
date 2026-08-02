# JavaScript 加载与执行

JavaScript 能力分为两层：

- `js`：面向业务的引擎无关 Runtime、源码包、执行策略、宿主能力、源码存储和错误模型。
- `js:quickjs`：基于 quickjs-kt 的实现，以及 Module 静态预解析、字节码缓存和损坏恢复。

Android、iOS 与 Desktop 共用 `noWebMain` 实现。业务不会接触 quickjs-kt 类型、字节码格式、
编译 API 或 Module 图解析 API。

## 业务入口

- `JsRuntime`：执行源码、绑定宿主函数、中断、关闭和查询关闭状态的最小契约。
- `JsRuntimeFactory`：选择具体 JavaScript 引擎的工厂接口。
- `JsProgramClient`：安装、加载和执行 `JsSourcePackage` 的高层入口。
- `JsRuntimeBundle`：可复用的预置模块与 Kotlin 宿主能力。
- `JsExecutionPolicy`：内部、教学场景的资源限制与能力白名单。
- `JsDiagnostic`：编辑器可以直接展示的稳定错误分类和源码位置。
- `QuickJsRuntimeFactory`：当前 QuickJS 实现入口；默认透明使用实现内部缓存。

每次 `JsProgramClient.execute()` 都会创建隔离 Runtime，不会在不同程序之间共享 `globalThis`、
Promise 队列或可变对象。

## 创建 Program Client

Program 只依赖公共 Runtime 工厂。源码是主数据，存放在业务指定的 App 私有目录；QuickJS 的
编译、依赖预解析和缓存均留在实现模块内部。

```kotlin
val storage = OkioJsProgramStorage(rootDirectory = appPrivatePath)
val client = JsProgramClient(
  sourceStore = storage,
  runtimeFactory = QuickJsRuntimeFactory,
)
```

## 内部动态脚本

内部远端源码必须使用业务真实签名校验器，不能使用 `TrustLocalJsSourceVerifier`。

```kotlin
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

修改宿主 API 时仍应提升 `JsRuntimeBundle.version`，供源码包安装策略和业务版本管理使用。QuickJS
字节码只按实际参与编译的源码及引擎版本隔离，不缓存宿主 Kotlin 对象。

## 教学脚本

教学编辑器使用无持久化缓存工厂：每次都编译当前输入，源码仅保存在 Runner 的进程内存中。

```kotlin
val runner = JsTeachingCodeRunner.create(
  runtimeFactory = QuickJsRuntimeFactory.uncached(),
)
val result = runner.execute("console.log('Hello'); 1 + 2")

result.consoleMessages.forEach { message -> output.tryEmit(message) }
```

需要输入能力时，可以自行构造 `JsTeachingConsoleCapability`、`JsTeachingInputCapability` 与
`JsExecutionEnvironment.forTeaching()`。控制台回调发生在引擎同步 binding 边界，只应快速投递
消息，不能阻塞或同步重入同一个 Runtime。取消执行协程会取消输入等待，并请求引擎中断执行。

JavaScript 引擎提供标准 `JSON.parse()` 和 `JSON.stringify()`。Kotlin 与 JavaScript 交换复杂业务
数据时，推荐通过宿主函数传递 JSON 字符串，避免把 Runtime 内对象泄漏到执行生命周期之外。

网络、数据保存和动态 UI 等高权限桥必须拆分为独立 `JsHostCapability`，不要向教学 Bundle 暴露
全能宿主对象。

## 直接使用 Runtime

只有不需要源码安装、策略和持久化的轻量场景才直接创建 Runtime。动态语言适配器和教学预览应
显式使用无缓存工厂：

```kotlin
val sources = mapOf("math.js" to "export const answer = 42;")
val runtime = QuickJsRuntimeFactory.uncached().create(
  moduleLoader = JsModuleLoader { name -> sources[name] },
)

try {
  runtime.evaluate<Unit>(
    code = "import { answer } from 'math.js'; globalThis.result = answer;",
    filename = "main.js",
    asModule = true,
  )
  val answer = runtime.evaluate<Int>("globalThis.result")
} finally {
  runtime.close()
}
```

`JsRuntime.isClosed` 是公共生命周期状态。关闭后不能再执行脚本或注册宿主能力，重复关闭必须安全。

## 错误诊断

底层引擎异常会在 `quickjs` 模块内转换为稳定的 `JsRuntimeException`，业务不依赖 quickjs-kt
异常类型。教学编辑器可以在展示错误时转换为 `JsDiagnostic`：

```kotlin
try {
  client.execute<Any?>(reference, environment)
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

语法、Module 解析和运行错误分别分类；协程取消映射为 `CANCELLED`。Runtime 无法区分执行超时与
主动中断时，两者统一映射为 `INTERRUPTED`。存储、验签和宿主桥等非 Runtime 异常保持原始语义，
统一转换诊断时归为 `HOST_ERROR`。

## QuickJS 缓存边界

`QuickJsRuntimeFactory` 默认在 `quickjs` 模块固定的平台临时目录内维护缓存；公共 `js` API 不提供
缓存路径、读写、清理、命中来源或编译产物。缓存只是可删除、可重建的优化，源码始终是主数据。

执行 ES Module 时，QuickJS 实现会先读取入口与已知依赖缓存，再调用静态 Module 图解析。只有
预解析成功后才执行顶层代码。预解析一旦失败，本次执行会立即抛错，不会自动重建 Runtime、重放
宿主函数或继续执行源码；实现只会删除本次实际使用的可疑缓存，使业务下一次主动执行时从源码编译。

动态 `import()` 可能发生在入口已经产生副作用之后，因此动态加载失败只使对应缓存失效，不会
自动重跑入口。普通 Script、`QuickJsRuntimeFactory.uncached()` 和教学 Runner 均不读写持久化缓存。

## 当前安全边界

- 内部远端源码必须验签，并保存在 App 私有目录。
- 教学脚本只能使用白名单 Bundle。
- 每次 Program 执行使用独立 Runtime，并应用内存、栈和执行时限。
- 普通 JavaScript 运行异常不会自动重跑源码，避免网络、存储等宿主副作用发生两次。
- JavaScript Runtime 不是系统级安全进程；高风险代码仍需要独立进程或更强的平台隔离。
