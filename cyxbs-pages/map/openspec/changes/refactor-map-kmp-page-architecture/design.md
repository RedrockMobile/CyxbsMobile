# Design

## Context

地图主入口当前在 `MapNavEntry.kt` 中同时负责地图内容、横竖屏页面编排、图片页状态、详情/搜索浮层协调和底图下载。地点详情已经通过 `PlaceDetailNavEntry` 作为 overlay entry 展示，并使用固定 `NAV_MAP_PLACE_DETAIL` content key；切换地点时替换 `PlaceDetailNavArgument`，由 `PlaceDetailViewModel` 取消旧请求并加载新地点。

地图底图下载目前在 `MapCompose` 的 `produceState` 内完成：先读取平台缓存与版本号，再决定复用、提示更新或下载，下载结果写回缓存并更新进度状态。`MapImageHelper` 已封装跨平台 HTTP 下载和缓存文件写入，但页面仍承担流程判断及重复的强制更新分支。

约束是只改 Android、iOS、Desktop 共享 KMP 代码；Web（JS/Wasm）不参与本次迁移。UI、手势、导航行为、缓存格式和用户可见文案保持不变。

## Goals / Non-Goals

**Goals:**

- 让地图内容与地点详情浮层的 NavEntry 职责清晰，确保地图和详情可以同时存在于 overlay 场景中。
- 固定地图入口和详情入口各自的 content key；详情地点变化通过 argument 更新并复用同一详情 NavEntry/ViewModel。
- 将底图缓存命中、版本判断、下载、进度、缓存写入和失败结果收敛到可测试的内部加载流程。
- 保持现有初次加载、旧图显示、更新确认、取消、失败提示和页面返回语义。

**Non-Goals:**

- 不改变地点详情、搜索、所有图片页或上传功能的产品行为。
- 不修改 `MapNavArgument`、`PlaceDetailNavArgument` 的外部序列化协议。
- 不补齐 Web/Wasm 的地图渲染或文件实现。
- 不引入新的网络库、持久化格式或跨模块公共 API。

## Decisions

### 1. 保留两个独立 NavEntry，并分别使用稳定 key

`MapNavEntry` 固定返回 `NAV_MAP`，`PlaceDetailNavEntry` 继续固定返回 `NAV_MAP_PLACE_DETAIL`。详情仍通过 `MapOverlaySceneStrategy` 作为最后一个 overlay entry 叠加在地图上；地点切换只替换详情 argument，并在同一 snapshot 中完成，保留详情 VM。

选择独立 key 是因为地图和详情需要同时渲染。把两者强行共用一个 key 会让导航系统把它们视为同一内容，破坏 overlay 结构。这里复用的是各自入口在参数变化时的 NavEntry，而不是跨入口共享 key 或 ViewModel。

### 2. 以页面编排函数划分地图内容与浮层协调

保留现有 `MapNavEntry.Content` 作为生命周期边界，继续在其中创建 `MapComposeViewModel` 和 `SearchViewModel`。将地图主体、横竖屏布局、返回处理和 overlay host 组织为职责明确的内部 composable；`PlaceDetailNavEntry.Content` 只负责创建详情 VM、响应 argument 并绘制 BottomSheet。

不把地图 VM 放入详情入口，也不使用全局 Compose state 或共享 ViewModelStoreOwner。地图与详情继续通过已有 `MapNavEvent` 传递业务动作。

### 3. 抽象底图加载决策，保留 MapImageHelper 作为 I/O 层

新增地图模块内部的加载协调层（命名和文件位置在实现时遵循现有 map/util 或 map/model 组织），输入地图 URL、服务端版本和是否强制更新，输出加载中的状态、成功字节和失败结果。它负责：

1. 读取 `getImageFile()` 与 `MapDataRepository.getMapVersion()`；
2. 在缓存缺失或强制更新时调用 `MapImageHelper.downloadImage`；
3. 通过回调报告下载进度；
4. 仅在缓存写入成功时保存地图版本；
5. 将取消异常继续向上抛出，将普通异常转换为页面已有失败状态。

页面只收集该流程结果并映射到现有 progress/update/failed dialog 状态。平台 `MapImageLoad`、`MapWidgetCompose` 仍只负责解码、手势、缩放和平移，不混入下载策略。

选择内部协调层而不是扩展 `MapComposeViewModel`，是为了避免把 I/O 流程和地图交互状态继续耦合；选择复用 `MapImageHelper` 而不是重写下载，是为了保留现有 HTTPS 转换、缓存清理和平台 actual 行为。

### 4. 验证以行为等价为主

优先复用地图模块现有 Desktop 测试结构，为加载决策补充无需 UI 的状态/分支测试；执行 map desktopTest 和 Android 编译。Web/Wasm 不作为本次验收目标。

## Risks / Trade-offs

- [详情参数更新时 NavEntry 重建] → 保持详情固定 content key，并在一个 snapshot 内替换 argument；验证地点连续切换时 VM 未被替换且旧请求被取消。
- [地图加载抽象改变错误或进度时序] → 复用 `MapImageHelper`，对缓存命中、版本不一致、强制更新、下载失败和取消分别测试，并保持原有状态赋值顺序。
- [拆分页面组合导致返回或 overlay 顺序变化] → 不改变现有 back handler、`MapBottomSheetEntryHost` 和 `MapOverlaySceneStrategy`，完成 Android 编译及已有桌面测试。
- [跨平台 actual 差异] → 只调整 commonMain 的流程调用，不修改 Android/iOS/Desktop 的文件 API actual；Web/Wasm 文件保持不动。

## Migration Plan

1. 先固定地图入口 key，并整理地图/详情入口的编排边界。
2. 抽出底图加载协调层，将 `MapCompose` 的重复下载分支迁移到该层。
3. 补充或调整 map desktopTest，执行 Android 编译并检查导航与状态引用。
4. 如出现回归，可先恢复页面对协调层的调用，保留独立 NavEntry key 改动；加载层不改变持久化格式，便于单独回滚。
