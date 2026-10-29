# Android 命令行构建

日常开发使用 `cyxbs-applications:test` 调试应用壳，不使用需要发布密钥的 `pro` 壳。根目录只提供两个 Make 目标，Windows 和 macOS 共用同一份 Python 实现，不需要为不同电脑修改仓库文件。

## 日常命令

在仓库根目录执行：

```sh
make debug
make install_and_run
```

| 命令 | 行为 |
| --- | --- |
| `make debug` | 检查 JDK/SDK，增量编译 Debug，打印 APK 路径、大小、包名、版本和安装命令；不需要连接手机 |
| `make install_and_run` | 检查环境和设备，增量编译，覆盖安装到选定设备并启动；不卸载、不清除应用数据 |

只有一台已授权设备时自动选择。连接多台手机或同时开启模拟器时，必须指定设备，不默认选择第一台：

```sh
make install_and_run SERIAL=设备编号
```

可用 SDK 的 `platform-tools/adb devices -l` 查看编号，Windows 可执行文件是 `adb.exe`。手机需要开启 USB 调试，首次连接请解锁并确认授权。`unauthorized`、`offline`、无设备等情况会在构建前报错；编译后再次核对原设备，防止换机误装。

## 首次配置

| 工具 | 要求 |
| --- | --- |
| JDK | 推荐 JDK 21，`JAVA_HOME` 指向 JDK 根目录而不是 `bin`；仓库的 `gradle/gradle-daemon-jvm.properties` 要求 daemon 使用 21 |
| Android SDK | 当前需要 Platform API 37、与 AGP 匹配的 Build-Tools（本机已安装 37.0.0）；向设备安装还需要 Platform-Tools；后续以版本目录和 AGP 要求为准 |
| Python | 3.9 或更新版本，仅使用标准库，无需 `pip install` |
| GNU Make（可选） | 提供 `make debug` 等快捷入口；Windows 默认不带，安装步骤见下文；也可直接运行 Python 脚本 |
| 网络 | 首次构建需下载 Gradle、Maven 依赖和可能的 Java toolchain，需能访问仓库配置的依赖源 |

建议先通过 Android Studio 的 SDK Manager 安装 SDK 并接受相关许可。无需单独安装 Gradle，也无需为 Android 命令行编译安装 IDE 的 Kotlin Multiplatform 插件。

仓库默认 Gradle 堆上限为 8 GiB、Kotlin daemon 为 4 GiB。内存不足时按本机情况调整个人 Gradle 配置，不要为了自己的电脑提交全局内存或 SDK 路径变更。首次完整构建与已有缓存的增量构建耗时会有很大差异。

### Windows

**Windows 默认没有 GNU Make，安装 Git for Windows 或 Android Studio 也不代表已经安装 Make。** Make 只负责转发快捷命令，真正编译 Android 的仍是 Gradle。不想额外安装 Make，可以直接使用本节后面的 Python 命令，功能和进度显示相同。

推荐在 PowerShell 或 CMD 中使用原生 Windows JDK、SDK 和 Python。不要混用 WSL 的 Python 与 Windows 的 SDK/ADB；WSL 的 USB 设备接入不是本入口负责的范围。

先检查 Python 3.9+ 和 JDK 21 是否已准备好：

```powershell
py -3 --version
java -version
```

没有 Python 时，从 [Python Windows 下载页](https://www.python.org/downloads/windows/) 安装受支持的 Python 3，按安装器提示配置命令行入口，安装后重开终端。如果只有 `python` 命令，使用 `python --version` 检查。JDK 和 SDK 按上表准备；安装 Make 不会替你安装这些工具。

#### 安装 GNU Make

下面使用 Windows 的 WinGet，不要求事先安装 Scoop 或 Chocolatey。安装及个人 `Path` 配置由开发者在自己的 Windows 电脑上完成，项目脚本不会自动执行这些系统操作。

1. 在 PowerShell 或 CMD 执行 `winget --version`。如果找不到命令，在 Microsoft Store 安装或更新微软的“应用安装程序 / App Installer”，然后重开终端。无法使用 Store 的电脑可参考 [微软 WinGet 安装说明](https://learn.microsoft.com/windows/package-manager/winget/)，或直接使用 Python 入口。
2. 执行以下命令安装 GNU Make，按提示确认协议及可能出现的系统授权。先正常打开终端即可，不需要默认以管理员身份运行。

```powershell
winget install --exact --id GnuWin32.Make --source winget
```

3. 安装后确认 `make.exe` 所在目录。常见位置是 `C:\Program Files (x86)\GnuWin32\bin`；如果改过安装位置，以实际目录为准。这个安装包可能不会自动配置 `Path`。
4. 开始菜单搜索“编辑账户的环境变量”，在当前用户的变量中选择 `Path`，点击“编辑 → 新建”，加入上一步的 **bin 目录**。不要填 `make.exe` 文件路径，不加引号，也不要覆盖原有 `Path` 条目。
5. 保存并关闭对话框，重新打开终端。如果使用 Android Studio 或 VS Code 的内置终端，还需退出并重新打开 IDE，以继承新环境变量。
6. 在新终端执行以下命令，确认能找到程序，且版本输出包含 `GNU Make`。`where.exe` 在 PowerShell/CMD 均可使用。

```powershell
where.exe make
make --version
```

WinGet 的 `GnuWin32.Make` 是较老的 Windows 移植版，安装成功不等于整个项目已通过 Windows 实机验证。若下载、电脑策略或本机兼容性阻碍安装，可以直接使用 Python 入口；已有 GNU Make 且工作正常时不必重复安装。

已有 Scoop 或 Chocolatey 的同学也可以任选对应命令安装，无需再走 WinGet；安装后同样重开终端并验证：

```powershell
scoop install make
```

```powershell
choco install make
```

这两条仅适用于已安装对应包管理器的电脑，Chocolatey 按其现有安装方式可能需要管理员终端。Git for Windows 默认不包含 Make；Visual Studio 的 `nmake` 也不能替代这里的 GNU Make。

#### 运行项目命令

GNU Make 和 Python 都准备好后，在仓库根目录执行：

```powershell
make debug
make install_and_run
```

Make 在 Windows 默认调用 `python`。如果电脑提供的是 `py` 启动器，使用：

```powershell
make debug PYTHON="py -3"
make install_and_run PYTHON="py -3"
```

不想安装 Make 时，两个完全等价的入口如下，PowerShell/CMD 都可运行：

```powershell
py -3 scripts/android.py debug
py -3 scripts/android.py install_and_run
py -3 scripts/android.py install_and_run --serial 设备编号
```

若只有 `python` 命令，将上述 `py -3` 替换为 `python`。如果 `python` 打开 Microsoft Store，使用已安装的 `py -3` 或检查 Windows 的应用执行别名。

PowerShell 临时环境配置示例，JDK 路径替换为本机实际目录：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
make debug PYTHON="py -3"
```

CMD 等价设置方式：

```bat
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
make debug PYTHON="py -3"
```

脚本会调用 `gradlew.bat` 和 `adb.exe`，按独立参数传递 APK 等路径，兼容空格；Windows 构建完成后分别打印 PowerShell 与 CMD 可复制的安装命令。PowerShell 执行带引号的程序路径必须带 `&`，不要直接照搬 Bash 写法。

### macOS

准备 Python 3.9+、JDK 21、Android SDK。GNU Make 通常随 Xcode Command Line Tools 提供，可用 `make --version` 检查。

```sh
python3 --version
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
make debug
```

没有 Make 时可执行 `python3 scripts/android.py debug`、`python3 scripts/android.py install_and_run`。Linux 使用相同入口及本机 Linux JDK/SDK，SDK 默认搜索 `~/Android/Sdk` 和 `~/Android/sdk`；本次未在 Linux 实机验证。

### SDK 与 ADB 查找

SDK 优先级依次为 `local.properties` 的 `sdk.dir`、`ANDROID_HOME`、兼容旧配置的 `ANDROID_SDK_ROOT`、当前系统常见 SDK 目录。显式配置无效时直接报错，不偷偷换成另一套 SDK。自动识别后仅统一当前构建子进程的 SDK 环境变量，不修改全局环境或 `local.properties`。

Windows 手写 `local.properties` 建议使用正斜杠，不加引号：

```properties
sdk.dir=C:/Users/你的用户名/AppData/Local/Android/Sdk
```

也支持 Android Studio 自动写出的 `C\:\\Users\\...` 转义格式。`local.properties` 是个人配置，已被 Git 忽略，不应提交或互相复制。

ADB 优先采用环境变量 `ADB` 指定的可执行文件，其次使用选中 SDK 的 Platform-Tools，最后检查 `PATH`。例如 `make install_and_run ADB="D:/Android SDK/platform-tools/adb.exe"`。自定义 `ADB` 无效会报错；没有安装 ADB 不影响普通 `make debug`。

## 进度与产物

交互终端默认保留 Gradle 动态进度，重定向日志或 CI 使用普通文本。进度来自真实 Gradle 任务，不额外运行一次构建统计总数，也不预测剩余时间。

```sh
make debug CONSOLE=rich
make debug CONSOLE=plain
```

直接 Python 入口使用 `--console rich` 或 `--console plain`。脚本另有 `[1/3]` 环境完成、`[2/3]` 构建完成、`[3/3]` 产物确认等阶段提示；安装启动共 5 个阶段，阶段数不是 Gradle 工作量百分比。

内部始终调用仓库 Wrapper 的精确任务：

```sh
./gradlew :cyxbs-applications:test:assembleDebug --console=rich --stacktrace --no-configuration-cache
```

Windows 手动运行的等价命令是 `gradlew.bat :cyxbs-applications:test:assembleDebug --console=rich --stacktrace --no-configuration-cache`；PowerShell 前面加 `./`。不执行 `clean`，保留增量编译和构建缓存。暂时关闭 configuration cache 是因为应用壳附带的导航报告任务不兼容它，并不是关闭所有缓存。

当前 APK 的相对路径如下，但程序实际读取 AGP 的 `output-metadata.json`，不写死文件名、包名或版本：

```text
cyxbs-applications/test/build/outputs/apk/debug/test-debug.apk
```

成功后会输出 APK 的绝对路径、MiB 大小、真实 `applicationId`、`versionName/versionCode`，以及定位到本机 ADB 的 `install -r` 命令。当前调试包名为 `com.mredrock.cyxbs.test`。当前入口只支持单个通用 APK；以后启用 ABI/density splits 时需扩展产物选择逻辑，不会随便安装列表中的第一项。

`install_and_run` 使用 `adb -s 设备编号 install -r` 保留数据；从设备解析 LAUNCHER Activity 后用 `am start -W` 启动并检查结果，不写死 Activity 类名。仅确认 Android 报告启动成功，不代表应用功能或长期稳定性已验证。

## 常见问题

| 现象 | 处理 |
| --- | --- |
| `make` 不是内部或外部命令 / 无法识别 `make` | Windows 默认没有 Make；按上文安装，将含 `make.exe` 的目录加入用户 `Path`，重启终端及 IDE，再用 `where.exe make` 检查；也可直接使用 Python 入口 |
| `winget` 无法识别 | 安装或更新微软 App Installer 后重开终端；受管理电脑按微软说明或管理员要求处理，不必为本项目强行安装包管理器 |
| Make 能运行，但找不到 `python` | 用 `py -3 --version` 检查 Python，再使用 `make debug PYTHON="py -3"`；两个命令都不可用时先安装 Python |
| 找不到 Java/JDK 或版本不足 | 检查 `JAVA_HOME` 和 `java -version`；IDE 内配置的 JDK 不一定对命令行生效 |
| 找不到 API 37 或 Build-Tools | 在 SDK Manager 中安装；API 目录可能是 `android-37.0`，脚本兼容小版本号 |
| SDK 配置看起来正确但仍报错 | 优先检查 `local.properties`，其优先级高于环境变量；不要保留别人的绝对路径 |
| 依赖下载失败、toolchain 下载失败、许可缺失 | 先解决日志中的网络、代理或 SDK 许可问题；不应把个人代理或凭据写入仓库 |
| 没设备、`unauthorized`、`offline` | 检查数据线、USB 调试和手机授权；Windows 还可能需要设备厂商 USB 驱动 |
| 多设备 | 使用 `SERIAL=设备编号` 或 Python 的 `--serial` 显式选择 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 旧包签名不同；自行决定是否备份并卸载旧包，脚本不会自动卸载造成数据丢失 |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | 使用相同或更新版本；不默认加 `-d`，不自动降级 |
| 编译失败但输出目录存在旧 APK | 脚本立即非零退出，不读取或安装旧 APK；修复错误后重新执行 |
| 控制台花屏或日志里有控制符 | 使用 `CONSOLE=plain`；Windows 优先使用 Windows Terminal，重定向日志有编码问题时可使用 Python UTF-8 模式 |
| 禁用非 Android 平台后配置失败 | 部分脚本仍引用桌面等源集，不要为本入口默认关闭所有非 Android 平台；精确 Android 任务不会主动编译 iOS 应用 |

## 维护与验证

核心逻辑在 `scripts/android.py`，Makefile 仅转发两个目标。更改入口后可运行不依赖 SDK 或手机的标准库测试：

```sh
python3 -B -m unittest discover -s scripts/tests -v
```

Windows 使用 `py -3 -B -m unittest discover -s scripts/tests -v`。覆盖 Windows properties/空格路径、SDK 优先级、Windows Wrapper 分支、设备选择、APK 元数据、失败不装旧包、安装失败不启动等情况。它们不能替代 Windows 实机构建。

2026-10-29 在 macOS 上实际执行 `make debug CONSOLE=plain` 成功，Gradle 报告 `1468 actionable tasks: 67 executed, 1401 up-to-date`，产物为 `7.0.1-alpha (95)`、约 `47.72 MiB`。这是已有缓存下的增量验证，不是全新机器冷构建基准。13 项脚本测试通过；无设备时安装入口在编译前正确停止。本次没有连接 Android 设备，也没有 Windows 测试机，因此 Windows 原生完整构建及真实设备安装启动仍需团队实测。
