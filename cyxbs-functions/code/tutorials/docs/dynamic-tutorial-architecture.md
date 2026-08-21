# 动态教程架构

## 模块边界

```text
cyxbs-functions:code:tutorials
├── js-bridge  教程 npm 包与端上的稳定序列化协议
├── catalog    仅包含 catalog.json 的静态 npm 包
└── java       Java 课程正文、初始源码与步骤校验

cyxbs-components:guided-tour
└── 通用布局锚点注册与镂空引导层，不依赖教程业务
```

每门语言只发布一个教程 npm 包。课程与课时是包内数据，不再拆分 npm 坐标；这样能够共享同一门
语言的模板、校验工具和稳定 ID，也避免用户进入课程路径时下载大量小依赖。

## 加载顺序

1. `DynamicTutorialManager.supportedTutorials()` 读取
   `@cyxbs-mobile/tutorial-catalog@latest/catalog.json`。
2. 用户展开某门语言后，Manager 按 Catalog 坐标加载对应 `DynamicTutorialService`。
3. `manifest()` 只返回侧边栏课程卡片；用户进入卡片后再调用 `course(courseId)` 获取正文与源码。
4. 客户端保存 course、lesson、step 的稳定 ID 和进度，不修改 npm 包内容。
5. 编辑或运行后调用 `evaluate()`，语言包根据源码与输出判断当前步骤是否完成。

新增语言时，在语言教程模块调用 `npmJsTutorial`，再把该 Project 加入 catalog 的
`generateDynamicTutorialCatalog` 列表即可。npm 包名只在语言模块的 `npmJsPackage` 声明一次。

## UI 接入约定

- 课程入口使用 `manifest.courses` 构造带前置关系的卡片式路径。
- 进入课时后，底部工具窗口顺序为 `Tutorial | Run | Performance`，Tutorial 默认展开。
- 教程正文只包含声明式文本、提示和代码块，npm 包不能返回 Compose 组件。
- 布局提示使用稳定 `anchorId`；业务控件通过 `Modifier.guidedTourTarget` 注册位置。
- 编辑器源码提示使用文件路径与 UTF-16 区间，由编辑器把区间换算为 `Rect` 后写入
  `GuidedTourTargetRegistry`。
- 引导数据禁止保存屏幕坐标，因此窗口旋转、宽屏切换和桌面缩放后可重新测量。

## 当前首包

`@cyxbs-mobile/tutorial-java` 当前提供三张可验证完整链路的课程卡片：Java 起步、流程控制、泛型与
集合。内容属于脚手架语料，后续可以在不改变端上 UI 协议的情况下继续扩充课程与步骤。
