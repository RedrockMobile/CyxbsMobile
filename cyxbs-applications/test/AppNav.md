# AppNav Deeplink 汇总

> 打包时由 class nav.AppNavReportTask 自动生成

- versionCode: 94
- versionName: 6.10.6-alpha
- date: 2026-05-24 02:22

## 调试方法

使用 idea 文档中的 ▶ 运行下面脚本，输入要测试的 deeplink

### Windows / macOS / Linux

> win 没 bash 可使用终端运行 PowerShell 版

idea 中点击左侧 ▶ 可直接运行
```sh
#!/usr/bin/env bash
while true; do
  printf "输入 deeplink (回车退出): "
  read -r link
  [ -z "$link" ] && break
  adb shell am start -a android.intent.action.VIEW -d "$link"
done
```

### Windows (PowerShell 版)

```powershell
while ($true) {
  $link = Read-Host "输入 deeplink (回车退出)"
  if ([string]::IsNullOrEmpty($link)) { break }
  adb shell am start -a android.intent.action.VIEW -d "$link"
}
```

未找到任何由 KSP 生成的 AppNavReport 文档，请检查 ksp-navigation 实现
