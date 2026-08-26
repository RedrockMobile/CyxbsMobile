package com.cyxbs.functions.code.language.js.bridge

import com.cyxbs.functions.code.npm.js.bridge.NpmJsService
import com.cyxbs.functions.code.npm.js.bridge.NpmJsServiceInstance
import com.cyxbs.functions.code.npm.js.bridge.NpmJsResult
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/**
 * 动态语法分析返回的一段高亮区间。
 *
 * [styleIds] 使用动态语言包给出的稳定样式标识，区间采用 UTF-16 偏移，与 Kotlin 和
 * JavaScript 字符串位置保持一致。
 */
@Serializable
data class DynamicHighlightSpan(
  val from: Int,
  val to: Int,
  val styleIds: List<String>,
)

/** 动态高亮本次使用的语法树缓存路径。 */
@Serializable
enum class DynamicHighlightCacheMode {
  /** 没有可复用语法树，完整解析当前源码。 */
  FULL,

  /** 根据前后源码变更区间复用未受影响的旧语法树片段。 */
  INCREMENTAL,

  /** 源码与上次完全一致，直接复用高亮结果。 */
  EXACT,
}

/** 前后两份源码之间用于 Lezer 增量解析的单一最小变更区间。 */
@Serializable
data class DynamicHighlightChangedRange(
  val fromBefore: Int,
  val toBefore: Int,
  val fromAfter: Int,
  val toAfter: Int,
)

/**
 * 动态包内部高亮阶段的细粒度指标。
 *
 * 时间统一使用微秒，避免跨 Kotlin/JS、Native 和 JVM 传输 [kotlin.time.Duration]。客户端测得的
 * Service 往返时间减去 [parseMicroseconds] 与 [collectMicroseconds]，可近似观察桥接和序列化成本。
 */
@Serializable
data class DynamicHighlightMetrics(
  val cacheMode: DynamicHighlightCacheMode,
  val sourceLength: Int,
  val changedRange: DynamicHighlightChangedRange? = null,
  val reusableFragmentCount: Int = 0,
  val parseMicroseconds: Long,
  val collectMicroseconds: Long,
)

/** 动态高亮区间及其语言包内部性能指标。 */
@Serializable
data class DynamicHighlightResult(
  val spans: List<DynamicHighlightSpan>,
  val metrics: DynamicHighlightMetrics,
)

/** 动态语言包返回的单个补全候选。 */
@Serializable
data class DynamicCompletionItem(
  val label: String,
  val displayLabel: String? = null,
  val detail: String? = null,
  val info: String? = null,
  val type: String? = null,
  val boost: Int = 0,
  val apply: String? = null,
)

/**
 * 一次补全查询的结果。
 *
 * [from] 与 [to] 表示应用候选时要替换的源码区间；无可用补全时返回 `null`。
 */
@Serializable
data class DynamicCompletionResult(
  val from: Int,
  val to: Int,
  val options: List<DynamicCompletionItem>,
)

/**
 * 由 npm JavaScript 包实现的动态语言能力。
 *
 * 业务将 `DynamicLanguageService::class`、npm 包名和版本传给 `NpmJsServiceLoader.load` 获取端上
 * 代理，不直接访问生成类或 JavaScript Runtime。Kotlin/JS 发布模块只需提供一个实现本接口的
 * object，KSP 会生成分发器。
 *
 * 所有业务方法都返回 [NpmJsResult]：当前语言包缺少接口方法时失败值为
 * `NpmJsServiceMethodNotImplementedException`；参数或返回值不符合 JSON 协议、Runtime 不可用、
 * JavaScript 实现抛错或显式返回失败时，失败值为 `NpmJsServiceInvocationException`。bundle
 * 不存在、下载失败或入口初始化失败发生在实例创建之前，仍由
 * `NpmJsServiceLoader.load` 直接报告。只有 [CancellationException] 会绕过结果对象继续抛出。
 */
@NpmJsService
interface DynamicLanguageService : NpmJsServiceInstance {

  /**
   * 返回当前语言用于文件标签和目录树的矢量图标。
   *
   * 每个语言包必须实现本方法。客户端可按语言包版本缓存解析后的 Compose 矢量图；图标数据损坏
   * 时可以回退到通用文件标记，但缺少实现不属于正常业务状态。
   *
   * @return 与具体平台资源无关的 SVG 填充路径模型。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun fileIcon(): NpmJsResult<DynamicLanguageIcon>

  /**
   * 返回当前语言用于“新建项目”的默认模板。
   *
   * 模板随语言 npm 包一起发布，客户端不得再内置语言源码。实现必须返回一个可直接创建且能够被
   * 当前语言运行能力识别的最小项目；路径合法性和重复文件仍由客户端在写盘前复核。
   *
   * @return 当前语言的默认项目名、活动文件和初始源码。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun projectTemplate(): NpmJsResult<DynamicLanguageProjectTemplate>

  /**
   * 发现当前工作区可由编辑器直接启动的入口。
   *
   * Java 等语言可以返回工作区中的多个 `main`，客户端会在顶部运行按钮中提供选择，并在
   * [DynamicRunTarget.location] 对应行显示运行标记。JavaScript、Python 等按文件执行的语言应
   * 根据 [activeFilePath] 返回当前文件目标，不需要枚举整个工作区的每个文件。
   *
   * 本方法只分析入口，不编译或执行程序。源码变化后客户端可以重新调用；语言包应尽量复用
   * 已有增量语法树，避免为了刷新行号标记重复完成整套编译。
   *
   * @param workspace 包含未保存文本的完整同语言工作区。
   * @param activeFilePath 当前编辑器文件，用于按文件执行的语言选择默认目标。
   * @return 按语言定义排序的可运行入口；为空表示当前工作区没有可运行目标。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun runTargets(
    workspace: DynamicLanguageWorkspace,
    activeFilePath: String,
  ): NpmJsResult<List<DynamicRunTarget>>

  /**
   * 把当前语言工作区编译为端上统一执行器可加载的 ES Module 图。
   *
   * 语言包只负责语法、语义和代码生成，不得在自身的分析 Runtime 中执行用户程序。Java、
   * Python 等语言可以转译为 JavaScript，JavaScript 语言则可以只规范化并包装原始模块；端上会
   * 为返回的程序创建另一个独立 Runtime。
   *
   * @param request 完整工作区快照及语言无关入口位置。
   * @return 成功时包含可执行 Module 图；源码错误通过结构化诊断返回，不以异常表示。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun compile(request: DynamicCompilationRequest): NpmJsResult<DynamicCompilationResult>

  /**
   * 分析工作区中的指定文件，返回按 UTF-16 偏移排序的高亮区间、增量缓存路径及语言包内部耗时。
   *
   * 返回的性能指标既用于确认语言包是否复用了增量语法树，也可与端上测得的整体耗时组合，
   * 近似分析 JavaScript 桥接成本。
   *
   * @param workspace 包含未保存内容的完整工作区快照。
   * @param filePath 本次需要高亮的工作区相对路径。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun highlight(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
  ): NpmJsResult<DynamicHighlightResult>

  /**
   * 查询指定光标位置的补全候选。
   *
   * @param workspace 包含未保存内容的完整工作区快照。
   * @param filePath 光标所在文件的工作区相对路径。
   * @param position 光标 UTF-16 偏移。
   * @param explicit 是否由用户主动触发补全。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun complete(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
    explicit: Boolean,
  ): NpmJsResult<DynamicCompletionResult?>

  /**
   * 查询光标所在词法符号的定义。
   *
   * @param workspace 包含未保存内容的完整工作区快照。
   * @param filePath 光标所在文件的工作区相对路径。
   * @param position 光标 UTF-16 偏移；可位于标识符内部或紧邻标识符末尾。
   * @return 工作区中的符号定义；光标不在可索引符号上时返回 null。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun definition(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
  ): NpmJsResult<DynamicSymbolDefinition?>

  /**
   * 查询光标所在词法符号在工作区中的引用。
   *
   * 返回值不重复包含定义区间；语言包无法静态确认的动态属性访问不会被猜测为引用。
   *
   * @param workspace 包含未保存内容的完整工作区快照。
   * @param filePath 光标所在文件的工作区相对路径。
   * @param position 光标 UTF-16 偏移。
   * @return 符号及非定义引用；光标不在可索引符号上时返回 null。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun references(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
  ): NpmJsResult<DynamicSymbolReferencesResult?>

  /**
   * 为光标所在词法符号生成工作区安全重命名修改。
   *
   * 语言包会拒绝非法标识符、保留字和可能改变词法绑定关系的名称冲突。调用方必须确认所有文件
   * 仍与 [workspace] 快照一致，再一次性应用返回的全部修改。
   *
   * @param workspace 包含未保存内容的完整工作区快照。
   * @param filePath 光标所在文件的工作区相对路径。
   * @param position 光标 UTF-16 偏移。
   * @param newName 调用方期望的新标识符。
   * @return 可应用或带拒绝原因的结果；光标不在可重命名符号上时返回 null。
   * @throws CancellationException 调用协程被取消。
   */
  @Throws(CancellationException::class)
  suspend fun rename(
    workspace: DynamicLanguageWorkspace,
    filePath: String,
    position: Int,
    newName: String,
  ): NpmJsResult<DynamicRenameResult?>
}
