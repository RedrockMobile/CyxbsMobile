"""在模拟器上安装 CI 专用 release 包，并检查更新弹窗是否真正显示。"""

import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from urllib.parse import urlencode


PACKAGE = "com.mredrock.cyxbs"
DUMP_PATH = "/sdcard/cyxbs-release-dialog.xml"
EXPECTED_TEXT = ("有新版本更新", "CI 更新弹窗测试", "立即更新")


def select_emulator_api(sdk_list: str) -> int:
    """从稳定通道的 SDK 列表选择最新正式 API；缺少对应镜像时拒绝降级。

    sdk_list 必须来自 sdkmanager --list --channel=0。只读取远端可用包，
    防止 Runner 已安装的预览包参与选择；命名预览和扩展 SDK 不作为正式 API。
    """
    platforms: set[int] = set()
    images: set[int] = set()
    available = False
    for line in sdk_list.splitlines():
        stripped = line.strip()
        if stripped == "Available Packages:":
            available = True
            continue
        if not available:
            continue
        if stripped.endswith(":"):
            break
        package = line.split("|", 1)[0].strip()
        platform = re.fullmatch(r"platforms;android-([0-9]+)", package)
        image = re.fullmatch(r"system-images;android-([0-9]+);google_apis;x86_64", package)
        if platform:
            platforms.add(int(platform.group(1)))
        if image:
            images.add(int(image.group(1)))
    if not platforms:
        raise ValueError("SDK 稳定通道未返回正式 Android 平台，请检查 SDK 查询结果")
    latest = max(platforms)
    if latest not in images:
        raise ValueError(f"最新稳定 Android API {latest} 暂无 Google APIs x86_64 镜像，请待镜像就绪后重跑")
    return latest


def adb(*args: str, timeout: int = 30) -> str:
    """执行 adb 命令并返回输出；设备或命令失败时立即终止发布检查。"""
    try:
        result = subprocess.run(
            ("adb", *args), capture_output=True, text=True, timeout=timeout, check=True
        )
    except subprocess.CalledProcessError as error:
        raise RuntimeError(
            f"adb 命令失败：{' '.join(args)}\n{error.stdout}\n{error.stderr}"
        ) from error
    return result.stdout


def visible_texts(xml: str) -> set[str]:
    """从当前界面的无障碍层级提取应用文本和描述，供弹窗断言使用。"""
    root = ET.fromstring(xml)
    return {
        value
        for node in root.iter("node")
        if node.attrib.get("package") == PACKAGE
        for key in ("text", "content-desc")
        if (value := node.attrib.get(key))
    }


def verify(apk: Path) -> None:
    """安装 release APK，通过页面链接强出弹窗，并在 30 秒内核对可见文案。"""
    if not apk.is_file():
        raise FileNotFoundError(f"release 测试包不存在：{apk}")
    with zipfile.ZipFile(apk) as archive:
        abis = {
            name.split("/")[1]
            for name in archive.namelist()
            if name.startswith("lib/") and name.endswith(".so")
        }
    if abis != {"x86_64"}:
        raise RuntimeError(f"CI release 测试包的 ABI 必须只有 x86_64，实际为 {sorted(abis)}")

    adb("install", "-r", str(apk), timeout=120)
    adb("shell", "am", "force-stop", PACKAGE)
    query = urlencode(
        {
            "versionName": "CI-test",
            "updateContent": "CI 更新弹窗测试",
            "downloadUrl": "https://example.com/cyxbs.apk",
        }
    )
    # adb shell 接收一条完整命令，单引号防止 URL 中的 & 被设备 shell 解释。
    command = (
        "am start -W -a android.intent.action.VIEW "
        f"-d 'cyxbs://dialog/update?{query}' -p {PACKAGE}"
    )
    adb("shell", command, timeout=60)

    deadline = time.monotonic() + 30
    observed: set[str] = set()
    while time.monotonic() < deadline:
        try:
            adb("shell", "uiautomator", "dump", DUMP_PATH, timeout=15)
            observed = visible_texts(adb("exec-out", "cat", DUMP_PATH))
        except (RuntimeError, subprocess.TimeoutExpired, ET.ParseError):
            # 冷启动期间窗口树可能暂时不可用，继续等待最终界面。
            time.sleep(1)
            continue
        if all(any(expected in text for text in observed) for expected in EXPECTED_TEXT):
            print("release 包更新弹窗已显示：标题、测试文案和更新按钮均可见")
            return
        time.sleep(1)

    raise RuntimeError(
        "release 包未显示完整更新弹窗；当前应用可见文本："
        + repr(sorted(observed)[:30])
    )


def main() -> None:
    """选择最新稳定模拟器 API，或安装 APK 并运行界面检查；选择模式仅输出 API。"""
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--apk", type=Path)
    mode.add_argument("--sdk-list", type=Path)
    args = parser.parse_args()
    if args.sdk_list is not None:
        print(select_emulator_api(args.sdk_list.read_text(encoding="utf-8")))
    else:
        verify(args.apk)


if __name__ == "__main__":
    main()
