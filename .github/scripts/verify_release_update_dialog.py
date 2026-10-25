"""在模拟器上安装 CI 专用 release 包，并检查更新弹窗是否真正显示。"""

from __future__ import annotations

import argparse
import json
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from urllib.parse import quote, urlsplit
from urllib.request import Request, urlopen


PACKAGE = "com.mredrock.cyxbs"
DUMP_PATH = "/sdcard/cyxbs-release-dialog.xml"
UPDATE_ACTION = "com.mredrock.cyxbs.action.TEST_UPDATE_DIALOG"
MAIN_ACTIVITY = f"{PACKAGE}/com.cyxbs.pages.home.ui.main.MainActivity"
# 文案由应用通过真实更新服务获取；脚本独立读取线上数据进行核对，不注入更新字段。
EXPECTED_TEXT = ("有新版本更新", "最新版本: ", "立即更新")
ONLINE_UPDATE_URL = "https://app.redrock.team/cyxbsAppUpdate.json"


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


def fetch_online_update() -> dict:
    """独立读取公开更新接口，保留原始版本名、完整文案与下载 URL 作为断言基准。"""
    try:
        with urlopen(ONLINE_UPDATE_URL, timeout=30) as response:
            info = json.load(response)
    except Exception as error:
        raise RuntimeError(f"无法读取线上更新数据，不能核对弹窗：{error}") from error
    if not isinstance(info, dict):
        raise ValueError("线上更新接口必须返回 JSON 对象")
    for field in ("version_name", "update_content", "apk_url"):
        if not isinstance(info.get(field), str) or not info[field].strip():
            raise ValueError(f"线上更新接口的 {field} 字段缺失或为空")
    url = urlsplit(info["apk_url"])
    if url.scheme not in ("http", "https") or not url.netloc:
        raise ValueError("线上 apk_url 必须是完整的 HTTP 或 HTTPS 地址")
    return info


def check_dialog_content(xml: str, info: dict) -> None:
    """逐字比对弹窗版本名和完整文案，保留接口中的空格、换行及 UI 固定说明。"""
    texts = visible_texts(xml)
    prefix = f"最新版本: {info['version_name']}\n"
    body = next((text for text in texts if text.startswith("最新版本: ")), "")
    if not body.startswith(prefix):
        raise RuntimeError(f"更新弹窗版本名与线上不一致：期望 {info['version_name']!r}，实际 {body.splitlines()[:1]!r}")
    expected = prefix + info["update_content"] + "\n\n点击点击，现在就更新一发吧~"
    if body != expected:
        raise RuntimeError(f"更新弹窗完整文案与线上不一致：期望 {expected!r}，实际 {body!r}")


def tap_node(node: ET.Element) -> None:
    """点击无障碍节点中心；禁用按钮或无效坐标立即失败，避免误点其他入口。"""
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if node.get("enabled") != "true" or match is None:
        raise RuntimeError("待点击按钮被禁用或缺少有效坐标")
    left, top, right, bottom = map(int, match.groups())
    if right <= left or bottom <= top:
        raise RuntimeError("待点击按钮不在可见区域内")
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def foreground_view_intent(dump: str) -> tuple[str, str | None]:
    """读取当前前台 Activity 的 VIEW URI，忽略历史任务中的旧下载链接。"""
    resumed = re.search(
        r"(?:topResumedActivity=|mResumedActivity:\s*|ResumedActivity:\s*)"
        r"ActivityRecord\{(\S+) u\d+ (\S+) ", dump,
    )
    if resumed is None:
        return "", None
    record_id, component = resumed.groups()
    for block in re.split(r"\n\s*\* Hist\s+#\d+:\s*", dump)[1:]:
        if not block.startswith(f"ActivityRecord{{{record_id} "):
            continue
        intent = re.search(r"\n\s*Intent \{([^\n]+)\}", block)
        if intent and "act=android.intent.action.VIEW " in intent[1]:
            uri = re.search(r"\bdat=(\S+)", intent[1])
            return component, uri[1] if uri else None
    return component, None


def verify_download(xml: str, info: dict) -> None:
    """点击更新按钮，核对前台浏览器的完整 VIEW URL，并确认目标返回 APK 数据。

    Chrome 首次启动只跳过浏览器账号引导，不操作掌邮协议或登录页面。
    不把短暂的 IntentDispatcher 或仅打开浏览器首页视为成功；下载请求只读取 ZIP 头。
    """
    url = info["apk_url"]
    # adb shell 仍经设备上的 shell 解析，线上 URL 中的 & 等字符必须正确引用。
    resolved = adb("shell", "cmd package resolve-activity --brief -a android.intent.action.VIEW -d "
                   + shlex.quote(url))
    components = re.findall(r"^([\w.]+/[\w.$]+)$", resolved, re.MULTILINE)
    if not components or components[-1].startswith((PACKAGE + "/", "android/")):
        raise RuntimeError("模拟器没有可直接处理下载链接的默认浏览器")
    browser = components[-1].split("/", 1)[0]
    buttons = [node for node in ET.fromstring(xml).iter("node")
               if node.get("package") == PACKAGE and node.get("text") == "立即更新"]
    if len(buttons) != 1:
        raise RuntimeError("更新弹窗必须存在唯一的立即更新按钮")
    tap_node(buttons[0])

    deadline = time.monotonic() + 30
    skipped: set[str] = set()
    component, actual_url = "", None
    while time.monotonic() < deadline:
        component, actual_url = foreground_view_intent(adb("shell", "dumpsys", "activity", "activities"))
        # Unicode 地址和等价的百分号编码均可接受，不允许忽略路径或查询参数。
        safe = ":/?#[]@!$&'()*+,;=%"
        if (component.startswith(browser + "/") and actual_url is not None
                and "IntentDispatcher" not in component and "firstrun" not in component.lower()):
            if quote(actual_url, safe=safe) != quote(url, safe=safe):
                raise RuntimeError(f"下载按钮打开的地址与线上不一致：期望 {url!r}，实际 {actual_url!r}")
            try:
                request = Request(quote(url, safe=safe), headers={"Range": "bytes=0-3"})
                with urlopen(request, timeout=30) as response:
                    signature = response.read(4)
                if signature != b"PK\x03\x04":
                    raise ValueError("下载地址返回的内容不是 APK/ZIP 文件")
            except Exception as error:
                raise RuntimeError(f"线上 APK 下载地址不可用：{error}") from error
            print("立即更新按钮检查通过：浏览器打开线上完整下载地址，目标返回 APK 数据")
            return
        if browser == "com.android.chrome" and "firstrun" in component.lower():
            try:
                adb("shell", "uiautomator", "dump", DUMP_PATH, timeout=15)
                nodes = ET.fromstring(adb("exec-out", "cat", DUMP_PATH)).iter("node")
                for node in nodes:
                    key = node.get("resource-id", "")
                    if (node.get("package") == browser and key not in skipped and key in {
                        browser + ":id/signin_fre_dismiss_button",
                        browser + ":id/terms_accept",
                        browser + ":id/negative_button",
                    }):
                        tap_node(node)
                        skipped.add(key)
                        break
            except (RuntimeError, subprocess.TimeoutExpired, ET.ParseError):
                # 浏览器引导切换期间窗口可能暂时不可读，继续等待稳定界面。
                pass
        time.sleep(1)
    raise RuntimeError(f"点击立即更新后未在浏览器打开线上下载地址；前台组件 {component!r}，地址 {actual_url!r}")


def verify(apk: Path, expected_abi: str = "x86_64") -> None:
    """安装 release 包，通过现有测试 Intent 检查真实线上更新弹窗。

    CI 默认只接受 x86_64；Apple Silicon 本地复现可显式指定 arm64-v8a。
    adb 继承 ANDROID_SERIAL，调用方应指定目标模拟器，避免操作其他已连接设备。
    全新安装停留在登录页，不操作协议弹窗或游客入口；更新 Intent 由 Activity 接收。
    """
    if not apk.is_file():
        raise FileNotFoundError(f"release 测试包不存在：{apk}")
    with zipfile.ZipFile(apk) as archive:
        abis = {
            name.split("/")[1]
            for name in archive.namelist()
            if name.startswith("lib/") and name.endswith(".so")
        }
    if abis != {expected_abi}:
        raise RuntimeError(f"release 测试包的 ABI 必须只有 {expected_abi}，实际为 {sorted(abis)}")

    online = fetch_online_update()
    adb("install", "-r", str(apk), timeout=120)
    adb("shell", "am", "force-stop", PACKAGE)
    # 先启动登录页，待界面就绪后再触发更新，避免冷启动导航与弹窗布局争用。
    adb("shell", "am", "start", "-W", "-n", MAIN_ACTIVITY,
        "-a", "android.intent.action.MAIN", timeout=60)

    deadline = time.monotonic() + 30
    observed: set[str] = set()
    update_requested = False
    while time.monotonic() < deadline:
        try:
            adb("shell", "uiautomator", "dump", DUMP_PATH, timeout=15)
            xml = adb("exec-out", "cat", DUMP_PATH)
            observed = visible_texts(xml)
        except (RuntimeError, subprocess.TimeoutExpired, ET.ParseError):
            # 冷启动期间窗口树可能暂时不可用，继续等待最终界面。
            time.sleep(1)
            continue
        if not update_requested:
            if observed:
                # 只等待应用窗口出现，不识别或点击协议按钮；数据仍由应用请求线上接口。
                adb("shell", "am", "start", "-W", "-p", PACKAGE,
                    "-a", UPDATE_ACTION, timeout=60)
                update_requested = True
                deadline = time.monotonic() + 30
            time.sleep(1)
            continue
        if all(any(expected in text for text in observed) for expected in EXPECTED_TEXT):
            check_dialog_content(xml, online)
            print("release 包更新弹窗检查通过：版本名和完整文案与线上一致")
            verify_download(xml, online)
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
    parser.add_argument("--abi", choices=("x86_64", "arm64-v8a"), default="x86_64",
                        help="测试包的唯一 ABI；CI 默认 x86_64，本地 ARM 模拟器可指定 arm64-v8a")
    args = parser.parse_args()
    if args.sdk_list is not None:
        print(select_emulator_api(args.sdk_list.read_text(encoding="utf-8")))
    else:
        verify(args.apk, args.abi)


if __name__ == "__main__":
    main()
