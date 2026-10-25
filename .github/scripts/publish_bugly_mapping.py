#!/usr/bin/env python3
"""发版完成后，把同一份正式 ARM64 mapping 补传到 Bugly；由 CI 将失败降级为警告。"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

import requests

from release_verify import check_artifact, sha256_file


# 与官网和本地说明文档配套的 3.3.5 JAR 完全一致；升级时同步核对版本、哈希和成功日志。
TOOL_URL = "https://bugly.qq.com/v2/sdk?id=d796e9d7-0423-422f-9eb9-63b6e16ef4f9"
TOOL_SHA256 = "351273b825254d2c2a83bcb5761ef908b1a1b278f54ee018d9555b5381e15f68"
APP_PACKAGE = "com.mredrock.cyxbs"
SUCCESS_MESSAGE = "上传成功！您可以在异常详情页点击"


def verify_tool(tool: Path) -> None:
    """仅允许执行已核实的官方 3.3.5 JAR，本地指定工具同样需要核对哈希。"""
    if not tool.is_file() or sha256_file(tool) != TOOL_SHA256:
        raise ValueError("Bugly 上传工具缺失或 SHA256 不一致，请核对官方 3.3.5 工具")


def download_tool(directory: Path) -> Path:
    """从官网流式下载 ZIP，仅提取唯一 JAR 并验哈希，不解压其他文件或提交二进制。"""
    archive = directory / "bugly-symbol.zip"
    # 官网下载需要下载页来源与浏览器请求头，不需要账号或上传密钥。
    headers = {"User-Agent": "Mozilla/5.0", "Referer": "https://bugly.qq.com/v2/downloads"}
    with requests.get(TOOL_URL, headers=headers, stream=True, timeout=(30, 180)) as response:
        response.raise_for_status()
        with archive.open("wb") as target:
            for block in response.iter_content(1024 * 1024):
                target.write(block)
    tool = directory / "buglyqq-upload-symbol.jar"
    with zipfile.ZipFile(archive) as package:
        jars = [name for name in package.namelist() if name.endswith("/buglyqq-upload-symbol.jar")]
        if len(jars) != 1:
            raise ValueError("Bugly 工具 ZIP 必须包含唯一的 buglyqq-upload-symbol.jar")
        with package.open(jars[0]) as source, tool.open("wb") as target:
            shutil.copyfileobj(source, target)
    verify_tool(tool)
    return tool


def check_upload_result(returncode: int, output: str) -> None:
    """工具可能吞异常并返回 0，必须同时看到最终成功提示才能判定补传成功。"""
    if returncode != 0 or SUCCESS_MESSAGE not in output or "上传符号表出错" in output:
        raise RuntimeError("Bugly mapping 上传未确认成功，请查看上方脱敏日志后补传")


def upload_mapping(artifacts: Path, expected_head: str, tool: Path | None = None) -> dict:
    """复核正式产物来源、版本、哈希后上传；密钥取环境变量，绝不读取预增后的版本文件。"""
    credentials = {}
    for name in ("BUGLY_APP_ID", "BUGLY_APP_KEY"):
        value = os.environ.get(name, "").strip()
        if not value:
            raise ValueError(f"缺少 GitHub Actions Secret {name}，无法上传 Bugly mapping")
        credentials[name] = value
    metadata = check_artifact(artifacts, expected_head)
    mapping = artifacts / f"proguardMapping-{metadata['tag']}.txt"
    with tempfile.TemporaryDirectory(prefix="cyxbs-bugly-") as temporary:
        directory = Path(temporary)
        if tool is None:
            tool = download_tool(directory)
        else:
            tool = tool.resolve()
            verify_tool(tool)
        # 只改工具输入文件名；内容仍是 CI 已校验、GitHub Release 保存的同一份 ARM64 mapping。
        staged_mapping = directory / "mapping.txt"
        shutil.copyfile(mapping, staged_mapping)
        command = [
            "java", "-Dfile.encoding=UTF-8", "-jar", str(tool),
            "-appid", credentials["BUGLY_APP_ID"], "-appkey", credentials["BUGLY_APP_KEY"],
            "-bundleid", APP_PACKAGE, "-version", metadata["versionName"],
            "-platform", "Android", "-inputMapping", str(staged_mapping),
        ]
        # SDK 只设置 appVersion，没有设置构建号，不能擅自把 versionCode 作为 buildNo。
        # 工具会打印参数和产生中间文件，因此隔离工作目录并在输出日志前移除密钥。
        try:
            result = subprocess.run(command, cwd=directory, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, text=True, encoding="utf-8",
                                    errors="replace", timeout=900, check=False)
        except subprocess.TimeoutExpired:
            raise RuntimeError("Bugly mapping 上传超过 15 分钟，请稍后补传") from None
        output = result.stdout
        for secret in credentials.values():
            output = output.replace(secret, "***")
        print(output, end="" if output.endswith("\n") else "\n")
        check_upload_result(result.returncode, output)
    return metadata


def main() -> None:
    """CI 最后调用本命令；失败返回非零并输出中文警告，工作流负责保持发版成功状态。"""
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--tool", type=Path)
    args = parser.parse_args()
    try:
        metadata = upload_mapping(args.artifacts, args.head, args.tool)
    except (OSError, ValueError, RuntimeError, requests.RequestException, zipfile.BadZipFile) as error:
        # 不打印含 Java 命令或 App Key 的异常堆栈；非零结果由 continue-on-error 接住。
        print(f"::warning::Bugly mapping 补传失败，不影响已完成的发版：{error}")
        raise SystemExit(1) from None
    message = f"Bugly mapping 上传成功：{APP_PACKAGE} / {metadata['versionName']}（code {metadata['versionCode']}）"
    print(message)
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(summary, "a", encoding="utf-8") as target:
            target.write(f"\n### Bugly mapping\n\n{message}\n")


if __name__ == "__main__":
    main()
