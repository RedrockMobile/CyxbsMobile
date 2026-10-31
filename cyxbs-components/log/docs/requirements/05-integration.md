# LOCAL-LOG-05：应用策略、兼容迁移与用户导出

Part of: [LOCAL-LOG-MAP](./map.md)

- 类型/标签：task / wayfinder:task、needs-triage
- 状态：in_progress，代码接入完成，真机验收待执行
- Blocked by：[03](./03-module-api.md)、[04](./04-storage.md)

## 需求

应用主动注入初始化政策和生命周期；旧 utils 单向兼容；用户可以开启诊断、清理和主动导出脱敏日志。记录诊断日志默认开启，只有主进程且隐私已同意后才采集；用户显式关闭后保留选择，不自动上传。

## 范围

- 启动读取政策，处理首次同意、重启已同意、撤回、用户开关、账号切换；不只依赖 onPrivacyAgreed。
- Android 主/子进程范围与 iOS 宿主桥明确；前后台限时 flush，不依赖终止回调。
- 旧 log/logg/LogUtils/LogLocal 兼容转发，不同时运行两套文件 writer。
- 网络采集允许名单与限长；禁止默认 BODY/token/cookie/学号/业务标题，query/path/异常也过滤。
- Android 私有非备份目录、cache 导出与 FileProvider 临时授权；iOS Application Support 排除备份/保护策略和分享；desktop 保存文件；Web 标明内存能力。
- 用户开启/关闭/清理/导出状态清晰；导出 manifest 与临时副本限额；不自动上传。

## 验收

未同意/关闭状态符合定稿政策；撤回后旧日志不再写入；重启已同意路径能正确配置。旧调用无重复输出、无依赖环。导出只能访问受控副本、日志脱敏、可解析，失败和取消清理临时资源；明确外部分享副本不能由本应用撤回。

## 进度

已完成代码接入：

- `InitialManager` 在各平台启动时配置控制台策略；文件层默认开启，仅主进程、隐私已同意且用户未关闭时运行，撤回隐私后停止写入。
- Android 使用 `noBackupFilesDir/cyxbs-log` 与 `cacheDir/cyxbs-log-export`，后台 `onStop` flush；iOS 使用 Application Support/Caches，启动后标记日志目录排除备份，并监听进入后台；Desktop 使用 `~/.cyxbs`；Web 明确 Unsupported。
- 旧 `log`/`logg`/`LogUtils` 单向转发 `CyxbsLog`，旧 `LogLocal` writer 已删除；网络 BODY 仍只在 Debug Logcat，不进入可导出文件。
- 设置页提供“记录诊断日志”开关、清理和导出；Android FileProvider 临时读授权、iOS Share Sheet、Desktop 打开目录。分享面板弹出仅提示文件已生成，不宣称分享成功。
- 导出 JSONL 带 manifest；文件入队前做常见凭据/Cookie/邮箱/长数字兜底脱敏，日程诊断移除标题、分类名和服务端 info；不自动上传。

仍需 Android/iOS 真机验证后台/锁屏、权限与系统分享；需要产品复核最终隐私文案和允许采集字段。应用无法撤回用户已分享给外部应用的副本。
