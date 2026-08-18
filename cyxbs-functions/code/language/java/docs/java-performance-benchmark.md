# Java 动态语言三平台性能基准

## 1. 统一测量口径

`DynamicLanguageManager.measurePerformance` 位于通用 `language:noWebMain`，Android、iOS 与 Desktop
调用同一实现。报告固定包含以下阶段：

| 阶段 | 口径 |
| --- | --- |
| `language-load` | 从语言目录、本地 npm 池加载 Java 包并创建语言 Service 会话 |
| `compile-full` | 首次编译两文件工作区 |
| `compile-incremental` | 路径和入口不变、只修改一个整数常量后的编译 |
| `compile-exact` | 对相同请求重复编译，默认取多次中位数和 P95 |
| `execute-isolated-runtime` | 每次创建并关闭独立 QuickJS Runtime，包含编译缓存查询与执行 |

墙钟时间始终记录；较新的语言包还会返回 Service 内部耗时与 `FULL`、`INCREMENTAL`、`EXACT`
缓存类型，旧 debug 包没有该字段时保留为空。内存采样位于阶段边界，不进入阶段计时。报告不设置
开发机硬阈值，回归判断必须比较相同设备、构建类型、包版本与场景。

## 2. Desktop 实测与获取方式

先保证项目根目录 `build/npm/debug-source` 中存在 catalog、Java 及依赖 tgz，再执行：

```shell
CYXBS_JAVA_PERFORMANCE_BENCHMARK=true \
CYXBS_PROJECT_DIR="$PWD" \
./gradlew :cyxbs-functions:code:editor:desktopTest \
  --tests com.cyxbs.functions.code.editor.preview.JavaDynamicLanguagePerformanceBenchmarkTest \
  --no-configuration-cache
```

报告生成到：

```text
cyxbs-functions/code/editor/build/reports/java-performance/desktop.json
```

2026-08-18 在 Apple Silicon macOS、JDK 21.0.10、已有本地 debug npm 图的实测基线如下。该结果
衡量本地包加载，不含 registry 网络下载：

| 阶段 | 中位数 | P95 |
| --- | ---: | ---: |
| language-load | 652.143 ms | 652.143 ms |
| compile-full | 56.765 ms | 56.765 ms |
| compile-incremental | 47.894 ms | 47.894 ms |
| compile-exact（7 次） | 42.752 ms | 45.704 ms |
| execute-isolated-runtime（7 次） | 42.093 ms | 49.315 ms |

JVM heap 检查点为：初始 24.1 MiB、峰值 232.0 MiB、关闭会话后立即采样 232.0 MiB，峰值相对
增长 207.9 MiB。基准不会主动触发 GC，因此最后一个检查点不能直接解释为泄漏或长期驻留；JVM heap
也不等于进程 RSS。需要分析回收后保留量、native QuickJS 或完整进程时，配合 JFR、VisualVM 或
macOS Activity Monitor，并把 GC、工具和采样周期写入报告环境字段。

## 3. Android 获取方式

Android debug 页面或 instrumentation 调用同一个 `measurePerformance`，场景和迭代次数必须与
Desktop 保持一致。建议分两类数据：

1. 报告中的阶段墙钟、Service 内部耗时与缓存类型；
2. 在基准前、load 后、编译后、执行峰值和 close 后执行
   `adb shell dumpsys meminfo com.mredrock.cyxbs.test`，记录 `TOTAL PSS`。

更细的 CPU、线程与 native heap 使用 Android Studio Profiler 或 Perfetto。测冷加载前应杀掉应用并
明确是否清理 npm 池；只重启进程而保留本地 tgz 测的是用户日常本地冷启动，清理池后才包含下载与
解包，二者不能混为一条趋势线。

## 4. iOS 获取方式

iOS debug 壳同样从共享 Kotlin 调用 `measurePerformance`，将 JSON 写入应用 Documents 或通过
调试日志导出。时间以共享单调时钟为准；内存与 CPU 使用 Instruments：

- Time Profiler 对应 load、compile、execute 三类阶段；
- Allocations/VM Tracker 记录基准前、load 后、执行峰值和 close 后；
- 需要时间线关联时，在调用边界增加 `os_signpost`，但不能替换共享报告的墙钟口径。

Simulator 与真机结果必须分开保存，并记录机型、系统版本、Debug/Release、npm 包版本和本地池状态。
本批只建立三平台取数入口并实际运行 Desktop；Android 与 iOS 未伪造测量数字。

## 5. 使用边界

- 首轮 load 只有一个样本，因此中位数和 P95 相同；跨进程重复采集后再比较冷启动分布。
- EXACT 和执行默认使用 7 次样本；性能回归建议至少运行 3 轮进程级基准，丢弃预热轮。
- JVM、ART 与 Kotlin/Native 的内存统计定义不同，只比较各自平台内的趋势，不横向比较绝对值。
- JSON 报告属于构建产物，不提交机器相关结果；文档只保留经过确认的参考基线。
