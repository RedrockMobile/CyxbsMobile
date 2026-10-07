# Map KMP Page Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 在不改变地图页面 UI、交互和业务行为的前提下，稳定地图与地点详情 NavEntry 生命周期，并把地图底图缓存/下载决策抽成可测试的内部加载协调器。

**Architecture:** MapNavEntry 与 PlaceDetailNavEntry 保持为可同时存在的独立入口，分别使用 NAV_MAP 与 NAV_MAP_PLACE_DETAIL 作为稳定 content key；地点变化继续通过替换 PlaceDetailNavArgument 更新同一个详情 entry。地图入口只保留生命周期与顶层编排，适配布局移入独立 MapScreen.kt；底图缓存命中、版本比较和下载由 MapImageLoader 处理，Compose 只把加载结果映射到现有 dialog 和地图字节状态。

**Tech Stack:** Kotlin Multiplatform、Compose Multiplatform、Navigation 3、Kotlin Coroutines、kotlin.test、Gradle

**Spec:** cyxbs-pages/map/openspec/changes/refactor-map-kmp-page-architecture/design.md

## Global Constraints

- 只改 Android、iOS、Desktop 共用 KMP 实现；不得修改 JS/Wasm source set。
- 不改变 MapNavArgument、PlaceDetailNavArgument 的序列化协议。
- 不改变地图、地点详情、搜索、所有图片页、上传功能的 UI、动画、返回与事件行为。
- 地图与详情必须使用不同且稳定的 content key，因为两者需要在 overlay 场景同时渲染。
- 不共享 ViewModelStoreOwner，不把 ViewModel、CoroutineScope 或 Compose State 存入全局状态。
- 保持现有地图缓存文件、版本存储格式、HTTPS URL 转换和缓存清理行为。
- 生产逻辑保持在 cyxbs-pages/map 内部，不新增跨模块公共 API 或依赖。
- 新增可测试逻辑遵循 RED-GREEN-REFACTOR；测试断言真实输出和状态，不断言 mock 自身。

---

### Task 1: 固定地图与详情导航入口契约

**Files:**
- Modify: cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/ui/MapNavEntry.kt:102-131
- Verify: cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/ui/MapSheetNavEntry.kt:32-68,126-141
- Create: cyxbs-pages/map/src/commonTest/kotlin/com/cyxbs/pages/map/ui/MapNavEntryTest.kt

**Interfaces:**
- Consumes: AppNavEntry.getContentKey(argument)、NAV_MAP、NAV_MAP_PLACE_DETAIL。
- Produces: MapNavEntry.getContentKey(MapNavArgument): String = NAV_MAP；保留 PlaceDetailNavEntry.getContentKey(...)=NAV_MAP_PLACE_DETAIL 和 snapshot 内 argument 替换逻辑。

- [ ] **Step 1: 写导航契约失败测试**

新增测试，使用不同 placeSearch 参数实例调用真实 entry，断言地图入口始终返回 NAV_MAP，详情入口在不同 placeId 下始终返回 NAV_MAP_PLACE_DETAIL，并断言两种 key 不相等：

~~~kotlin
class MapNavEntryTest {
  @Test
  fun mapAndDetailEntriesKeepIndependentStableContentKeys() {
    val mapEntry = MapNavEntry()
    val detailEntry = PlaceDetailNavEntry()

    assertEquals(NAV_MAP, mapEntry.getContentKey(MapNavArgument()))
    assertEquals(NAV_MAP, mapEntry.getContentKey(MapNavArgument("二教")))
    assertEquals(NAV_MAP_PLACE_DETAIL, detailEntry.getContentKey(PlaceDetailNavArgument("1")))
    assertEquals(
      NAV_MAP_PLACE_DETAIL,
      detailEntry.getContentKey(PlaceDetailNavArgument("2", expanded = true)),
    )
    assertNotEquals(NAV_MAP, NAV_MAP_PLACE_DETAIL)
  }
}
~~~

- [ ] **Step 2: 运行测试确认 RED**

Run: ./gradlew :cyxbs-pages:map:desktopTest --tests '*MapNavEntryTest*' --console=plain

Expected: FAIL，因为 MapNavEntry 尚未覆盖 getContentKey，两个不同参数不会稳定返回 NAV_MAP。

- [ ] **Step 3: 添加最小稳定 key 实现**

在 MapNavEntry 中加入：

~~~kotlin
override fun getContentKey(argument: MapNavArgument): String = NAV_MAP
~~~

不改动详情 entry 的 key、overlay metadata、showPlaceDetail 的 snapshot 替换或事件发送逻辑。

- [ ] **Step 4: 运行测试确认 GREEN**

Run: ./gradlew :cyxbs-pages:map:desktopTest --tests '*MapNavEntryTest*' --console=plain

Expected: PASS，且输出中没有测试失败。

- [ ] **Step 5: 自审并提交**

~~~bash
git add cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/ui/MapNavEntry.kt \
  cyxbs-pages/map/src/commonTest/kotlin/com/cyxbs/pages/map/ui/MapNavEntryTest.kt
git commit -m "refactor(map): stabilize map navigation entry"
~~~

### Task 2: 拆分地图入口与页面编排

**Files:**
- Modify: cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/ui/MapNavEntry.kt:109-228
- Create: cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/ui/MapScreen.kt
- Modify: docs/map-naventry-communication-research.md

**Interfaces:**
- Consumes: Task 1 的稳定 MapNavEntry、现有 MapComposeViewModel、SearchViewModel、MapBottomSheetEntryHost(Boolean)。
- Produces: @Composable internal fun MapScreen(argument: MapNavArgument)；入口只创建作用域内 ViewModel、调用 MapScreen 和现有 dialogs；MapScreen 承担页面状态广播、返回处理、横竖屏布局和所有图片页切换。

- [ ] **Step 1: 记录重构前基线**

Run: ./gradlew :cyxbs-pages:map:desktopTest --console=plain

Expected: PASS。该任务只调整组合边界，不新增源文本式测试；现有测试与 Task 1 契约测试作为行为基线。

- [ ] **Step 2: 创建 MapScreen 顶层编排**

将 MapNavEntry.Content 中页面状态广播和横竖屏选择，以及现有 WH100vInfinityCompose / WH100v150Compose 的逻辑移至 MapScreen.kt。入口签名：

~~~kotlin
@Composable
internal fun MapScreen(argument: MapNavArgument) {
  val mapViewModel = viewModel<MapComposeViewModel>()
  val searchViewModel = viewModel<SearchViewModel>()
  // 保留 MapPageChanged snapshotFlow 和现有横竖屏分支。
}
~~~

提取共享返回处理和 AnimatedContent 壳，消除横竖屏中完全相同的返回/图片页切换代码；地图页内容通过一个 mapContent composable 参数保留横竖屏差异。不得改变现有动画方向、AllPictureCompose 参数、竖屏 MapContent 或横屏工具栏内容。

- [ ] **Step 3: 收窄 MapNavEntry 职责**

MapNavEntry.Content 保留 entry 生命周期内的 MapComposeViewModel factory 创建、dialogs 和上传状态，然后调用 MapScreen(argument)。不得将 VM 或 state 作为全局变量传递。

- [ ] **Step 4: 更新导航职责文档**

在 docs/map-naventry-communication-research.md 中明确：MapNavEntry 使用 NAV_MAP，PlaceDetailNavEntry 使用 NAV_MAP_PLACE_DETAIL，二者不同是为了同时渲染；地点变化替换详情 argument 并复用详情 entry/VM，不共享地图 VM；Web/JS/Wasm 不在本次重构与验证范围内。

- [ ] **Step 5: 编译与回归验证**

Run: ./gradlew :cyxbs-pages:map:desktopTest :cyxbs-pages:map:compileAndroidMain --console=plain

Expected: BUILD SUCCESSFUL，Task 1 测试继续通过。

- [ ] **Step 6: 自审并提交**

~~~bash
git add cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/ui/MapNavEntry.kt \
  cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/ui/MapScreen.kt \
  docs/map-naventry-communication-research.md
git commit -m "refactor(map): separate page orchestration"
~~~

### Task 3: 以 TDD 实现底图加载协调器

**Files:**
- Create: cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/model/MapImageLoader.kt
- Create: cyxbs-pages/map/src/commonTest/kotlin/com/cyxbs/pages/map/model/MapImageLoaderTest.kt
- Reuse unchanged: cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/util/MapImageHelper.kt
- Reuse unchanged: cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/model/MapDataRepository.kt

**Interfaces:**
- Consumes: MapImageDownloadResult、getImageFile()、MapDataRepository.getMapVersion/saveMapVersion、MapImageHelper.downloadImage。
- Produces: MapImageLoadRequest(url, version, forceDownload)、MapImageLoadResult.Success(bytes, updateAvailable)、MapImageLoadResult.Failure(cause)，以及 MapImageLoader.load(request, onDownloadStart, onProgress)。

- [ ] **Step 1: 写缓存命中与版本提示失败测试**

使用构造器注入内存边界函数，测试缓存非空且本地版本存在时不下载；版本相同返回 updateAvailable=false，版本不同返回相同缓存字节和 updateAvailable=true。断言真实 MapImageLoadResult.Success。

- [ ] **Step 2: 写首次下载、强制更新、失败和取消测试**

覆盖：缓存或版本缺失时下载；下载进度转为 0f..1f；isCached=true 才保存版本；forceDownload=true 忽略缓存；普通异常返回 Failure；CancellationException 原样抛出。

- [ ] **Step 3: 运行测试确认 RED**

Run: ./gradlew :cyxbs-pages:map:desktopTest --tests '*MapImageLoaderTest*' --console=plain

Expected: FAIL，生产类型尚不存在。

- [ ] **Step 4: 写最小加载协调器实现**

~~~kotlin
internal class MapImageLoader(
  private val readCachedImage: suspend () -> ByteArray? = { getImageFile() },
  private val readCachedVersion: () -> Long? = { MapDataRepository.getMapVersion() },
  private val downloadImage: suspend (String, (Long, Long) -> Unit) -> MapImageDownloadResult =
    { url, listener -> MapImageHelper.downloadImage(url, listener) },
  private val saveVersion: (Long) -> Unit = { MapDataRepository.saveMapVersion(it) },
) {
  suspend fun load(
    request: MapImageLoadRequest,
    onDownloadStart: () -> Unit,
    onProgress: (Float) -> Unit,
  ): MapImageLoadResult
}
~~~

非强制下载时只有“非空缓存 + 非空版本”才直接返回缓存；否则下载。仅 isCached=true 保存版本；捕获普通异常生成 Failure，单独重抛取消异常。

- [ ] **Step 5: 运行测试确认 GREEN 并重构**

Run: ./gradlew :cyxbs-pages:map:desktopTest --tests '*MapImageLoaderTest*' --console=plain

Expected: 全部 PASS。随后运行 ./gradlew :cyxbs-pages:map:desktopTest --console=plain，确保现有测试仍通过。

- [ ] **Step 6: 自审并提交**

~~~bash
git add cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/model/MapImageLoader.kt \
  cyxbs-pages/map/src/commonTest/kotlin/com/cyxbs/pages/map/model/MapImageLoaderTest.kt
git commit -m "refactor(map): extract image loading workflow"
~~~

### Task 4: 将 Compose 地图内容接入加载协调器

**Files:**
- Modify: cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/ui/MapNavEntry.kt:592-650（如果 Task 2 已将 MapCompose 移至 MapScreen.kt，则修改新位置）
- Reuse: cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/model/MapImageLoader.kt
- Verify unchanged: cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/widget/MapWidget.kt
- Verify unchanged: cyxbs-pages/map/src/{androidMain,iosMain,desktopMain,noWebMain,webMain,jsMain,wasmJsMain}/**/*MapImage*

**Interfaces:**
- Consumes: Task 3 的 MapImageLoader.load 与 MapImageLoadResult。
- Produces: Compose 加载绑定：下载开始显示现有进度 dialog，进度更新 downloadProgress，成功更新地图字节与版本提示，失败关闭进度并显示现有失败 dialog。

- [ ] **Step 1: 运行协调器测试作为接入前保护**

Run: ./gradlew :cyxbs-pages:map:desktopTest --tests '*MapImageLoaderTest*' --console=plain

Expected: PASS。

- [ ] **Step 2: 用协调器替换 produceState 内重复分支**

通过 remember { MapImageLoader() } 创建协调器。以 mapInfo.mapUrl、mapInfo.pictureVersion、viewmodel.isUpdateStart.value 构造请求；onDownloadStart 将进度置零并显示 progress dialog，onProgress 更新 downloadProgress。

Success 时设置 value = result.bytes、关闭 progress dialog，并在 result.updateAvailable 时打开 update dialog。Failure 时关闭 progress dialog、打开 download failed dialog，不覆盖旧地图字节。取消由协调器抛出，produceState 不吞掉。

移除 Compose 文件对 MapDataRepository、MapImageHelper、getImageFile 和普通异常分支的直接依赖。

- [ ] **Step 3: 检查平台渲染层未改变**

Run: git diff --name-only

Expected: 不包含 MapWidget.kt、任何 MapImageLoad.*、Android/iOS/Desktop actual、JS/Wasm 文件。

- [ ] **Step 4: 编译并运行测试**

Run: ./gradlew :cyxbs-pages:map:desktopTest :cyxbs-pages:map:compileAndroidMain --console=plain

Expected: BUILD SUCCESSFUL；加载协调器和导航契约测试通过。

- [ ] **Step 5: 自审并提交**

~~~bash
git add cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/ui/MapNavEntry.kt \
  cyxbs-pages/map/src/commonMain/kotlin/com/cyxbs/pages/map/ui/MapScreen.kt
git commit -m "refactor(map): bind screen to image loader"
~~~

只添加实际存在且已修改的文件；不要用空路径提交。

### Task 5: 完成集成验证与 OpenSpec 任务状态

**Files:**
- Modify: cyxbs-pages/map/openspec/changes/refactor-map-kmp-page-architecture/tasks.md
- Verify: 本计划前四个任务涉及的全部文件

**Interfaces:**
- Consumes: Tasks 1-4 的完整实现和测试。
- Produces: 通过的 map Desktop 测试、Android map 编译、无 Web/Wasm 改动证明，以及 OpenSpec 10/10 完成状态。

- [ ] **Step 1: 运行完整 map Desktop 测试**

Run: ./gradlew :cyxbs-pages:map:desktopTest --console=plain

Expected: BUILD SUCCESSFUL；现有 MapNavEventTest、MapDataRepositoryTest、新导航测试和加载协调器测试全部通过。

- [ ] **Step 2: 运行 Android map 编译**

Run: ./gradlew :cyxbs-pages:map:compileAndroidMain --console=plain

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 检查范围与错误**

Run: git diff --name-only "$(git merge-base zzx/bugfix/map_naventry HEAD)"..HEAD

Expected: 只有计划、OpenSpec 文档、地图 commonMain/commonTest/desktopTest 和导航职责文档；不得出现 jsMain、wasmJsMain、webMain 或平台 actual 改动。

使用 IDE diagnostics 检查新增/修改 Kotlin 文件，确认没有新增编译错误。

- [ ] **Step 4: 更新 OpenSpec checkboxes**

只有在对应实现和验证全部完成后，将 tasks.md 的 1.1-3.2 十项全部从 - [ ] 改为 - [x]。运行 openspec instructions apply --change refactor-map-kmp-page-architecture --json，Expected: state 为 all_done，进度 10/10。

- [ ] **Step 5: 提交计划与 OpenSpec 状态**

~~~bash
git add cyxbs-pages/map/docs/superpowers/plans/2026-09-30-map-kmp-page-architecture.md \
  cyxbs-pages/map/openspec/changes/refactor-map-kmp-page-architecture
git commit -m "docs(map): complete architecture change plan"
~~~

如果计划或 OpenSpec 文件已在更早任务提交，只提交剩余修改，禁止重复或空提交。
