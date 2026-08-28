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

> **维护原则**：每次做完一个 CMP 迁移任务、发现项目里有可复用组件时，应在对应分类下追加，
> 让下一个做迁移的 AI 直接复用，而不是重复造轮子或引入第三方库。
> 追加时按「触发场景 / 源码位置 / 简要说明」三段式。

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
- **源码位置**：`cyxbs-components/view/src/commonMain/kotlin/com/cyxbs/components/view/ui/BottomSheet.kt`
- **配套 scene strategy**：`cyxbs-components/view/src/commonMain/.../ui/BottomSheetSceneStrategy.kt`（用于 navigation3 overlay）
- **项目内使用示例**：`cyxbs-pages/schoolcar/src/commonMain/.../widget/CarInfoButtonSheet.kt`
- **说明**：`peekHeight` 控制常驻高度；`expand()` / `collapse()` / `hide()` 三个状态；`bottomSheetDraggable()` 必须挂在 content 内子组件上才能响应拖拽。

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
  - **坑（务必注意）**：**桌面端 JDK `Preferences` 单个 value 有 8192 字节长度上限**，超长会抛异常。长数据（大 JSON、列表）必须**分段保存**——范例 `SplitSettingsTodoLocalDataSource.kt` + `TodoSettingsKeys.kt`（按 chunk index 分片存 + 索引分片）。

---

## 其他待补充

后续 AI 在做 CMP 迁移时若发现以下场景的可复用组件，应在对应分类下追加：

- **列表拖拽排序**：当前项目内无 Compose 实现。如后续引入 `sh.calvin.reorderable` 或自研，应追加。
- **左滑删除**：当前用 Material1 的 `IconButton` 直接显示删除按钮。如后续引入 `SwipeToDismiss` 或第三方，应追加。
- **图片加载**：`com.cyxbs.components.utils.extensions.ImageFromUrlCompose`（DiscoverPage 用到），可补充参数和占位图模式。
- **Toast**：`com.cyxbs.components.utils.extensions.toast`（Compose 内也能调）。
- **登录弹窗**：`com.cyxbs.components.config.login.rememberLoginDialogState` + `doIfLogin(function="签到") { ... }`。
- **主题色 / 暗黑模式**：`com.cyxbs.components.config.compose.theme.LocalAppColors.current.xxx`（如 `.bottomBg` / `.tvLv2` / `.whiteBlack`）。
- **点击无涟漪**：`com.cyxbs.components.utils.compose.clickableNoIndicator { ... }`。
- **对勾动画**：`cyxbs-pages/todo/src/commonMain/.../ui/main/CheckLineCompose.kt`（Canvas + Animatable 复刻老端 `CheckLineView`）。
- **教学周 / 学期日期**：`com.cyxbs.components.config.time.SchoolCalendar`（commonMain object）—— `getWeekOfTerm()` 当前教学周、`getFirstMonDay()` 开学第一天；配 `Num2CN.number2ChineseNumber()` 转中文。示例 `TodoWeekHeader.kt`。

---

## 维护指引（给未来 AI 的）

### 何时追加
- 做完一个 CMP 迁移任务
- 发现项目里已有可复用的 Composable / Modifier 扩展 / 工具函数
- 或者踩过某个坑（依赖、参数类型、动画时序）下次不想再踩

### 如何追加
1. **判断放哪**：只有**复杂且常用**的东西（不限 UI 组件——对话框 / 底部弹窗 / 日历 / 滚轮等组件，也包括 KV 存储这类常用基础设施）才单独列编号标题分类；其余一律放进文末「其他待补充」，一行 bullet 即可，细节让后续 AI 自己看类方法，别在文档里堆全量 API。
2. 单独分类按三段式写：**触发场景 / 源码位置 / 简要说明**；「其他待补充」里只写「名称：全限定名 + 一句话作用 + 关键坑 + 示例路径」。
3. 用 `Edit` 工具在本文件对应位置追加
4. **不要新建 skill**，避免 skill 泛滥

### 何时新建 skill
- 只有当经验**跨项目通用**（不限于 CyxbsMobile）时，才在 `~/.claude/skills/` 新建
- 项目内特定经验永远放在本文件
