#!/usr/bin/env bash
# 构建 cyxbs-applications/test 的 Debug APK，并安装、启动到选中的 Android 设备。
# 脚本兼容 macOS、Linux 以及 Git for Windows 提供的 Bash。
set -euo pipefail

# 多台设备同时连接时，脚本会按顺序选择第一台在线的已登记设备。
# 团队成员可以把 `adb devices` 显示的设备 ID 追加到这个数组中，例如：
#   "your-device-id" # 姓名或用途
# 临时使用未登记设备时，也可以执行：
#   PREFERRED_DEVICE=your-device-id bash .codex/scripts/install-test-debug.sh
PREFERRED_DEVICE_IDS=(
  "3ad7ce04" # guoxiangrui 的常用 Android 设备
  # "your-device-id" # 在这里添加其他成员的设备 ID
)

# 环境变量指定的设备优先级最高，便于个人临时使用且不必修改已提交的脚本。
if [[ -n "${PREFERRED_DEVICE:-}" ]]; then
  PREFERRED_DEVICE_IDS=("$PREFERRED_DEVICE" "${PREFERRED_DEVICE_IDS[@]}")
fi

# 以下变量描述目标应用与构建产物，模块或包名发生变化时需要同步调整。
PACKAGE_NAME="com.mredrock.cyxbs.test"
# 必须显式指定业务主界面；使用 monkey 可能误选 LeakCanary 注册的 Leaks Launcher Activity。
MAIN_ACTIVITY="com.cyxbs.pages.home.ui.main.MainActivity"
APK_DIR="cyxbs-applications/test/build/outputs/apk/debug"
GRADLE_TASK=":cyxbs-applications:test:assembleDebug"

# Action 必须在 CyxbsMobile_2 项目根目录或其 worktree 根目录运行，避免误构建其他工程。
if [[ ! -d "cyxbs-applications/test" ]] || \
   [[ ! -f "gradlew" && ! -f "gradlew.bat" ]]; then
  echo "Wrong workspace: $(pwd)"
  echo "Run this action from the CyxbsMobile_2 project or its worktree."
  exit 1
fi

# 根据 Bash 所在平台选择 Gradle Wrapper 和 Android SDK 的调用方式。
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    PLATFORM="windows"
    ;;
  Darwin*)
    PLATFORM="macos"
    ;;
  *)
    PLATFORM="linux"
    ;;
esac

# 将 Windows 路径转换为 Git Bash 可直接访问的路径；其他平台保持原样。
to_bash_path() {
  local input_path="$1"
  if [[ "$PLATFORM" == "windows" ]] && command -v cygpath >/dev/null 2>&1; then
    cygpath -u "$input_path"
  else
    printf '%s\n' "$input_path"
  fi
}

# 优先使用 PATH 中的 adb，其次查找 Android SDK 环境变量和各平台默认安装目录。
find_adb() {
  local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  local candidate=""

  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi

  if [[ -n "$sdk_root" ]]; then
    sdk_root="$(to_bash_path "$sdk_root")"
    if [[ "$PLATFORM" == "windows" ]]; then
      candidate="$sdk_root/platform-tools/adb.exe"
    else
      candidate="$sdk_root/platform-tools/adb"
    fi
  elif [[ "$PLATFORM" == "windows" && -n "${LOCALAPPDATA:-}" ]]; then
    candidate="$(to_bash_path "$LOCALAPPDATA")/Android/Sdk/platform-tools/adb.exe"
  elif [[ "$PLATFORM" == "macos" && -n "${HOME:-}" ]]; then
    candidate="$HOME/Library/Android/sdk/platform-tools/adb"
  elif [[ "$PLATFORM" == "linux" && -n "${HOME:-}" ]]; then
    candidate="$HOME/Android/Sdk/platform-tools/adb"
  fi

  if [[ -n "$candidate" && -x "$candidate" ]]; then
    printf '%s\n' "$candidate"
    return 0
  fi

  return 1
}

# Windows 通过 gradlew.bat 构建，macOS/Linux 通过可执行的 gradlew 构建。
run_gradle() {
  if [[ "$PLATFORM" == "windows" ]]; then
    if [[ ! -f "gradlew.bat" ]]; then
      echo "gradlew.bat was not found."
      return 1
    fi
    cmd.exe /d /c gradlew.bat "$@"
  else
    if [[ ! -x "gradlew" ]]; then
      echo "gradlew is not executable. Run: chmod +x gradlew"
      return 1
    fi
    ./gradlew "$@"
  fi
}

# 在开始构建前确认 adb 可用，让 SDK 配置问题尽早暴露。
if ! ADB="$(find_adb)"; then
  echo "adb was not found."
  echo "Add Android SDK platform-tools to PATH or set ANDROID_SDK_ROOT."
  exit 1
fi

# 同时保留设备 ID 和状态；unauthorized/offline 设备也需要参与错误提示。
DEVICE_LIST="$("$ADB" devices | awk 'NR > 1 && NF >= 2 { print $1, $2 }')"
DEVICE_COUNT="$(printf '%s\n' "$DEVICE_LIST" | awk 'NF { count++ } END { print count + 0 }')"

# 单设备场景直接使用唯一设备；多设备场景只选择登记过且状态为 device 的设备。
case "$DEVICE_COUNT" in
  0)
    echo "No Android device was detected."
    exit 1
    ;;
  1)
    DEVICE_SERIAL="$(printf '%s\n' "$DEVICE_LIST" | awk 'NF { print $1; exit }')"
    DEVICE_STATE="$(printf '%s\n' "$DEVICE_LIST" | awk 'NF { print $2; exit }')"
    if [[ "$DEVICE_STATE" != "device" ]]; then
      echo "Device $DEVICE_SERIAL is in state: $DEVICE_STATE"
      echo "Unlock the device and accept the USB debugging prompt."
      exit 1
    fi
    ;;
  *)
    DEVICE_SERIAL=""

    # 按配置顺序匹配，防止 adb 在多设备环境下把 APK 安装到错误的手机或模拟器。
    for preferred_device in "${PREFERRED_DEVICE_IDS[@]}"; do
      preferred_state="$(
        printf '%s\n' "$DEVICE_LIST" |
          awk -v serial="$preferred_device" '$1 == serial { print $2; exit }'
      )"

      if [[ "$preferred_state" == "device" ]]; then
        DEVICE_SERIAL="$preferred_device"
        break
      fi

      if [[ -n "$preferred_state" ]]; then
        echo "Registered device $preferred_device is in state: $preferred_state"
      fi
    done

    if [[ -z "$DEVICE_SERIAL" ]]; then
      echo "Multiple devices are connected, but no online registered device was found."
      echo "Add your device ID to PREFERRED_DEVICE_IDS or set PREFERRED_DEVICE temporarily."
      echo "Connected devices:"
      printf '%s\n' "$DEVICE_LIST"
      exit 1
    fi
    ;;
esac

# 先生成 APK，再由 adb 精确安装到选中的设备，避免 Gradle installDebug 操作全部设备。
echo "Selected device: $DEVICE_SERIAL"
echo "Building $GRADLE_TASK"
run_gradle "$GRADLE_TASK"

# 构建完成后确认输出目录存在，避免继续使用旧路径或错误模块的产物。
if [[ ! -d "$APK_DIR" ]]; then
  echo "APK output directory was not found: $APK_DIR"
  exit 1
fi

APK_LIST="$(find "$APK_DIR" -maxdepth 1 -type f -name '*.apk' -print | sort)"
APK_COUNT="$(printf '%s\n' "$APK_LIST" | awk 'NF { count++ } END { print count + 0 }')"

# 当前安装流程只支持单 APK；发现拆分 APK 时主动停止，避免随机安装错误文件。
if [[ "$APK_COUNT" -eq 0 ]]; then
  echo "No debug APK was found in: $APK_DIR"
  exit 1
fi

if [[ "$APK_COUNT" -ne 1 ]]; then
  echo "Expected one APK, but found $APK_COUNT. Refusing to install an arbitrary split APK."
  printf '%s\n' "$APK_LIST"
  exit 1
fi

APK_PATH="$(printf '%s\n' "$APK_LIST" | awk 'NF { print; exit }')"

# -r 保留应用数据并覆盖安装，-t 允许安装被标记为 testOnly 的 Debug APK。
echo "Installing $APK_PATH"
"$ADB" -s "$DEVICE_SERIAL" install -r -t "$APK_PATH"

# 显式启动业务 MainActivity，避免 monkey 在多个 Launcher Activity 中打开 LeakCanary 的 Leaks 页面。
echo "Launching $PACKAGE_NAME/$MAIN_ACTIVITY"
"$ADB" -s "$DEVICE_SERIAL" shell am start \
  -W \
  -n "$PACKAGE_NAME/$MAIN_ACTIVITY"

echo "Done."
