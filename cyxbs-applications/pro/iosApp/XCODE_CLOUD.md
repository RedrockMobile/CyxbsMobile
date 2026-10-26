# Xcode Cloud 发布

## 构建入口

- 仓库：`RedrockMobile/CyxbsMobile`
- Workspace：`cyxbs-applications/pro/iosApp/CyxbsMobile2019_iOS.xcworkspace`
- 共享 Scheme：`CyxbsMobile2019_iOS`
- App Bundle ID：`com.mredrock.cyxbs`
- Widget Bundle ID：`com.mredrock.cyxbs.CyxbsWidget3M975`
- 当前工程 Team ID：`3M9757C25S`（App 和 Widget 从 Project 继承）
- 两个 target 的 App Group：`group.com.mredrock.cyxbs.widget.3m9757c25s`

不要选择 `cyxbs-applications/multiplatform/iosApp`，它是示例壳；不要修改 Cloud 中其他 App 的 `iosApp` 产品。
`Configuration/Config.xcconfig` 中的 `TEAM_ID` 不是当前工程使用的 `DEVELOPMENT_TEAM`，不能据此切换发布团队。

## 仓库侧配置

`ci_scripts` 位于正式 workspace 同级，Xcode Cloud 会自动发现：

1. `ci_post_clone.sh` 安装 JDK 21，必要时安装 CocoaPods，以 `IS_SIMULATOR=0 pod install --deployment` 安装真机依赖，并检查 Gradle Wrapper。
2. Kotlin 的 Xcode Build Phase 在 Cloud 中重新 source `xcode_cloud_environment.sh`，使 `JAVA_HOME` 在实际 Gradle 进程生效。脚本之间的环境变量不会自动继承。
3. 保留现有的 `embedAndSignAppleFrameworkForXcode` 与 `syncComposeResourcesForIos`，不跳过 Kotlin 编译。
4. 提交 `Podfile.lock`，升级依赖时在本地执行 `IS_SIMULATOR=0 pod install` 并一起提交锁文件；云端不运行 `pod update`。安装失败最多重试 3 次，每次间隔 10 秒，以处理 GitHub clone 的临时 502 等下载失败；持续失败仍会终止构建。

CocoaPods 公共 Specs 使用 CDN，避免在每次全新 Cloud 环境克隆整个 Specs 仓库。保留阿里云 Specs 源以解析 HTTPDNS 依赖。

## 首次连接

先把上述文件提交并推送到远端，再在 Xcode 打开正式 workspace：

1. **Integrate → Create Workflow**，选择“掌上重邮”和拥有现有 App 的开发团队。
2. 连接 `RedrockMobile/CyxbsMobile`。Xcode 使用的 Apple 账号与浏览器 App Store Connect 账号必须一致，否则连接页可能提示 `Request is for another user`。
3. 如 GitHub 提示新增仓库访问权限，由仓库管理员核对并授权 Apple 的 Xcode Cloud 集成访问此仓库。
4. 使用现有 App Store Connect 记录 `974026615`，不要创建同名的新 App。

Xcode Cloud 的工作流保存在 Apple 服务端；仓库里的 shell 脚本不会自动创建、启用工作流，也不会自动上传构建。

## 推荐工作流

| 设置 | TestFlight | App Store |
| --- | --- | --- |
| 名称 | CyxbsMobile TestFlight | CyxbsMobile App Store |
| 启动条件 | `develop` 分支代码变化，或手动启动 | `ios-v*` 标签，或手动选择发布提交 |
| 构建入口 | 正式 workspace / `CyxbsMobile2019_iOS` | 同左 |
| Action | Archive / iOS / Release | Archive / iOS / Release |
| Archive Distribution | TestFlight and App Store | TestFlight and App Store |
| 签名 | 自动签名，使用现有 App 所属团队 | 同左 |
| Post-action | TestFlight Internal Testing，选择实际测试组 | 可选内部测试组；构建上传后进入 App Store Connect 提审 |

初次配置时先使用手动启动验证，再启用分支/标签自动触发。Cloud 上选择与项目依赖兼容的正式版 Xcode/macOS，首次成功后固定版本。不要选择 **TestFlight Internal Only**，否则该构建不能提交 App Store 审核。

Xcode Cloud 管理递增构建号。在产品 Settings 设置 **Next Build Number** 大于 App Store Connect 已使用的构建号，App 与 Widget 保持同一版本号。`MARKETING_VERSION` 由源码维护，新版本同时更新两个 target。

不要将 `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED` 设为 `YES`，否则会跳过 Kotlin 编译。此项目不需要在仓库中保存 `.p12`、描述文件或 App Store Connect API 私钥；使用 Cloud 自动签名。

## 发布到 TestFlight 与 App Store

1. Cloud Archive 成功，并完成 App Store Connect 处理后，在 TestFlight 核对版本、构建号和测试组。外部测试组可能需要 Beta App Review。
2. 正式上架时在 App Store Connect 创建新的 iOS 版本，选择同一个已测试的构建。
3. 填写此次更新说明，核对截图、隐私信息和审核账号，提交 App Review；按版本设置在审核通过后发布。Cloud 上传成功不等于 App Store 已发布。
4. 下载保留该次构建的 archive 和 dSYM，供崩溃符号化和追踪版本使用。

已有 App Store 版本 `7.0.0 (3)` 于本次配置时显示 Ready for Distribution；后续上架需要尚未发布的新版本号。

## 本地验证

### Cloud 提示 workspace does not exist

先核对该次构建 Overview 的分支和提交。正式工程位于 `develop`，旧 `master`
分支不含上述 workspace；即使工作流的 Branch Changes 已设置为 `develop`，
Manual Start 的 Any Branches 仍允许手动误选 `master`。
重新开始构建时明确选择含正式工程和 `ci_scripts` 的分支，不要对旧 `master`
构建点击 Rebuild（它会继续使用错误的源码）。首次配置脚本也必须先提交并推送。

当前 Default 工作流的手动启动已限定为 `develop` 与 `codex/xcode-cloud-ios`，
避免再次选择旧 `master`。修复合入后可移除临时修复分支入口。

### 验证编译与归档

```sh
cd cyxbs-applications/pro/iosApp
IS_SIMULATOR=0 pod install --deployment
cyxbs_derived_dir=$(mktemp -d /tmp/cyxbs-cloud-check.XXXXXX)
xcodebuild \
  -workspace CyxbsMobile2019_iOS.xcworkspace \
  -scheme CyxbsMobile2019_iOS \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -derivedDataPath "$cyxbs_derived_dir" \
  -archivePath "$cyxbs_derived_dir/CyxbsMobile.xcarchive" \
  CODE_SIGNING_ALLOWED=NO archive
```

本地无签名 Archive 只能验证编译与归档。正式分发仍需验证 Cloud 端签名、App Group profile、上传处理和测试组分发结果。

## 官方文档

- [自定义构建脚本](https://developer.apple.com/documentation/xcode/writing-custom-build-scripts)
- [准备第三方依赖](https://developer.apple.com/documentation/xcode/making-dependencies-available-to-xcode-cloud)
- [配置第一个工作流](https://developer.apple.com/documentation/xcode/configuring-your-first-xcode-cloud-workflow)
- [分发 Cloud 构建至 TestFlight](https://developer.apple.com/documentation/xcode/distributing-your-xcode-cloud-builds-through-testflight)
- [设置下一次构建号](https://developer.apple.com/documentation/xcode/setting-the-next-build-number-for-xcode-cloud-builds)
