# LOCAL-LOG-05：应用策略、兼容迁移与用户导出

Part of: [LOCAL-LOG-MAP](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/map.md>)

- 类型/标签：task / wayfinder:task、needs-triage
- 状态：planned，未实现
- Blocked by：[03](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/03-module-api.md>)、[04](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/04-storage.md>)

## 需求

应用主动注入初始化政策和生命周期；旧 utils 单向兼容；用户可以开启诊断、清理和主动导出脱敏日志。默认状态、诊断时长和文案需先从地图 Fog 定稿，不能默认为全部生产内容采集。

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

未开始。初始化现状与平台证据见[设计报告](</Users/generals/AndroidProject/CyxbsMobile/docs/research/cyxbs-log-module-design.md>)。
