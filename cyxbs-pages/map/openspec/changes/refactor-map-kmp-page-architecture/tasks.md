# Tasks

## 1. 导航入口与页面职责整理

- [x] 1.1 为 `MapNavEntry` 增加稳定的地图 content key，并核对 `PlaceDetailNavEntry` 的独立 key、overlay metadata 和 argument 替换逻辑；通过代码审查确认地图与详情可同时存在且连续切换地点不会新建详情 ViewModel。
- [x] 1.2 将 `MapNavEntry` 内的地图内容、横竖屏编排、返回处理和浮层 host 整理为职责清晰的内部 composable，保持搜索、所有图片页、返回键和页面动画行为；通过 Android 编译验证引用和组合层级完整。
- [x] 1.3 保持 `PlaceDetailNavEntry` 只负责详情 VM、argument 初始化和 BottomSheet 绘制，验证地点切换时旧详情请求取消、新地点参数生效，且详情在地图图片页或搜索页显示时遵循现有隐藏规则。
- [x] 1.4 更新地图导航职责说明文档，记录稳定 key、argument 更新和 ViewModel 作用域；通过文档检查确认不引入跨入口共享状态或 Web 范围。

## 2. 地图底图加载流程抽象

- [x] 2.1 定义 map 模块内部的底图加载结果和请求协调接口，覆盖缓存命中、缓存缺失、版本不一致、强制更新、下载进度、取消和失败结果；通过 Kotlin 编译确认接口仅依赖 map 模块现有类型。
- [x] 2.2 将 `MapCompose` 中的缓存读取、版本判断、下载、缓存写入和重复分支迁移到加载协调层，继续调用 `MapImageHelper` 与 `MapDataRepository`，保持原有缓存格式和版本保存条件；通过代码审查核对所有异常和取消路径。
- [x] 2.3 将加载结果绑定回现有进度、更新确认和失败 dialog 状态，保持旧图优先显示及强制更新语义；通过 map desktopTest 覆盖缓存命中、首次下载、更新下载和下载失败分支。
- [x] 2.4 保持 `MapWidgetCompose` 与各平台 `MapImageLoad` 只处理图片渲染、手势和状态回调，不修改 Android/iOS/Desktop actual；通过变更检查确认 JS/Wasm 文件未被纳入改动。

## 3. 集成验证

- [x] 3.1 运行 map 模块 Desktop 测试并修复导航事件或加载状态回归，验证现有地图详情、收藏、图片和上传事件协议仍可编译运行。
- [x] 3.2 执行 Android map 模块编译，检查 KMP commonMain 与 Android source set 的依赖和 ViewModel 作用域无误；记录未执行 Web/Wasm 验证的范围。
