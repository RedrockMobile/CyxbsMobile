# npm package pool

`code:npm` 是 Android、iOS、Desktop 共用的 npm 下载与 ESM Module 图基础设施，不依赖具体 JS 引擎。

推荐通过 `NpmJsExecutor` 完成下载与运行，或单独使用 `NpmPackagePool` 管理依赖。`Exact` 固定复用
指定版本；`Latest` 在每个包池实例首次运行该入口前检查一次 registry，同一实例内不重复刷新。

```text
首次/Exact 变化/Latest 启动检查：registry metadata -> 精确依赖图 -> tgz + SRI
后续加载：     EntryResolution -> 本地归档
池代际变化：   本地 semver 重解析 -> 完整时原子切换，否则保留旧图
依赖图更新：   新图可运行后异步执行可达性 GC
14 天未使用：  移除入口 -> 标记剩余入口可达包 -> 清理不可达归档
```

调用方应通过 `acquireEntry()` 获取租约，并在 `NpmModuleGraphFactory.create()` 完成后调用 `release()`；
也可以使用自动释放的 `withEntry()`。同名包的多个版本可以共存，Module normalize 会根据“发起 import
的父包”选择其 EntryResolution 中锁定的具体版本。

业务通常直接使用统一入口：

```kotlin
NpmJsExecutor(packagePool).executeValue(
  request = NpmEntryRequest(
    packageName = "@cyxbs-mobile/cyxbs-functions-code-language-js",
    version = NpmEntryVersion.Latest,
  ),
  runtimeFactory = runtimeFactory,
  code = """import "@cyxbs-mobile/cyxbs-functions-code-language-js";""",
)
```

应用 `manager.npmJs` 或 `manager.npmJsBridge` 的模块无需手写 npm 坐标：包名固定为
`@cyxbs-mobile/` 加完整 Gradle 模块路径（将 `:` 转为 `-`），版本固定使用该模块的
`project.version`。

需要绑定宿主桥或执行多步逻辑时使用 `withRuntime()`；Runtime 会在依赖完整更新后创建，并在回调
结束后关闭。

## 网络请求

客户端只会发起两类 GET：

1. `GET {registryBaseUrl}/{encodedPackageName}`：首次解析或入口请求变化时获取版本、依赖、SRI 和
   tarball 地址；发送 `Accept: application/vnd.npm.install-v1+json`。
2. `GET {dist.tarball}`：本地无相同 name/version/integrity 归档时下载，写入前验证 SRI。

`Latest` 只有新包池实例的首次使用会请求 metadata；失败且旧图完整时回退旧版本。新闭包全部下载并
校验成功后才会原子切换，切换后异步触发可达性 GC；当前入口图和活动租约均不可达的旧包会被删除。
`Exact` 不执行启动检查。

单次执行传入 `NpmRefreshPolicy.FORCE` 时，即使当前包池已经刷新过也会在创建 Runtime 前重新请求；
刷新失败会保留旧图但直接结束本次执行，业务如需降级可再次使用 `AUTO`。默认 `AUTO` 不改变上述
每实例首次检查和失败回退行为。

当前 semver 支持精确版本、`latest`、caret、tilde、wildcard、比较器组合、连字符区间和 `||`；npm
alias、git、file、workspace 协议会明确失败。
