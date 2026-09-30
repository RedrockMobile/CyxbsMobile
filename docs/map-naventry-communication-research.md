# Map NavEntry 通信与职责拆分

## 约束

只支持单个 Map 页面实例。不共享 ViewModelStoreOwner，不保存全局 ViewModel、Scope 或 Compose State，不引入 sessionId。
本文件记录实际实现，替代早期只定义事件、未完成状态迁移的方案。
只调整 ViewModel 职责、通信和绑定，不改变现有布局、页面动画、搜索入口或弹层交互。Web/JS/Wasm 不在本次重构与验证范围内。

## 状态归属

| NavEntry | ViewModel | 所有状态与职责 |
| --- | --- | --- |
| MapNavEntry | MapComposeViewModel | 地图数据、缩放平移、锚点、分类、收藏展示、地图下载、原有图片页切换及图片快照、上传 dialog 状态及上传任务 |
| MapNavEntry | SearchViewModel | 原有竖屏搜索输入、结果、历史及搜索页切换 |
| SearchNavEntry | SearchViewModel | 宽屏搜索输入、结果、历史、搜索 sheet |
| PlaceDetailNavEntry | PlaceDetailViewModel | 当前地点、详情请求、收藏操作、详情 sheet |

三种 ViewModel 均直接继承 BaseViewModel。无公共地图 VM 基类，无互相持有实例。
MapNavEntry 使用 NAV_MAP，PlaceDetailNavEntry 使用 NAV_MAP_PLACE_DETAIL；二者使用不同 contentKey，以便同时渲染。地点变化只替换详情 argument，复用详情 entry/VM，不共享地图 VM。
同类型 VM 在不同 NavEntry 中是独立实例，只使用当前 entry 的 owner；一个 entry 内的功能组件可以获取该 entry 自己的 VM。
竖屏搜索仍嵌在主地图原有 AnimatedContent 中；宽屏搜索仍是 SearchNavEntry 弹层。
“所有图片”保留主地图中的页面切换与返回逻辑，通过 OpenAllPictures 接收地点 ID 和图片 URL 快照。MapNavEntry 只创建 entry 作用域的 MapComposeViewModel、显示 dialogs 并调用 MapScreen；MapScreen 获取同一 entry-scoped MapComposeViewModel 与 SearchViewModel，处理页面状态广播、返回、横竖屏布局和图片页切换。
上传 dialog 统一放在主地图入口，状态和任务归 MapComposeViewModel。详情入口通过 RequestPhotoUpload 事件请求打开，不获取主地图 VM。大图预览继续使用现有 MapShowPictureNavEntry。

## 正确的作用域

原 BottomSheetSceneStrategy 的 stateProvider 在 entry.Content() 外执行，不适合在该回调内获取 entry 的 ViewModel。
地图使用模块内 MapOverlaySceneStrategy，只负责叠加 entry.Content()；VM、BottomSheetCompose 和内容都在 entry 内创建。
LocalBottomSheetScope 仅传递当前 sheet 的拖拽能力，不覆盖 LocalViewModelStoreOwner。
地图控制器不再持有 BottomSheetState，只执行地图与锚点动画。

## 事件协议

静态 SharedFlow 定义在 MapComposeViewModel.companion object。replay 为 0，不使用 DROP_OLDEST，也不缓存待恢复事件。
发送使用挂起 emit，订阅在所属 ViewModel 的 viewModelScope 内启动，ViewModel 清除时自动取消。

| 事件 | 发送方 | 接收方 |
| --- | --- | --- |
| SelectPlace(place) | SearchViewModel | MapComposeViewModel，转换为地图 UI 事件 |
| ShowPlaceDetail(placeId, expanded) | 地图导航协调函数 | 已存在的 PlaceDetailViewModel |
| CollapsePlaceDetail | 地图 UI 事件处理 | PlaceDetailViewModel |
| HidePlaceDetail | 点击地图空白 | PlaceDetailViewModel |
| CollapseSearch | 地图定位、地点或分类操作 | SearchViewModel |
| CollectionsChanged | 详情收藏操作成功 | MapComposeViewModel，重新读取仓库收藏数据 |
| OpenAllPictures(placeId, images) | PlaceDetailViewModel | MapComposeViewModel，保存本入口图片快照并切换原有图片页 |
| RequestPhotoUpload(placeId) | PlaceDetailViewModel | MapComposeViewModel，记录上传地点并打开上传 dialog |
| MapPageChanged(picturesVisible, searchVisible) | 主地图入口的原有页面状态观察 | 详情和搜索 VM，各自更新弹层是否渲染 |

事件只携带业务参数，不传 VM、owner、lambda、Scope 或可变 Compose State。
expanded 为 true 表示展开，false 表示折叠，null 表示保持当前高度（隐藏时恢复 peek）。
同一个 VM 的用户输入到 UI 动画仍使用实例级 MapUiEvent；它不负责其他入口的状态。
MapPageChanged 是状态发生变化时的通知，静态流不保存当前页；接收方仅在自己的 VM 内维护显示条件。
事件订阅不能在自身 collect 回调里同步向同一个无缓冲流 emit，避免等待自身接收导致挂起。后续动作通过 VM 作用域中的独立任务发送。

## 初次打开与后续更新

1. 主地图先订阅自己的 UI 事件，然后执行首次聚焦。
2. 首次展示详情直接导航到 PlaceDetailNavArgument(placeId, expanded)，不向尚不存在的 VM 发初始化事件。
3. 详情入口先创建 VM，再按导航参数加载数据；不因详情为空而阻止创建 VM。
4. 已有详情时更新导航参数，并向活动 VM 发送 ShowPlaceDetail。
5. 更新参数使用固定 contentKey，在一个 Snapshot 内替换，保留同一详情 VM；导航参数同步记录最新地点。
6. 从图片预览返回时，地点没有改变就不重新初始化 sheet，以保留展开状态。
7. 切换地点取消上一次详情请求，防止旧结果覆盖新地点。

搜索选择通过常驻地图 VM 接收。竖屏关闭原有内嵌搜索页；宽屏搜索保留，收到折叠命令。
详情与搜索的展开、折叠和隐藏由各自 VM 执行，主地图只发送动作。

## 收藏、上传与平台能力

收藏操作成功后更新详情自己的列表和仓库，再通知地图刷新其收藏展示，不共享列表对象。
图片上传捕获启动时的 placeId，避免上传过程中当前地点变化导致照片提交到其他地点。
上传计数按批次重置，上传状态通过 finally 结束。
Android/iOS 外部地图导航抽为 expect/actual 函数 openMapNavigation，不再为了平台功能继承地图 VM。

## 验证

MapNavEventTest 覆盖：
- 订阅前的事件不重放；
- 多个活动订阅者按顺序收到连续命令；
- 取消订阅后不再收到事件。

本次限定验证范围：
- map desktopTest；
- map Android 编译。

按本次要求不执行模拟器或其他 UI 验证。编译和事件测试不等同于堆内存泄漏实测。
