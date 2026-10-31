---
name: cyxbs-ios-kmp-hybrid-build
description: 编译、签名、安装并启动掌上重邮的 KMP/CMP 混合 iOS 应用到真机或模拟器。当用户要求“编译混合工程 KMP 到 iOS”“安装 KMP 混合版 iOS”或诊断该混合壳构建时使用；不用于纯 KMP 示例工程。
---

# 掌上重邮 KMP 混合 iOS 构建

## 目标工程

只构建完整混合壳：

`cyxbs-applications/pro/iosApp/CyxbsMobile2019_iOS.xcworkspace`

不要把 `cyxbs-applications/multiplatform/iosApp` 当作交付目标；它是纯 KMP/CMP 示例壳。

混合壳的有效入口应满足：

- `CyxbsMobile2019_iOS/AppDelegate.swift` 导入 `CyxbsApplicationsMultiplatform`；
- 启动时调用 `IOSAppKt.doInitApp(impl:)`；
- 根控制器来自 `IOSAppKt.MainViewController()`，并用原生导航控制器承接未迁移页面。

构建前检查以上入口和 Xcode target 的 Build Phases。必须存在调用 `:cyxbs-applications:multiplatform:embedAndSignAppleFrameworkForXcode` 的脚本，或有等价的 KMP framework 链接机制。若缺失且用户已授权修复构建配置，按下方排查文档补齐后继续；否则说明混合壳构建链不完整。不要静默编译成纯原生包。

遇到 KMP 构建阶段缺失、Swift 找不到类型、文件已删除但 Xcode 仍在编译、或不确定是否链接了最新 Kotlin 时，读取 [构建配置排查](references/build-troubleshooting.md)。保留工作区现有签名及其他改动，不要用 HEAD 整份覆盖 `project.pbxproj`。

## 设备选择

用户明确真机或模拟器时直接完成对应分支，不在构建前重复确认。未说明时只问一句：`要部署到真机还是 iOS 模拟器？`

- 真机：用 `xcrun devicectl list devices` 查找 `available (paired)` 的设备。当前会话已有目标则沿用；仅有一台可用真机时直接选择；多台时让用户指定。
- 模拟器：用 `xcrun simctl list devices available` 查找 iPhone。优先已 boot 的设备；否则仅有一个合理候选时直接 boot，多台候选无法判断时让用户指定，并用 `xcrun simctl bootstatus <UDID> -b` 等待启动完成。

选定目标后持续执行到构建、安装、启动和进程核验完成。目标不可用时报告阻塞，不切换到另一类设备，也不擅自删除现有 App。

## 构建前检查

1. 在仓库根目录记录 `git rev-parse HEAD` 和 `git status --short`。构建当前 checkout；除非用户明确要求同步或合入，不执行 `pull`、`merge`、`rebase` 或切分支。
2. 在 `cyxbs-applications/pro/iosApp` 下确认 `Podfile`、`CyxbsMobile2019_iOS.xcworkspace` 和 scheme `CyxbsMobile2019_iOS` 存在。始终构建 workspace，不构建 `.xcodeproj`。
3. 确认 Xcode、Gradle 和 CocoaPods 可用。每次部署都在 `cyxbs-applications/pro/iosApp` 执行与目标匹配的 Pod 安装，而不是根据旧 Pods 或 DerivedData 猜测当前 slice：
   - 真机：`IS_SIMULATOR=0 pod install`
   - 模拟器：`IS_SIMULATOR=1 pod install`
4. Pod 安装成功后再构建。高德地图、Bugly 等预编译库的 arm64 device/simulator slice 互斥；切换目标时必须重新执行对应命令。
5. 若 Xcode/Clang 报 YYKit 的 `Chained comparison 'X < Y < Z'`，先确认 `Podfile` 的 `post_install` 已给 Pods 追加 `-Wno-parentheses`，再重新执行对应 Pod 安装。只修 Podfile，禁止直接编辑 `Pods/YYKit`，因为生成源码会被覆盖。

## 签名与 App Groups

仓库当前 checkout 的 Xcode build settings、主应用/Widget entitlements 和共享容器常量是唯一配置来源。真机部署前用 `xcodebuild ... -showBuildSettings` 读取实际解析值，并交叉核对：

- 主应用和 Widget 使用同一 `DEVELOPMENT_TEAM`，且 `CODE_SIGN_STYLE = Automatic`。
- Widget Bundle ID 以主应用 Bundle ID 为前缀。
- 主应用、Widget entitlements 与 Swift 共享容器常量使用同一个 App Group。当前仓库基线为 `group.com.mredrock.cyxbs.widget.3m9757c25s`；若 checkout 已有一致的正式变更，以该 checkout 为准。

**禁止自动改写** `DEVELOPMENT_TEAM`、主/Widget Bundle ID 或 App Group。不得从旧 DerivedData、历史安装包或 `security find-identity` 显示名推断这些值；证书名称括号内的标识也不等于 App 的 `TeamIdentifier`。若自动签名或 profile 拒绝仓库配置，保留原值，报告 Apple 返回的具体错误、target 和标识；只有用户明确授权修改签名配置后才能改。

真机构建使用 `-allowProvisioningUpdates -allowProvisioningDeviceRegistration`。构建后以产物为准：`codesign -dvvv` 的 `TeamIdentifier`，以及 `codesign -d --entitlements :-` 解析出的 `application-identifier` 和 `com.apple.security.application-groups` 必须在主应用与 Widget 间一致。必要时再解析各自 `embedded.mobileprovision` 交叉验证。

## 构建、安装与启动

所有命令从 `cyxbs-applications/pro/iosApp` 运行，scheme 固定为 `CyxbsMobile2019_iOS`。每次调用用 `mktemp -d` 创建独立 DerivedData；同一轮故障修复后可复用。长时间的 Pod/Gradle/Xcode 命令必须持续报告进度。

### 真机分支

```bash
DERIVED_DATA="$(mktemp -d /tmp/cyxbs-ios-hybrid-device.XXXXXX)"
xcodebuild \
  -workspace CyxbsMobile2019_iOS.xcworkspace \
  -scheme CyxbsMobile2019_iOS \
  -configuration Debug \
  -destination "id=<UDID>" \
  -derivedDataPath "$DERIVED_DATA" \
  -allowProvisioningUpdates \
  -allowProvisioningDeviceRegistration \
  build
APP="$DERIVED_DATA/Build/Products/Debug-iphoneos/掌上重邮.app"
```

构建成功后按顺序完成：

1. 确认完整 Xcode 构建以 `BUILD SUCCEEDED` / exit code 0 结束；Gradle 的 `BUILD SUCCESSFUL` 不能代替该判断。
2. 确认 `APP` 存在，读取主应用和 `PlugIns/CyxbsWidgetExtension.appex` 的 Bundle ID、`codesign -dvvv` 与 `codesign -d --entitlements :-`。两者 `TeamIdentifier` 和 App Group 必须匹配仓库配置。
3. 用 `xcrun devicectl device install app --device <UDID> "$APP"` 安装。
4. 用 `xcrun devicectl device process launch --device <UDID> --terminate-existing <bundle-id>` 启动。
5. 用 `xcrun devicectl device info processes --device <UDID>` 确认主应用进程未立即退出；不要使用当前 `devicectl` 不支持的 `--hide-default-apps`。

### 模拟器分支

```bash
DERIVED_DATA="$(mktemp -d /tmp/cyxbs-ios-hybrid-simulator.XXXXXX)"
xcodebuild \
  -workspace CyxbsMobile2019_iOS.xcworkspace \
  -scheme CyxbsMobile2019_iOS \
  -configuration Debug \
  -destination "platform=iOS Simulator,id=<UDID>" \
  -derivedDataPath "$DERIVED_DATA" \
  build
APP="$DERIVED_DATA/Build/Products/Debug-iphonesimulator/掌上重邮.app"
xcrun simctl install <UDID> "$APP"
xcrun simctl launch --terminate-running-process <UDID> <bundle-id>
```

以 `simctl launch` 返回的 PID 和模拟器中的存活进程确认拉起成功。模拟器不执行真机 provisioning profile 校验，但仍须核对 Bundle ID、KMP framework 架构和 Compose 资源。

### KMP 产物判定

本项目的 `CyxbsApplicationsMultiplatform.framework` 是静态 framework。它应由本轮对应平台的 `compileKotlin*`、`linkDebugFramework*`、`assembleDebugAppleFrameworkForXcode*` 和 `syncComposeResourcesForIos` 生成并参与 Xcode 链接；用 `file`、`lipo -archs` 和本轮链接日志核对架构。**不要要求它出现在最终 App 的 `Frameworks` 目录**，也不要因 `embedAndSignAppleFrameworkForXcode SKIPPED` 单独出现就判定失败。详细判定见 [构建配置排查](references/build-troubleshooting.md)。

若安装提示同 Bundle ID 的旧 App 来自另一团队，iOS 不允许覆盖升级。说明卸载会清除该 App 本地数据，只有取得用户明确确认后才执行卸载；不得通过改 Bundle ID 绕过。

## 验证与报告

报告必须包含：构建提交 SHA、实际部署目标、Bundle ID、构建/安装/启动结果，以及是否验证 KMP framework 与 App Group profile。

若含未提交修改，注明“提交 SHA + 工作区修改”。安装及启动成功只证明部署成功；只有实际观察到对应页面与交互结果，才能报告业务验收通过。可用 `devicectl device info processes` 核对安装路径和存活进程；支持时用 `devicectl device capture screenshot --device <UDID> --destination <path.png>` 观察页面。截图若显示其他 App，不能作为本 App 页面验证的证据。

当用户验证某个 Kotlin 改动时，先执行 `git merge-base --is-ancestor <commit> HEAD`。若返回假，直接说明当前安装包不含该提交，先更新构建源再安装。不要仅凭 App 的版本号判断源码是否最新。

针对课表 `Mobile*ItemFactory` 的改动：在 CMP 课表页验证。课程卡片走 `MobileCourseLessonItemFactory` 的 BottomSheet；普通日程走 `MobileScheduleItemFactory`，无重叠时打开独立详情，重叠时显示课表 BottomSheet。旧原生页面不经过这些 factory。
