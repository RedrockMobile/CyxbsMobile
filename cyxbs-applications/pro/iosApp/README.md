# README

## 掌上重邮

#### 所属

[**红岩网校工作站 **](https://redrock.team)



#### 介绍

掌上重邮是应用于重邮校内的APP，为重邮学子带来了更具校园特色的互联网服务，提供包括课表查询、没课约、校历、空教室等功能。让广大邮子享受到线上快捷便利的校园生活。



#### 官网 [掌上重邮](https://m.app.redrock.team)



## 环境配置 & pod install 说明

### 模拟器开发（默认）

```bash
# 以 iosApp 作为 iOS 项目的根目录 
cd cyxbs-applications/pro/iosApp

# 安装依赖 (⚠️注意：如果真机调试/打发布包时使用 IS_SIMULATOR=0 pod install)
pod install

# 进入 xcworkspace 而不是进入 xcworkspace
open CyxbsMobile2019_iOS.xcworkspace
```

> 默认即为模拟器模式。AMap（高德地图）和 Bugly 等预编译 fat framework  
> 不含 arm64-simulator slice，脚本会自动 patch 为 arm64-sim 以支持 Apple Silicon 模拟器。

### 真机调试 / 打发布包

```bash
# 需要附带上 IS_SIMULATOR=0
IS_SIMULATOR=0 pod install
```

> 从 `.device-orig` 备份还原原始 arm64-device 二进制，之后可正常 Archive 或真机运行。  
> 打完包若需切回模拟器，再执行一次默认的 `pod install` 即可。

### 注意事项

- Podfile 无需手动修改，通过环境变量 `IS_SIMULATOR=0/1` 控制即可
- `Pods/` 目录不入 git，每次 clone 后需要执行 `pod install`
- 若更新了 AMap / Bugly pod 版本（`pod update`），脚本会自动重建备份，无需额外操作





<img src="https://is1-ssl.mzstatic.com/image/thumb/Purple116/v4/1a/05/33/1a05333c-5e87-6158-9da5-8ad42ae43565/AppIcon-0-0-1x_U007emarketing-0-0-0-7-0-0-sRGB-0-0-0-GLES2_U002c0-512MB-85-220-0-0.png/246x0w.webp" width = "300">



