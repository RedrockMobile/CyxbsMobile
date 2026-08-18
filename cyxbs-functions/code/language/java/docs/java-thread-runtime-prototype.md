# Java 受限真并行线程原型

> 状态：2026-08-18 完成 Desktop 技术原型；经成本评估后决定暂缓生产实现。Java 语言包当前不
> 支持 `Thread`、`synchronized` 或跨线程共享对象，也不会用单 Runtime 交错执行冒充多线程。

## 1. 原型目标

原型只验证正式设计成立所必需的三个事实：

1. 每个 Java 线程使用独立 QuickJS Runtime 时，未加锁的共享字段读改写能够真实出现丢失更新；
2. 把同一读改写放入宿主 monitor 后，结果能够稳定恢复；
3. 两个独立 QuickJS CPU 任务可以占用不同宿主线程，并比串行执行更快。

原型位于
`code/language/src/desktopTest/.../JavaThreadRuntimePrototypeTest.kt`，没有修改
`DynamicLanguageService`、Java builtin catalog、AST、IR 或公开宿主 ABI。

## 2. 已验证的执行模型

```text
固定大小宿主线程池
  ├─ 线程 A：创建 → 进入 → 关闭 QuickJS Runtime A
  └─ 线程 B：创建 → 进入 → 关闭 QuickJS Runtime B
                     │
                     ▼
       宿主共享基础字段 / monitor
```

- Runtime 和 JS 对象始终保持线程亲和，不跨线程进入；
- 共享计数器位于宿主堆，未同步自增拆成 read 与 write 两个 ABI 步骤；
- `synchronized` 原型把完整读改写放入同一个宿主 monitor；
- 每个 Runtime 仍使用独立的 16 MiB 内存、256 KiB 栈、10 秒执行上限，并关闭字节码缓存；
- 测试结束会关闭 Runtime 和线程池，异常路径同样释放资源。

## 3. Desktop 结果

本机为 12 个可用处理器的 macOS Desktop，使用项目当前 QuickJS 实现。CPU 用例先执行一次极小
Runtime 预热，再对相同的 8,000,000 次确定性整数循环比较串行与双线程并行。

| 闸门 | 结果 | 结论 |
| --- | ---: | --- |
| 未同步共享计数 | 期望 512，实际稳定为 256 | 真实观察到丢失更新 |
| 宿主 monitor 计数 | 期望 4,000，实际 4,000 | 同步后结果稳定 |
| 两个 QuickJS CPU 任务 | 串行 364 ms，并行 187 ms | 约 1.95×，运行于两个宿主线程 |

计时是技术方向证据而非长期性能基线；CPU 调度、温度和后台负载会改变绝对值。测试只要求在
Desktop 可见至少两个处理器时并行耗时小于串行耗时，不锁死某个倍率。

## 4. 正式实现仍缺少的部分

1. frontend/AST 需要开放 `synchronized` statement，并支持受控的 `Thread(Runnable)`、
   `start`、`join`、`sleep`、`currentThread` 与 `isAlive`；
2. semantic/IR 需要识别可跨线程捕获的值，把共享字段、静态字段和基础数组转换为宿主句柄；
3. lowering 需要把未同步读改写保持为分离操作，把 synchronized 区域转换成异常安全的 monitor
   enter/exit，不能意外把所有访问原子化；
4. 宿主调度器需要限制线程数、继承运行总超时，并在取消、超时、异常和 Runtime 创建失败时 join
   并清理全部子线程；
5. stdout/stderr 需要按事件到达顺序汇合，并继续遵守全局 UTF-8 输出配额；
6. Android 与 iOS 必须使用相同三项用例验证 QuickJS 线程亲和、真实并行和资源峰值。任一平台
   失败时都不能退回单 Runtime 交错后仍对外称为“并行”。

## 5. 当前产品决策：暂不支持线程

线程调度本身不是主要阻塞。真正昂贵的是让多个独立 QuickJS Runtime 正确观察同一份 Java
可变状态。不同 Runtime 不能直接交换 `JSValue` 或普通 JS 对象，因此生产实现至少需要以下一种
跨 Runtime 桥：

- 宿主 SharedHeap，以稳定 Handle 表示对象身份、字段、静态字段、数组和 monitor；
- SharedArrayBuffer 与固定内存布局，承载高频基础字段、原子量和等待信号；
- 所有共享访问转发给对象所属 Runtime，但这会串行化热路径，也无法自然覆盖完整对象图。

只实现 `Thread.start/join` 而复制捕获对象，会让常见的共享自增代码修改副本；把所有共享访问
逐次转发到 Kotlin `Map` 又会让字段热循环承担大量跨语言调用。这两种做法都不适合作为 Java
教学能力公开。quickjs-kt 的实验性原生上下文 API只能降低未来桥接实现的开销，不能让多个
Runtime 直接共享普通 JS 对象，也不能消除 Java 对象身份、内存可见性和 monitor 语义的工作量。

因此当前冻结以下边界：

1. 不向 Java builtin catalog 登记 `Thread`、原子类或并发容器；
2. 不开放 `synchronized`、`volatile`、`wait/notify`、ThreadLocal 或线程池；
3. 不以 Worker、Promise、协程或单 Runtime 交错执行模拟并对外宣称 Java 多线程；
4. 本文件和 Desktop 测试仅作为可行性证据，不属于正式语言包兼容承诺；
5. 线程相关源码继续在 frontend/semantic 阶段得到“不支持”诊断，不能退化为生成 JS 后的
   `undefined` 或宿主异常。

未来只有同时满足以下条件才重新开启生产实现：

1. 课程或产品出现明确、持续的 Java 多线程教学需求；
2. 先冻结 SharedHeap/Handle ABI、对象身份、引用写屏障和 monitor 生命周期设计；
3. Desktop、Android、iOS 都验证 Runtime 线程亲和、取消、超时和资源上限；
4. 高频共享基础字段使用 SharedArrayBuffer 或原生连续内存，不能依赖逐字段 Kotlin Map 回调；
5. 差分测试覆盖 `start/join` happens-before、丢失更新、`synchronized`、异常退出和构造期间逃逸。

## 6. 结论

Desktop 结果只证明“每线程独立 Runtime + 宿主共享堆/monitor”在技术上可行，不代表当前语言包
已支持 Java 多线程。考虑共享对象桥、性能和完整资源治理的实现成本，线程能力现阶段明确延期；
后续功能扩展继续聚焦单线程 Java 8 教学语法、类库、诊断和编辑器体验。
