# 动态语言不支持能力统计

## 1. 目的

阶段 4 不按 Java SE API 总量盲目扩展，而根据真实教学代码遇到的能力缺口排序。每次
`DynamicLanguageSession.compile`（包括 `run` 内部触发的编译）完成后，端上读取结构化诊断，只把
稳定 code 中含 `unsupported` 的项计入本地统计。例如：

- `java.frontend.unsupported`
- `java.semantic.raw_type_unsupported`
- `JAVA_LOWERING_UNSUPPORTED`
- `JAVASCRIPT_COMMONJS_UNSUPPORTED`

`undefined_name`、`unknown_method`、语法错误等可能由用户代码本身造成，不代表产品缺少已规划能力，
因此不会混入统计。

## 2. 聚合口径

每项使用以下稳定身份：

```text
languageId + npmPackageName + npmPackageVersion + diagnosticCode
```

同时记录两个数值：

- `affectedCompilationCount`：一次编译中相同 code 无论出现多少次都只增加 1，用于判断多少次用户
  操作受到影响；
- `diagnosticOccurrenceCount`：保留实际诊断条数，用于识别一次工作区中大量重复出现的缺口。

不同 npm 版本不会合并，避免能力已经补齐后仍被旧版本历史数据误导。结果按受影响编译次数、诊断
次数和稳定身份排序，最多持久化 256 项；超过 160 字符的异常 code 不记录。

## 3. 隐私与生命周期

统计仅保存语言 ID、npm 包坐标/版本、诊断 code 和两个计数，不保存以下内容：

- Java 源码或生成的 JavaScript；
- 诊断 message、note；
- 文件路径、源码区间或符号名；
- 标准输入、标准输出或运行参数。

默认 JSON 位于系统缓存区域的 `cyxbs-code/language-capabilities/v1`，与用户工程和正式业务数据分离；
系统清理缓存后自然重建。存储损坏或写入失败不会阻断编译、运行或编辑器功能。

## 4. 读取和清空

业务通过 Manager 读取不可变快照：

```kotlin
val all = manager.unsupportedCapabilityStatistics()
val java = manager.unsupportedCapabilityStatistics("java")
manager.clearUnsupportedCapabilityStatistics("java")
manager.clearUnsupportedCapabilityStatistics()
```

语言参数接受 Catalog ID 或别名。代码测试页的设置栏展示前 5 项，并提供“清空本地统计”按钮；它
只清理这份统计，不影响 npm 包池、语言图标、编译结果缓存或编辑器文件会话。

## 5. 如何用于能力规划

统计是排序信号，不是自动开放语法/API 的开关。准备增强版本时应：

1. 按 `affectedCompilationCount` 排序，优先确认高频 code 对应的真实教学用例；
2. 分 npm 版本排除已经修复的旧包历史；
3. 用差分测试为准备支持的 case 增加 javac/java 基准；
4. 评估包体、运行时与维护成本后再加入 allowlist；
5. 继续明确拒绝反射、动态类加载、网络、文件和原生序列化等高成本边界。

当前实现没有上传或全局遥测；若后续需要汇总，必须另行设计用户授权、匿名化、保留周期和服务端
限流，不能直接上传本地 JSON。
