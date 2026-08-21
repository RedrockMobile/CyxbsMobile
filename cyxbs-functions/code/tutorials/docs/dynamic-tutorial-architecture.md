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
3. `manifest()` 只返回侧边栏课程卡片和课时/步骤稳定 ID，不传输正文与源码；用户进入卡片后再调用
   `course(courseId)` 获取完整内容。
4. 客户端保存 course、lesson、step 的稳定 ID 和进度，不修改 npm 包内容。
5. 编辑或运行后调用 `evaluate()`，语言包根据源码与输出判断当前步骤是否完成。

`DynamicTutorialSession` 是 npm 包与编辑器之间的失败关闭边界：Manifest 和课程首次读取后会校验并在
会话内缓存，语言身份、摘要、稳定 ID、活动文件、引导区间、完成条件、相对路径和文本/源码总量均需
满足端上限制。重复 ID、缺少正文或超大反馈会抛出 `DynamicTutorialProtocolException`，不会把畸形
对象交给 Compose；同一 JavaScript Runtime 的调用也会串行执行，关闭后禁止继续读取旧缓存。Catalog、
npm 包或协议加载失败时，课程侧栏提供显式重试；重试通过新的 Compose effect 代次先取消并释放旧
Runtime，再创建新会话，不要求用户切换语言。

## 进度与代码现场

- `DynamicTutorialManager` 以 `languageId + courseId` 为键原子保存每门课程的最新进度，不在本地
  复制课程正文，也不修改 npm 包。
- 已完成项使用 `lessonId + stepId`，恢复位置使用 course、lesson、step 的稳定 ID。教程包新增、删除或
  重排步骤后，客户端会过滤失效 ID，并定位到当前版本第一个未完成步骤。
- 相同 npm 包版本会恢复小型多文件工作区和活动文件；包版本变化时只恢复学习进度，源码回到新包的
  初始模板，避免旧代码覆盖更新后的教学内容。
- 进度 JSON 使用临时文件加原子移动写入，并限制课程数、步骤数、文件数和源码总字符数。缓存损坏、
  schema 不兼容或单条记录越界时按空记录处理，不影响教程 npm 包重新下载与使用。
- 编辑器在输入停止后保存，在课程切换或页面离开时冲刷最后一份快照；课程路径依据 Manifest 的轻量
  课时目录显示“已完成/总课时”和下一课时，不需要提前下载每门课程正文。
- 进度列表按成功写入顺序保留最近课程；教程加载后自动恢复最近一门未完成课程，全部完成时则恢复
  最近课程供复习，无需额外续学卡片、时间戳或另一份偏好状态。
- 一门课程的多个课时分别保存工作区；Tutorial 工具窗口可直接切换课时并标记完成状态，重置入口只
  删除当前课程记录，不影响同语言的其他课程。

新增语言时，在语言教程模块调用 `npmJsTutorial`，再把该 Project 加入 catalog 的
`generateDynamicTutorialCatalog` 列表即可。npm 包名只在语言模块的 `npmJsPackage` 声明一次。

## UI 接入约定

- 课程入口使用 `manifest.courses` 构造带前置关系的卡片式路径，并统一解析未开始、进行中、已完成与
  锁定状态。未完成全部 `prerequisiteCourseIds` 时，侧栏展示缺少的前置课程且加载入口再次拒绝；未知
  前置 ID 或循环依赖按锁定处理，已完成课程不会因 npm 包后来新增前置关系而重新锁住。
- Manifest 的课时摘要必须与完整课程中的课时标题和步骤 ID 一致；客户端在课程加载时交叉校验，避免
  包版本错配导致课程路径进度与正文不一致。旧包缺少摘要时仍保留状态文字作为兼容降级。
- 进入课时后，底部工具窗口顺序为 `Tutorial | Run | Performance`，Tutorial 默认展开。
- 当前课时完成后 Tutorial 窗口提供“下一课时”；整门课程完成后提供“课程路径”，并在进度异步落盘前
  先用内存完成状态解锁下一门课程，避免连续学习时出现短暂的错误锁定。
- 教程正文只包含声明式文本、提示和代码块，npm 包不能返回 Compose 组件。
- 布局提示使用稳定 `anchorId`；业务控件通过 `Modifier.guidedTourTarget` 注册位置。
- 编辑器源码提示使用文件路径与 UTF-16 区间，由编辑器把区间换算为 `Rect` 后写入
  `GuidedTourTargetRegistry`。
- 引导数据禁止保存屏幕坐标，因此窗口旋转、宽屏切换和桌面缩放后可重新测量。

## 当前首包

`@cyxbs-mobile/tutorial-java` 当前提供四张可验证完整链路的课程卡片：Java 起步、流程控制、类与
对象、泛型与集合。面向对象课程通过独立 Java 文件演示字段、构造器、继承、重写和父类引用的动态
分派；后续仍可在不改变端上 UI 协议的情况下继续扩充课程与步骤。
