---
name: cyxbs-cmp-reusable-components
description: >
  掌邮 (Cyxbs) 项目内 CMP 迁移的可复用组件与模式库。当用户在做 CMP 迁移、写到 Compose UI、
  遇到对话框 / 返回键拦截 / 日期选择 / 列表 / 图片 / 主题色等场景时使用。
  触发关键词（命中任一即应加载本 skill）：
  "CMP 迁移"、"迁移到 Compose"、"复用已有组件"、"项目里有现成的吗"、
  "BottomSheetCompose"、"底部弹窗"、"临时对话框"、"TodoBottomSheet"、
  "返回键拦截"、"NavigationBackHandler"、"backHandler 废弃"、
  "日历选择器"、"日期选择器"、"WheelSelectCompose"、"CalendarCompose"、
  "滚轮选择"、"时分选择"、"ChooseDialogCompose"、"确认弹窗"。
  本 skill 是「活文档」——后续 AI 在迁移过程中发现新可复用组件时应在对应分类下追加。
---

# CyxbsMobile CMP 迁移 - 可复用组件与模式库

## 维护指引

- 完成 CMP 迁移、发现项目内可复用的组件或踩到值得复用的关键坑时，应及时追加，让后续 AI 优先复用已有实现。
- 本 skill 只作为能力索引：记录组件能解决的问题、源码位置和无法从命名推断的关键坑；参数默认值、完整 API、实现步骤与边界行为统一查看源码 KDoc。单项说明原则上不超过 1～2 句，避免复制源码文档。
- 只有复杂且常用的能力才单独建立编号分类，并按「触发场景 / 源码位置 / 简要说明」组织；其余内容放入文末「其他待补充」，使用一行说明「名称 + 全限定名或路径 + 用途 + 关键坑或示例」。
- 修改本文档时使用 `Edit` 工具，不要通过新建项目内 skill 拆散内容；只有经验能够跨项目通用时，才在 `~/.claude/skills/` 新建独立 skill。

---

## 一、对话框（居中弹窗）

### `ChooseDialogCompose` — 通用 1~2 按钮弹窗

- **触发场景**：删除确认、完成确认、未保存退出确认、表单选择确认等居中弹窗
- **源码位置**：`cyxbs-components/view/src/commonMain/kotlin/com/cyxbs/components/view/ui/ChooseDialogCompose.kt`
- **说明**：优先用此组件，**不要用 `androidx.compose.material.AlertDialog`**。`showState` 是 `MutableState<Boolean>`；`negativeBtnText = null` 时只渲染一个按钮；按钮背景色默认取 `LocalAppColors.current.positive` / `.negative`，自动适配暗黑模式。同文件内还有 `ChooseDialogComposeContent` / `DialogTwoBtnCompose` / `DialogOneBtnCompose` / `DialogPositiveBtnCompose` / `DialogNegativeBtnCompose` 可单独复用。
- **todo 模块 thin wrapper**：`cyxbs-pages/todo/src/commonMain/.../ui/dialog/TodoConfirmDialog.kt` 包了 `title + message + confirmText + dismissText + onConfirm + onDismiss` 简化 API，其他模块类似需求可参考。

---

## 二、底部弹窗（Bottom Sheet）

### `BottomSheetCompose` + `BottomSheetState` — 持久 peek 抽屉

- **触发场景**：需要常驻底部 peek 高度、可拖拽展开的抽屉（如校车线路信息、地图地点详情）
- **源码位置**：`cyxbs-components/view/src/commonMain/kotlin/com/cyxbs/components/view/ui/bottomsheet/BottomSheet.kt`
- **配套 scene strategy**：`cyxbs-components/view/src/commonMain/.../ui/bottomsheet/BottomSheetSceneStrategy.kt`（用于 navigation3 overlay）
- **项目内使用示例**：`cyxbs-pages/schoolcar/src/commonMain/.../widget/CarInfoButtonSheet.kt`
- **说明**：`peekHeight` 控制常驻高度，稳定锚点与拖动/吸附状态分别对外暴露；具体状态 API、等待方式和中途改向语义查看 `BottomSheetState.kt` 的 KDoc。`bottomSheetDraggable()` 必须挂在 content 内子组件上才能响应拖拽。
- **导航栏适配**：组件支持根据父级剩余 Insets 自动补齐折叠高度、绘制底部占位并避让展开态内容；相关参数为 `navigationBarContent` 和 `navigationBarPaddingInContent`，具体默认值、自定义方式与行为边界请直接查看 `BottomSheet.kt` 中的 KDoc 和源码逻辑。
- **IME 关闭顺序**：启用 `dismissOnClickOutside` 时默认由 `dismissImeOnClickOutsideFirst` 保证键盘可见时第一次点击蒙层只收起键盘，键盘收起后再次点击才触发业务 `onDismissRequest`；无此需求时显式传 false。iOS 可见性由 utils 模块的 `IosImeInitialService` 在应用启动时全局监听 UIKit 通知，组件内不要重复注册。

### `TodoBottomSheet` — 临时对话框用法（包装模式）

- **触发场景**：要把 `BottomSheetCompose` 用作**临时弹出对话框**（如分类选择、日历选择、重复设置），用完即消失
- **源码位置**：`cyxbs-pages/todo/src/commonMain/kotlin/com/cyxbs/pages/todo/ui/dialog/TodoBottomSheet.kt`

---

## 三、日期选择

### `CalendarCompose` — 日历网格

- **触发场景**：年月日选择
- **源码位置**：`cyxbs-components/view/src/commonMain/kotlin/com/cyxbs/components/view/calendar/CalendarCompose.kt`
- **配套 state**：`cyxbs-components/view/src/commonMain/.../calendar/state/CalendarState.kt`（`rememberCalendarState`）
- **配套组件**：`CalendarMonthCompose` / `MonthTextCompose` / `WeekTextCompose` / `CalendarDateCompose`
- **说明**：`state.clickDate` 取选中日期（`com.cyxbs.components.config.time.Date` 的 `.year` / `.monthNumber` / `.dayOfMonth` 是属性）；`startDate = Today` 自然防选过去日期；默认 `Column(fillMaxSize).then(modifier)`，传 `Modifier.height(X.dp)` 可覆盖高度，推荐 260.dp（折叠态 1 周 + 标题）。

---

## 四、滚轮选择

### `WheelSelectCompose` — 通用滚轮

- **触发场景**：分类选择（学习/生活/其他）、数字选择（1..31 日）、星期选择（周一..周日）、时分选择
- **源码位置**：`cyxbs-components/view/src/commonMain/kotlin/com/cyxbs/components/view/wheel/WheelScrollCompose.kt`
- **配套背景**：`WheelSelectBackground`（带上下渐变遮罩，同文件）
- **说明**：`options` 必须是 `ImmutableList<String>`（`kotlinx.collections.immutable`），用 `persistentListOf(...)` 或 `list.toPersistentList()` 构造；`selectedLine` 是 `Animatable<Float, AnimationVector1D>`，取值用 `selectedLine.value.roundToInt()`。

### `CalendarCompose` + `WheelSelectCompose` 组合 — 日期 + 时分选择

- **触发场景**：老端 `CalendarDialog` 的 CMP 替代
- **组合示例**：`cyxbs-pages/todo/src/commonMain/kotlin/com/cyxbs/pages/todo/ui/dialog/CalendarPickerDialog.kt`
- **关键坑**：取当前时分用 `kotlin.time.Clock.System.now().toLocalDateTime(...)`（**注意是 `kotlin.time.Clock`，不是 `kotlinx.datetime.Clock`**）；时间格式化用 `h.toString().padStart(2, '0')`，不要用 JVM-only 的 `String.format`。

---

## 五、导航 / 返回键处理

### `NavigationBackHandler` — 替代废弃 `backHandler`

- **触发场景**：Compose 页面内拦截系统返回键（未保存弹确认、管理模式退出等）
- **源码位置**：`androidx.navigationevent.compose.NavigationBackHandler`（navigationevent 库）
- **废弃版**：`com.cyxbs.components.utils.compose.backHandler`（Modifier 扩展，已 `@Deprecated`）
- **项目内使用示例**：
  - `cyxbs-pages/map/src/commonMain/.../SearchCompose.kt`
  - `cyxbs-pages/map/src/commonMain/.../MapNavEntry.kt`
  - `cyxbs-pages/course/src/commonMain/.../FindCourseScreen.kt`
- **说明**：`isBackEnabled` 动态变化时自动重新注册；`NavigationEventInfo.None` 是默认值。

---

## 六、简单 KV 存储（≈ SharedPreferences）

### `defaultSettings` / `AccountSettings` / `PreferencesSettings` — 多平台 Key-Value 存储

- **触发场景**：保存简单键值（开关、上次选择、轻量缓存）。等价于安卓 `SharedPreferences`，基于 multiplatform-settings 库。
- **源码位置**：`cyxbs-components/config/src/commonMain/kotlin/com/cyxbs/components/config/sp/`（`SpTable.kt` / `AccountSettings.kt` / `PreferencesSettings.kt`）
- **说明**：
  - **`defaultSettings`**（`SpTable.kt`）：设备维度的通用 KV。通用 key 放这里，命名规范 `SP_模块名_作用名`（如果跨模块使用 key 常量需写在 `SpTable.kt` 中，模块内使用则命名不要太简单以防止重复）。
  - **`accountSettings` / `AccountSettings.get(stuNum)`**：按**当前登录人/指定学号**区分的 KV（未登录 stuNum 为 null）。需要随账号隔离的数据用它。
  - **业务独用命名空间**：用 `PreferencesSettings.get(key)` 拿一块独立命名空间；`AccountSettings` 就是继承 `PreferencesSettings` 按学号区分的范例。
  - **坑（务必注意）**：**桌面端 JDK `Preferences` 单个 value 有 8192 字节长度上限**，超长会抛异常。长数据（大 JSON、列表）建议使用 FileKit 保存到本地文件，或者按字段分段存储。

---

## 其他待补充

后续 AI 在做 CMP 迁移时若发现以下场景的可复用组件，应在对应分类下追加：

- **列表拖拽排序**：当前项目内无 Compose 实现。如后续引入 `sh.calvin.reorderable` 或自研，应追加。
- **左滑删除**：当前用 Material1 的 `IconButton` 直接显示删除按钮。如后续引入 `SwipeToDismiss` 或第三方，应追加。
- **图片加载**：`com.cyxbs.components.utils.extensions.ImageFromUrlCompose`（DiscoverPage、校历页用到），支持自定义 placeholder/error、`colorFilter` 和 `ImageRequest.Builder`；业务需要自定义缓存身份时，应直接在 builder 中同时设置 `memoryCacheKey` / `diskCacheKey`，校历页是 URL + 版本号的参考实现。
- **Toast**：`com.cyxbs.components.utils.extensions.toast`（Compose 内也能调）。
- **自定义下拉刷新与 iOS 回弹**：列表通过 `NestedScrollConnection` 驱动刷新头时，应设置 `LazyColumn(overscrollEffect = null)`，避免 iOS 默认回弹消费后续拖动；判断顶部的 `LazyListState` 必须绑定到该列表。参考 `SportNavEntry.kt` 的 `SportRecord`。
- **登录弹窗**：`com.cyxbs.components.config.login.rememberLoginDialogState` + `doIfLogin(function="签到") { ... }`。
- **主题色 / 暗黑模式**：`com.cyxbs.components.config.compose.theme.LocalAppColors.current.xxx`（如 `.bottomBg` / `.tvLv2` / `.whiteBlack`）。
- **点击无涟漪**：`com.cyxbs.components.utils.compose.clickableNoIndicator { ... }`。
- **对勾动画**：`cyxbs-pages/todo/src/commonMain/.../ui/main/CheckLineCompose.kt`（Canvas + Animatable 复刻老端 `CheckLineView`）。
- **教学周 / 学期日期**：`com.cyxbs.components.config.time.SchoolCalendar`（commonMain object）—— `getWeekOfTerm()` 当前教学周、`getFirstMonDay()` 开学第一天；配 `Num2CN.number2ChineseNumber()` 转中文。示例 `TodoWeekHeader.kt`。
- **Navigation3 返回与 Compose 数据生命周期**：栈内 `NavEntry` 返回时会重新进入 Composition，`remember` / `rememberCoroutineScope` / `LaunchedEffect` 不会因为 `contentKey` 固定而保留。
