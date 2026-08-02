# Dynamic language

该模块保存动态语言能力的宿主核心，不暴露 QuickJS 等具体 JavaScript 引擎：

- `DynamicLanguageAdapter`：向编辑器提供稳定的高亮、补全与异常边界；
- `DynamicLanguageModuleGraph`：表示下载和依赖解析完成后的不可变内存 ESM 图；
- `internal/`：负责 JavaScript Runtime 协议调用，业务不应引用；

后端清单、npm 下载、完整性校验和版本解析规则仍在设计中，不属于当前模块的稳定协议。未来下载层完成
这些步骤后，只需构造 `DynamicLanguageModuleGraph` 交给适配器；Module Loader 回调内不得进行网络或
磁盘访问。
