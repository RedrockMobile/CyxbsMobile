#!/usr/bin/env python3
"""Portable Android debug build/install entry point; Python 3.9+, stdlib only."""

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
WINDOWS = os.name == "nt"
TASK = ":cyxbs-applications:test:assembleDebug"
METADATA = ROOT / "cyxbs-applications/test/build/outputs/apk/debug/output-metadata.json"


class BuildError(Exception):
    pass


def log(message):
    print(message, flush=True)


def properties(path):
    """Read Java properties, including Android Studio's escaped Windows paths."""
    if not path.is_file():
        return {}
    values = {}
    pending = ""
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = pending + line.lstrip()
        if (len(line) - len(line.rstrip("\\"))) % 2:
            pending = line[:-1]
            continue
        pending = ""
        if not line or line.startswith(("#", "!")):
            continue
        match = re.match(r"([^\s=:]+)\s*[=:]?\s*(.*)", line)
        if match:
            def unescape(item):
                token = item.group(1)
                if token.startswith("u") and len(token) == 5:
                    return chr(int(token[1:], 16))
                return {"t": "\t", "n": "\n", "r": "\r", "f": "\f"}.get(token, token)
            values[match[1]] = re.sub(r"\\(u[0-9a-fA-F]{4}|.)", unescape, match[2])
    return values


def command_text(command, powershell=WINDOWS):
    if powershell:
        return "& " + " ".join("'" + str(arg).replace("'", "''") + "'" for arg in command)
    return shlex.join([str(arg) for arg in command])


def execute(command, env, capture=False):
    command = [str(arg) for arg in command]
    result = subprocess.run(
        command, cwd=ROOT, env=env, check=False,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None,
        encoding="utf-8", errors="replace",
    )
    if result.returncode:
        if capture and result.stdout:
            log(result.stdout.rstrip())
        raise BuildError("命令失败（退出码 {}）：{}".format(result.returncode, command_text(command)))
    return result.stdout or ""


def check_java(env):
    suffix = ".exe" if WINDOWS else ""
    home = env.get("JAVA_HOME")
    java = Path(home) / ("bin/java" + suffix) if home else shutil.which("java")
    javac = Path(home) / ("bin/javac" + suffix) if home else shutil.which("javac")
    if not java or not javac or not Path(java).is_file() or not Path(javac).is_file():
        raise BuildError("未找到完整 JDK；请安装 JDK 21 并设置 JAVA_HOME（不要指向 bin 目录）。")
    version = execute([java, "-version"], env, capture=True)
    match = re.search(r'version "(\d+)', version)
    if not match or int(match[1]) < 21:
        raise BuildError("请使用 JDK 21 或兼容的更新版本；仓库 Gradle daemon 要求 JDK 21。\n" + version)
    log("Java：{}\n{}".format(java, version.splitlines()[0]))


def locate_sdk(env):
    local = properties(ROOT / "local.properties").get("sdk.dir")
    configured = local or env.get("ANDROID_HOME") or env.get("ANDROID_SDK_ROOT")
    if configured:
        sdk = Path(configured).expanduser()
        if not sdk.is_absolute():
            sdk = ROOT / sdk
        if not sdk.is_dir():
            raise BuildError("Android SDK 路径不存在：{}；请检查 local.properties / ANDROID_HOME。".format(sdk))
    else:
        if WINDOWS:
            defaults = [Path(env["LOCALAPPDATA"]) / "Android/Sdk"] if env.get("LOCALAPPDATA") else []
        elif sys.platform == "darwin":
            defaults = [Path.home() / "Library/Android/sdk"]
        else:
            defaults = [Path.home() / "Android/Sdk", Path.home() / "Android/sdk"]
        sdk = next((path for path in defaults if path.is_dir()), None)
        if sdk is None:
            raise BuildError("未找到 Android SDK；请设置 ANDROID_HOME 或在 local.properties 配置 sdk.dir。")
    sdk = sdk.resolve()
    # Child-process environment only; never write developers' local.properties.
    env["ANDROID_HOME"] = str(sdk)
    env["ANDROID_SDK_ROOT"] = str(sdk)
    log("Android SDK：{}".format(sdk))
    catalog = (ROOT / "gradle/libs.versions.toml").read_text(encoding="utf-8")
    match = re.search(r'^android-compileSdk\s*=\s*"(\d+)"', catalog, re.MULTILINE)
    if not match:
        raise BuildError("无法读取 android-compileSdk，请检查 gradle/libs.versions.toml。")
    api = match[1]
    platforms = sdk / "platforms"
    installed = platforms.is_dir() and any(
        (path / "android.jar").is_file()
        and properties(path / "source.properties").get("AndroidVersion.ApiLevel", "").split(".")[0] == api
        for path in platforms.iterdir() if path.is_dir()
    )
    if not installed:
        raise BuildError("请在 Android Studio SDK Manager 安装 Android SDK Platform API {}。".format(api))
    tools = sdk / "build-tools"
    if not tools.is_dir() or not any(tools.iterdir()):
        raise BuildError("请在 SDK Manager 安装 Android SDK Build-Tools；具体版本由 AGP 决定。")
    return sdk


def locate_adb(sdk, env):
    explicit = env.get("ADB")
    if explicit:
        found = shutil.which(explicit)
        if not found:
            raise BuildError("ADB 指定的可执行文件不存在或不可执行：{}".format(explicit))
        return str(Path(found).resolve())
    sdk_adb = sdk / "platform-tools" / ("adb.exe" if WINDOWS else "adb")
    return str(sdk_adb) if sdk_adb.is_file() else shutil.which("adb")


def select_device(adb, requested, env):
    output = execute([adb, "devices", "-l"], env, capture=True)
    devices = {}
    for line in output.splitlines():
        # Long listings use padding spaces; short listings may use a tab.
        match = re.match(
            r"^(\S+)\s+(device|offline|unauthorized|recovery|sideload|bootloader|"
            r"host|connecting|authorizing|unknown|no permissions)(?:\s|$)", line,
        )
        if match:
            devices[match[1]] = match[2]
    if requested:
        if requested not in devices:
            raise BuildError("指定设备 {} 未连接。\n{}".format(requested, output))
        serial = requested
    elif len(devices) != 1:
        raise BuildError(
            "需要唯一设备，当前发现 {} 台；无设备请连接并开启 USB 调试，"
            "多设备请使用 make install_and_run SERIAL=设备编号 或 --serial。\n{}".format(len(devices), output)
        )
    else:
        serial = next(iter(devices))
    if devices[serial] != "device":
        raise BuildError("设备 {} 状态为 {}；请解锁手机确认 USB 调试授权，或重新连接。".format(serial, devices[serial]))
    return serial


def read_artifact():
    if not METADATA.is_file():
        raise BuildError("构建未生成 APK 元数据：{}；不会回退安装历史 APK。".format(METADATA))
    try:
        data = json.loads(METADATA.read_text(encoding="utf-8"))
        elements = data["elements"]
        if len(elements) != 1 or elements[0].get("filters"):
            raise BuildError("当前入口仅支持单个通用 APK；检测到 split/filter 产物，拒绝自动选择。")
        item = elements[0]
        apk = (METADATA.parent / item["outputFile"]).resolve()
        if METADATA.parent.resolve() not in apk.parents or apk.suffix.lower() != ".apk":
            raise BuildError("APK 元数据中的输出路径无效。")
        if not apk.is_file() or apk.stat().st_size == 0:
            raise BuildError("元数据对应的 APK 不存在或为空：{}".format(apk))
        package = data["applicationId"]
        if not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+", package):
            raise BuildError("APK 元数据中的 applicationId 无效。")
        return apk, package, item["versionName"], item["versionCode"]
    except (ValueError, KeyError, TypeError, IndexError) as error:
        raise BuildError("无法解析 APK 元数据：{}".format(error)) from error


def print_artifact(artifact, adb, serial):
    apk, package, name, code = artifact
    log("\n构建成功\nAPK：{}\n大小：{:.2f} MiB\n包名：{}\n版本：{} ({})".format(
        apk, apk.stat().st_size / (1024 * 1024), package, name, code,
    ))
    command = [adb or "adb"] + (["-s", serial] if serial else []) + ["install", "-r", str(apk)]
    if WINDOWS:
        log("安装命令（PowerShell）：\n" + command_text(command))
        log("安装命令（CMD）：\n" + subprocess.list2cmdline(command))
    else:
        log("安装命令（单设备）：\n" + command_text(command))
    if not adb:
        log("提示：未找到 adb；只编译不受影响，安装前请安装 SDK Platform-Tools 并配置 PATH。")
    log("编译、安装并启动：make install_and_run\n")


def install_and_launch(artifact, adb, serial, env, total):
    apk, package, _, _ = artifact
    select_device(adb, serial, env)  # Recheck the same device after the potentially long build.
    base = [adb, "-s", serial]
    log("正在覆盖安装，保留应用数据……")
    output = execute(base + ["install", "-r", str(apk)], env, capture=True)
    log(output.rstrip())
    if not re.search(r"^Success\s*$", output, re.MULTILINE):
        raise BuildError("adb 未确认安装成功；不启动应用，也不会自动卸载旧包。")
    log("[4/{}] 已安装到 {}".format(total, serial))
    output = execute(base + [
        "shell", "cmd", "package", "resolve-activity", "--brief",
        "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", package,
    ], env, capture=True)
    component = next((line.strip() for line in output.splitlines()
                      if re.fullmatch(r"[\w.$]+/[\w.$]+", line.strip())), None)
    if not component:
        raise BuildError("找不到应用的启动 Activity：\n" + output)
    output = execute(base + [
        "shell", "am", "start", "-W", "-n", component,
        "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
    ], env, capture=True)
    log(output.rstrip())
    if not re.search(r"^Status:\s*ok\s*$", output, re.MULTILINE) or re.search(r"Error:|Exception", output):
        raise BuildError("Activity 启动未成功，请查看上方 adb 输出。")
    log("[5/{}] 应用已启动：{}".format(total, package))


def main():
    if sys.version_info < (3, 9):
        raise BuildError("请使用 Python 3.9 或更新版本，无需安装 pip 依赖。")
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("debug", "install_and_run"))
    parser.add_argument("--serial", default=os.environ.get("SERIAL", ""), help="目标 adb 设备编号")
    parser.add_argument("--console", choices=("auto", "rich", "plain"), default=os.environ.get("CONSOLE", "auto"))
    args = parser.parse_args()
    if args.console not in ("auto", "rich", "plain"):
        raise BuildError("CONSOLE 只能是 auto、rich 或 plain。")
    env = os.environ.copy()
    total = 5 if args.action == "install_and_run" else 3
    wrapper = ROOT / ("gradlew.bat" if WINDOWS else "gradlew")
    if not wrapper.is_file():
        raise BuildError("未找到仓库 Gradle Wrapper：{}".format(wrapper))
    check_java(env)
    sdk = locate_sdk(env)
    adb = locate_adb(sdk, env)
    serial = ""
    if args.action == "install_and_run":
        if not adb:
            raise BuildError("安装需要 adb，请在 SDK Manager 安装 Android SDK Platform-Tools。")
        serial = select_device(adb, args.serial, env)
        log("目标设备：{}".format(serial))
    log("[1/{}] 环境检查完成".format(total))
    console = args.console
    if console == "auto":
        console = "rich" if sys.stdout.isatty() and not env.get("CI") else "plain"
    # Relative .bat path avoids Windows cmd.exe quoting issues with spaces in repo paths.
    # On Unix, sh also works when a checkout has lost gradlew's executable bit.
    command = [".\\gradlew.bat"] if WINDOWS else ["sh", str(wrapper)]
    command += [TASK, "--console=" + console, "--stacktrace", "--no-configuration-cache"]
    log("开始增量构建（不执行 clean），进度由 Gradle 实时输出。\n" + command_text(command))
    execute(command, env)
    log("[2/{}] Gradle Debug 构建完成".format(total))
    artifact = read_artifact()
    print_artifact(artifact, adb, serial)
    log("[3/{}] APK 信息已确认".format(total))
    if args.action == "install_and_run":
        install_and_launch(artifact, adb, serial, env, total)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        log("\n已取消，不继续安装或启动。")
        sys.exit(130)
    except (BuildError, OSError) as error:
        print("\n错误：{}".format(error), file=sys.stderr, flush=True)
        print("请参阅 doce/android-command-line.md；不会自动卸载应用或安装历史 APK。", file=sys.stderr)
        sys.exit(1)
