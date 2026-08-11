# Dynamic language

该模块保存动态语言能力的宿主核心，不暴露 QuickJS 等具体 JavaScript 引擎：

- `DynamicLanguageManager`：发现 Catalog，并按语言 ID 或别名创建独立会话；
- `DynamicLanguageService`：直接向编辑器提供高亮与补全能力，并保留 npm 层原始异常；
- `js-bridge`：只保存端上与 Kotlin/JS 动态语言包共享的 Service 协议；
- `catalog`：从已登记语言 Project 自动生成纯 `catalog.json` 并发布到固定坐标
  `@cyxbs-mobile/language-catalog`，不携带 Kotlin/JS Runtime；
- `internal/`：桥接通用 npm Service Loader，业务不应引用。

Manager 只缓存解析、校验后的 Catalog 快照。Catalog 与语言包按 `latest` 加载，下载、依赖解析、
完整性校验及本地回退均复用 `code:npm`；只有语言实现会创建独立 Runtime 和字节码缓存，并由调用方
关闭 Service。

新增语言时，语言模块在自己的 `build.gradle.kts` 中调用 `npmJsLanguage { ... }` 保存元数据；
Catalog 模块只需把该 Project 加入 `generateDynamicLanguageCatalog(...)`。生成任务会自动读取
`npmJsPackage.packageName`、生成 JSON，并让语言包先于 Catalog 发布。
