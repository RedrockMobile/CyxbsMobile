# AppNav Deeplink 汇总

> 打包时由 build-logic/manager/nav.AppNavReportTask 自动生成
> 该文件需要被 git 提交用于后续使用

- versionCode: 94
- versionName: 6.10.6-alpha
- date: 2026-05-31 14:31

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

## 模板说明

URL 模板用 `{}` / `[]` 区分 required / optional，object fields 段递归展开复杂结构。

### URL 模板

- `name={Type}` — required 字段，调用方必须提供
- `name=[Type]` — optional 字段（构造参数有默认值），调用方可省略
- 类型末尾的 `?` 表示 Kotlin nullable，值允许为 null（与 optional 不同：nullable 仍要求字段出现）
- 集合 / Map 直接以原始 Kotlin 类型出现，例如 `{List<TextInfo>}`、`{Map<String, TextInfo>}`，编码方式遵循 kotlinx.serialization JSON 形式

### object fields

仅当字段含可展开的内部结构时才会出现。展开规则：

- `name: Type` — required 字段，`[name]: Type` — optional 字段
- 普通 `@Serializable` 类：列出每个非 `@Transient` 的主构造参数
- `enum`：列出所有 entry 名
- `Map<K, V>`：展开为 `value: V { ... }`（仅 V 是复杂类型时才进一步展开）
- `Collection<E>` / `Array<E>`：展开为 `value: E { ... }`
- 带类型形参的类（如 `Wrapper<T>`）：按外层泛型实参替换 `T` 后再展开

### 示例

```text
deeplink: cyxbs://test/abc?title={String}&content={String}&map=[Map<String, TextInfo>]&button=[ButtonInfo?]
object fields:
  [map]: Map<String, TextInfo> {
    value: TextInfo {
      text: String
      [isBold]: Boolean
    }
  }
  [button]: ButtonInfo? {
    text: String
    [action]: String?
  }
```

解读：`title` / `content` 必填；`map` 可省略，若提供则为 `Map<String, TextInfo>` 的 JSON；`button` 可省略且允许为 null。`TextInfo` 中只有 `text` 必填，其它字段可省略。

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
deeplink: cyxbs://course?stuNum={String}&stableKey=[String?]
```

### course_find

- entry: `com.cyxbs.pages.course.find.FindCourseNavEntry`
- argument: `com.cyxbs.pages.course.api.FindCourseNavArgument`

```text
deeplink: cyxbs://course_find?initialQuery=[String]&directStuNum=[String]
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
deeplink: cyxbs://home?page=[String]
```

## :cyxbs-pages:login

### login

- entry: `com.cyxbs.pages.login.ui.LoginNavEntry`
- argument: `com.cyxbs.pages.login.api.LoginNavArgument`

```text
deeplink: cyxbs://login?targetUrl=[String?]
```

## :cyxbs-pages:map

### map

- entry: `com.cyxbs.pages.map.ui.MapNavEntry`
- argument: `com.cyxbs.pages.map.api.MapNavArgument`

```text
deeplink: cyxbs://map?placeSearch=[String?]
```

### map_show_picture

- entry: `com.cyxbs.pages.map.ui.MapShowPictureNavEntry`
- argument: `com.cyxbs.pages.map.ui.MapShowPictureNavArgument`

```text
deeplink: cyxbs://map_show_picture?imageList={List<String>}&currentIndex={Int}
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
deeplink: cyxbs://dialog/notice?title={String}&content={String}&map=[Map<String, TextInfo>]&button=[ButtonInfo?]
object fields:
  [map]: Map<String, TextInfo> {
    value: TextInfo {
      text: String
      [isBold]: Boolean
      [textSize]: Int
      [textColorStr]: String?
      [action]: String?
    }
  }
  [button]: ButtonInfo? {
    text: String
    [action]: String?
  }
```

## :cyxbs-pages:schoolcar

### school_car

- entry: `com.cyxbs.pages.schoolcar.ui.SchoolCarNavDestination`
- argument: `com.cyxbs.pages.schoolcar.api.SchoolCarNavArgument`

```text
deeplink: cyxbs://school_car
```

## :cyxbs-pages:ufield

### fairground

- entry: `com.cyxbs.pages.ufield.fairground.FairgroundNavEntry`
- argument: `com.cyxbs.pages.ufield.api.FairgroundNavArgument`

```text
deeplink: cyxbs://fairground
```
