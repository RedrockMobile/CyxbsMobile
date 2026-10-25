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

用户明确真机或模拟器时直接继续。未说明时，先问一句：`要部署到真机还是 iOS 模拟器？`

- 真机：用 `xcrun devicectl list devices` 查找已连接或已配对设备；沿用当前会话已选设备，尚未选择且有多个候选时让用户指定。
- 模拟器：用 `xcrun simctl list devices available` 查找；选择可用 iPhone，并在需要时 boot。

不要因为设备未连接而改为其他目标，也不要擅自删除现有 App。

## 构建前检查

1. 在仓库根目录记录 `git rev-parse HEAD` 和 `git status --short`。构建当前 checkout；除非用户明确要求同步或合入，不执行 `pull`、`merge`、`rebase` 或切分支。
2. 在 `cyxbs-applications/pro/iosApp` 下确认存在 `Podfile` 与 `CyxbsMobile2019_iOS.xcworkspace`。
3. 确认 CocoaPods 可用。缺失时安装 CocoaPods；首次使用或 Pods 缺失时执行 `pod install`。
4. 依赖二进制架构必须与目标一致：
   - 真机：`IS_SIMULATOR=0 pod install`
   - 模拟器：`pod install`（默认 `IS_SIMULATOR=1`）

每次在真机和模拟器间切换都重新执行对应的 `pod install`。高德地图、Bugly 等预编译库的 arm64 device/simulator slice 互斥。

## 签名与 App Groups

真机前读取主应用和 Widget 的 `Signing & Capabilities` / `.entitlements`：

- 两个 target 必须属于同一开发团队，并启用自动签名。
- Widget 的 Bundle ID 必须以主应用 Bundle ID 为前缀；若当前团队无法注册仓库原有 Widget ID，使用当前团队下唯一的后缀。
- 主应用与 Widget 必须使用同一个 App Group，且 Swift 共享容器常量和两个 entitlements 完全一致。
- 若 Apple 拒绝仓库原有 group，不能反复重试该标识。创建当前团队唯一的新 `group.*` 标识，并仅替换共享容器的全部实际引用。

真机构建可使用 `xcodebuild -allowProvisioningUpdates` 让 Xcode 创建或更新 profile。若 profile 仍不支持 group，说明 App Group 未属于该团队或未关联 App ID，应报告具体标识和阻塞原因。

## 构建、安装与启动

从 `cyxbs-applications/pro/iosApp` 运行，scheme 固定为 `CyxbsMobile2019_iOS`。每轮验证用 `mktemp -d` 创建独立的临时 `-derivedDataPath`，该轮修复失败后的重试可复用此目录。独立目录不能代替 Kotlin 构建阶段：仍需确认当前源码的编译、framework 组装与 Compose 资源同步实际执行。

真机构建示例：

```bash
xcodebuild -allowProvisioningUpdates \
  -workspace CyxbsMobile2019_iOS.xcworkspace \
  -scheme CyxbsMobile2019_iOS \
  -configuration Debug \
  -destination 'id=<UDID>' \
  -derivedDataPath /tmp/cyxbs-ios-hybrid-device \
  build
```

构建成功后：

1. 确认 `.app` 存在，并用 `codesign -dvv` 核对 Bundle ID 与 TeamIdentifier。
2. 若配置了 App Group，解析主 App 与 `PlugIns/CyxbsWidgetExtension.appex` 的 `embedded.mobileprovision`，确认二者都包含同一 group。
3. 真机通过 `xcrun devicectl device install app` 安装，再用 `xcrun devicectl device process launch --terminate-existing <bundle-id>` 启动。
4. 模拟器通过 `xcrun simctl install booted <app>`，再用 `xcrun simctl launch booted <bundle-id>` 启动。

若安装提示同 Bundle ID 的已安装 App 来自另一团队签名，iOS 不允许覆盖升级。说明必须卸载旧 App，明确提示会清除该 App 本地数据；取得用户明确确认后才执行卸载。

## 验证与报告

报告必须包含：构建提交 SHA、实际部署目标、Bundle ID、构建/安装/启动结果，以及是否验证 KMP framework 与 App Group profile。

若含未提交修改，注明“提交 SHA + 工作区修改”。安装及启动成功只证明部署成功；只有实际观察到对应页面与交互结果，才能报告业务验收通过。可用 `devicectl device info processes` 核对安装路径和存活进程；支持时用 `devicectl device capture screenshot --device <UDID> --destination <path.png>` 观察页面。截图若显示其他 App，不能作为本 App 页面验证的证据。

当用户验证某个 Kotlin 改动时，先执行 `git merge-base --is-ancestor <commit> HEAD`。若返回假，直接说明当前安装包不含该提交，先更新构建源再安装。不要仅凭 App 的版本号判断源码是否最新。

针对课表 `Mobile*ItemFactory` 的改动：在 CMP 课表页验证。课程卡片走 `MobileCourseLessonItemFactory` 的 BottomSheet；普通日程走 `MobileScheduleItemFactory`，无重叠时打开独立详情，重叠时显示课表 BottomSheet。旧原生页面不经过这些 factory。
