# Java 受限真并行线程原型

> 状态：2026-08-18 完成 Desktop 技术原型；尚未接入 Java `Thread` API，也未完成 Android/iOS
> 闸门，因此不能对业务声明“已支持 Java 多线程”。

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

## 5. 结论

Desktop 结果证明“每线程独立 Runtime + 宿主共享堆/monitor”方向可行，值得继续做受限实现；
它没有证明完整 Java Memory Model，也没有开放跨线程普通对象图、集合、volatile、wait/notify、
ThreadLocal、原子类、线程池或优先级。

下一步若进入生产实现，应先完成最小 `Thread(Runnable)`、`start/join/sleep`、基础共享字段和
`synchronized` 的纵向闭环，再分别运行 Android/iOS 原型；不应从完整 JMM 或大型并发类库开始。
