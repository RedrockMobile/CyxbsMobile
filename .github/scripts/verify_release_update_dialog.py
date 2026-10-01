"""在模拟器上安装 CI 专用 release 包，并检查更新弹窗是否真正显示。"""

import argparse
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from urllib.parse import urlencode


PACKAGE = "com.mredrock.cyxbs"
DUMP_PATH = "/sdcard/cyxbs-release-dialog.xml"
EXPECTED_TEXT = ("有新版本更新", "CI 更新弹窗测试", "立即更新")


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
    """读取待测 APK 路径并运行模拟器界面检查。"""
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    args = parser.parse_args()
    verify(args.apk)


if __name__ == "__main__":
    main()
