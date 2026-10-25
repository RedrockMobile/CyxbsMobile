# 混合壳构建配置排查

## 补齐 KMP 链接链

`AppDelegate.swift` 导入 Kotlin 模块，不代表 Xcode 已纳入 framework 和桥接源码。检查主应用 target：

- `KmpInterfaceImpl.swift` 必须有文件引用，并加入主应用的 Compile Sources。
- Compile Sources 之前应有 `Compile Compose Multiplatform` 脚本阶段，关闭该阶段的 Based on dependency analysis，使每次 Xcode 构建都询问 Gradle 是否需要更新。
- 主应用 Debug / Release 的 `ENABLE_USER_SCRIPT_SANDBOXING = NO`，让 Gradle 能读取仓库、缓存和输出路径；无需改 Widget 的此设置。
- Debug / Release 的 `FRAMEWORK_SEARCH_PATHS` 保留原条目并追加 `$(SRCROOT)/../../multiplatform/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`，不要硬编码 SDK 版本或真机架构。

脚本内容：

```sh
set -e
cd "$SRCROOT/../../.."
./gradlew :cyxbs-applications:multiplatform:embedAndSignAppleFrameworkForXcode :cyxbs-applications:multiplatform:syncComposeResourcesForIos
```

不要为验证最新代码设置跳过 Kotlin 构建的环境变量。保留 Gradle 增量编译即可，无需对整个仓库反复 clean。

## 过期或遗漏的文件引用

若出现 `Build input file cannot be found`、`FinderTools` 资源不存在，或 `UICollectionView.ElementKindSection` 找不到，先核对 `project.pbxproj` 与磁盘源码是否一致。迁移后旧工程配置可能重新加入已经删除的原生页面，也可能遗漏抽出的共用代码。

- 用 CocoaPods 自带的 Ruby `xcodeproj` 解析主 target 的 Sources / Resources，检查 `PBXBuildFile.file_ref.real_path.exist?`；不要把 SDK 引用或构建生成的 framework 当作源码缺失。
- 缺失时先按文件名搜索当前仓库。已移动的文件修正 group/path；源码确已删除的文件仅移除失效构建条目，不恢复被迁移删除的旧业务代码，也不删除其他真实文件。
- `FYHCycleLabel.h/.m` 的现位置是 `CyxbsMobile2019_iOS/Tools/Encapsulation/TextCycleView/`，旧地图目录中的引用应移动到这里。
- `CyxbsMobile2019_iOS/Tools/Encapsulation/UICollectionView+ElementKindSection.swift` 仍被原生 WeDate 约课页使用，需要加入主应用 Compile Sources；不是 UIKit 自带类型。

Homebrew 安装 CocoaPods 时，系统 Ruby 可能找不到 `xcodeproj`。先查看 `pod` 启动脚本的 Ruby / `GEM_HOME` 配置，复用其环境；不要假设必须安装另一份 gem。结构修改后用 `plutil -lint <project.pbxproj>` 检查语法，再检查 target 中有无缺失或重复的编译输入。

## 确认最新 framework 已进包

本项目生成静态 Kotlin framework。`embedAndSignAppleFrameworkForXcode SKIPPED` 单独出现不代表构建失败：静态 framework 无需复制进 App 的 `Frameworks` 目录。应同时核对：

1. 当前平台的 `compileKotlinIosArm64`、`linkDebugFrameworkIosArm64`、`assembleDebugAppleFrameworkForXcodeIosArm64` 成功或被 Gradle 判定为最新。
2. `syncComposeResourcesForIos` 成功，App 包内有 `compose-resources`。
3. Xcode 的 Swift 编译和链接命令使用本轮 SDK 对应的 `build/xcode-frameworks/...` 目录。
4. 必要时在 framework 导出头或最终 App 二进制中核对本次改动的接口/符号。Debug 构建的 Kotlin 实现可能链接在 `<AppName>.debug.dylib`，可用 `xcrun nm` 检查，不能只检查很小的 App 启动可执行文件。

必须以完整 Xcode 构建末尾的 `BUILD SUCCEEDED` 判断混合壳成功；日志中 Gradle 的 `BUILD SUCCESSFUL` 只证明 Kotlin 部分完成。

## 外部应用调起排查

`devicectl device info apps` 默认仅列开发应用。检查 QQ、百度地图等 App Store 应用时加 `--include-default-apps`（或 `--include-all-apps`），不能根据默认列表为空断言未安装。

WebView 外链报“未安装”时检查是否用了 `UIApplication.canOpenURL`：它要求 scheme 出现在宿主 `LSApplicationQueriesSchemes` 中，未声明也会返回 false。若只是要打开链接，使用 `openURL(url:options:completionHandler:)` 并以回调结果提示失败，无需为每个网页可能使用的 scheme 扩充查询白名单。保留 URL 解析失败处理；不要把所有调起失败都断言成应用未安装。
