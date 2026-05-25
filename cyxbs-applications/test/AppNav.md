# AppNav Deeplink 汇总

> 打包时由 build-logic/manager/nav.AppNavReportTask 自动生成
> 该文件需要被 git 提交用于后续使用

- versionCode: 94
- versionName: 6.10.6-alpha
- date: 2026-05-25 23:10

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

## :cyxbs-functions:update

### dialog/update

- entry: `com.cyxbs.functions.update.dialog.UpdateInfoDialogNavEntry`
- argument: `com.cyxbs.functions.update.dialog.UpdateInfoNavArgument`

```text
deeplink: cyxbs://dialog/update?versionName={String}&updateContent={String}&downloadUrl={String}
```

## :cyxbs-pages:course

### course

- entry: `com.cyxbs.pages.course.CourseNavEntry`
- argument: `com.cyxbs.pages.course.api.CourseNavArgument`

```text
deeplink: cyxbs://course?stuNum={String}
```

## :cyxbs-pages:emptyroom

### emptyroom

- entry: `com.cyxbs.pages.emptyroom.ui.EmptyRoomNavEntry`
- argument: `com.cyxbs.pages.emptyroom.api.EmptyRoomNavArgument`

```text
deeplink: cyxbs://emptyroom
```

## :cyxbs-pages:food

### food

- entry: `com.cyxbs.pages.food.ui.FoodNavEntry`
- argument: `com.cyxbs.pages.food.api.FoodNavArgument`

```text
deeplink: cyxbs://food
```

## :cyxbs-pages:home

### home

- entry: `com.cyxbs.pages.home.ui.HomeNavDestination`
- argument: `com.cyxbs.pages.home.api.HomeNavArgument`

```text
deeplink: cyxbs://home?page={String optional}
```

## :cyxbs-pages:login

### login

- entry: `com.cyxbs.pages.login.ui.LoginNavEntry`
- argument: `com.cyxbs.pages.login.api.LoginNavArgument`

```text
deeplink: cyxbs://login?targetUrl={String optional}
```

## :cyxbs-pages:map

### map

- entry: `com.cyxbs.pages.map.ui.MapNavEntry`
- argument: `com.cyxbs.pages.map.api.MapNavArgument`

```text
deeplink: cyxbs://map?placeSearch={String optional}
```

### map_show_picture

- entry: `com.cyxbs.pages.map.ui.MapShowPictureNavEntry`
- argument: `com.cyxbs.pages.map.ui.MapShowPictureNavArgument`

```text
deeplink: cyxbs://map_show_picture?imageList={String[]}&currentIndex={Int}
```

## :cyxbs-pages:mine

### about

- entry: `com.cyxbs.pages.mine.about.ui.AboutNavEntry`
- argument: `com.cyxbs.pages.mine.about.ui.AboutNavArgument`

```text
deeplink: cyxbs://about
```

## :cyxbs-pages:notification

### dialog/notice

- entry: `com.cyxbs.pages.notification.dialog.NoticeDialogNavEntry`
- argument: `com.cyxbs.pages.notification.api.NoticeNavArgument`

```text
deeplink: cyxbs://dialog/notice?title={String}&content={String}&map={Map json optional}&button={ButtonInfo json optional}
object fields:
  map: Map
  button: ButtonInfo {
    text: String
    action: String optional
  }
```

## :cyxbs-pages:schoolcar

### school_car

- entry: `com.cyxbs.pages.schoolcar.ui.SchoolCarNavDestination`
- argument: `com.cyxbs.pages.schoolcar.api.SchoolCarNavArgument`

```text
deeplink: cyxbs://school_car
```

