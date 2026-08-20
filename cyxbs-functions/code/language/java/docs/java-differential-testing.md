# Java javac/java 差分测试

## 目标

差分测试使用完全相同的 Java 8 源码，分别交给当前 JDK 的 `javac --release 8`/`java` 与
项目内 `JavaToJavaScriptCompiler`/Node 执行，再比较：

- 编译成功或失败；
- 可跨编译器稳定比较的诊断类别；
- stdout、stderr；
- 未捕获异常的 Java 简单类名。

它用于发现“示例能运行、但 Java 语义边界不同”的问题，不替代 compiler 各层的聚焦单元测试。

## 当前覆盖

当前共 300 个 fixture。测试会强制检查总数不低于 300、ID 唯一、分类集合固定，并要求每个分类
至少包含 20 项，防止后续删除语料或通过重复同类案例凑数量。300 项已经形成覆盖主要教学语法、
常用类库与关键错误路径的稳定基线；后续优先根据新缺陷定向增加，而不再单纯追求总数。

| 分类 | 数量 | 主要范围 |
| --- | ---: | --- |
| `control-flow` | 28 | 循环、break/continue、switch、条件表达式、enhanced-for |
| `numeric` | 26 | 整数溢出、long、浮点、移位、窄化与数值提升 |
| `array` | 29 | 默认值、多维/部分分配、协变、越界、varargs |
| `text-and-wrapper` | 30 | UTF-16 String、StringBuilder、包装缓存与拆装箱 |
| `collection` | 33 | List/Set/Map/Iterator、别名、视图、null 与对象身份 |
| `generic-and-overload` | 33 | 泛型推断、边界、通配符、继承代换、重载阶段 |
| `object-model` | 29 | 构造器链、字段、虚分派、类初始化、接口 default/static |
| `exception` | 28 | checked/runtime、finally、multi-catch、受控资源 |
| `functional-and-enum` | 22 | lambda、四类方法引用、enum 初始化与接口分派 |
| `io-and-multi-file` | 22 | Scanner、stdout/stderr、多文件与跨文件泛型调用 |
| `compiler-diagnostic` | 20 | 未解析符号、类型不匹配、歧义、final、missing return |

旧式单入口目录的分类集中维护在
`src/javacDifferentialTest/cases/coverage.properties`；矩阵 suite 的分类直接写在 `entries.tsv`。

## 两种语料格式

### 独立 case

适合编译失败、运行时未捕获异常或需要独立多文件布局的语料：

```text
301-example/
├── case.properties
└── src/demo/Main.java
```

`case.properties` 声明 `id`、`category`、入口类、入口方法、descriptor，以及可选的
`standardInputBase64`。旧 100 项由 `coverage.properties` 统一提供分类，新 case 应优先直接声明。

### 共享源码矩阵 suite

适合一组语法结构相近、都能通过 javac 编译的运行结果语料：

```text
301-310-example-matrix/
├── case.properties
├── entries.tsv
└── src/demo/Main.java
```

`entries.tsv` 每行依次为：ID、分类、入口方法、descriptor、可选标准输入 Base64。各列使用 Tab
分隔。suite 只复用一次 javac 编译产物；每个入口仍使用新的 ClassLoader、JavaScript Function、
标准流与 Scanner 输入，因此静态字段和运行状态不会跨 fixture 泄漏。

同一 suite 中任意源码编译失败会使整个 suite 成为失败基准，因此预期编译失败的语料必须使用
独立 case。当前 Java allowlist 仍拒绝嵌套类型，测试辅助类应写成同 package 顶层类型或独立文件。

## 运行方式

只重新生成 javac/java 参考 fixture：

```shell
./gradlew :cyxbs-functions:code:language:java:generateJavacDifferentialFixtures --rerun-tasks
```

运行 Java 模块完整测试（包含全部差分 fixture）：

```shell
./gradlew :cyxbs-functions:code:language:java:jsNodeTest
```

差分测试会一次汇总全部不一致项。修复时应先判断是语料超出公开 allowlist，还是实现与 Java 8
语义确有差异；不能为了让测试变绿而把 JavaScript 动态行为当作 Java 语义兜底。
