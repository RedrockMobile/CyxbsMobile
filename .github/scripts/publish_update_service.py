#!/usr/bin/env python3
"""非交互地上传正式 APK，并同步掌邮服务端的版本信息。"""

import argparse
import hashlib
import json
import os
from pathlib import Path

import requests

from release_version import read_online_version


BASE_URL = "https://app.redrock.team"


def sha256_file(path: Path) -> str:
    """分块计算大文件哈希，避免把 APK 全部读入内存。"""
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify_download(session: requests.Session, url: str, apk: Path) -> None:
    """下载服务端返回的文件并核对哈希，防止版本信息指向损坏的包。"""
    if not url.startswith("https://"):
        raise ValueError("服务端 APK 地址必须使用 HTTPS")
    digest = hashlib.sha256()
    # CDN 地址来自服务端响应，不向该地址转发发版 token。
    with requests.get(url, stream=True, timeout=(30, 1800)) as response:
        response.raise_for_status()
        for block in response.iter_content(1024 * 1024):
            if block:
                digest.update(block)
    if digest.hexdigest() != sha256_file(apk):
        raise ValueError("服务端下载的 APK 与本次构建文件不一致")


def publish(apk: Path, metadata: dict, notes: str, token: str,
            checked_online: tuple[int, object]) -> str:
    """校验线上版本仍与候选检查时一致，再上传 APK 并更新版本 JSON。"""
    if not token:
        raise ValueError("缺少 RELEASE_TOKEN")
    if not apk.is_file() or apk.stat().st_size == 0:
        raise ValueError("正式 APK 不存在或为空")
    session = requests.Session()
    session.headers.update({"token": token})
    # 版本 JSON 是公开接口；令牌只用于后续上传和写入请求。
    response = requests.get(f"{BASE_URL}/cyxbsAppUpdate.json", timeout=(30, 60))
    response.raise_for_status()
    current = response.json()
    new_code = metadata["versionCode"]
    new_name = metadata["versionName"]
    if current["version_code"] == new_code:
        # 允许失败后的重跑，但不能拿同一 code 覆盖另一份更新内容。
        if current["version_name"] != new_name or current["update_content"] != notes:
            raise ValueError("线上 versionCode 已被其他版本占用")
        verify_download(session, current["apk_url"], apk)
        return current["apk_url"]
    if (current["version_code"], current.get("version_name")) != checked_online:
        raise ValueError("线上版本在候选检查后发生变化，请重新发起发版检查")
    if current["version_code"] != new_code - 1:
        raise ValueError("线上 versionCode 与本次发布不连续")

    # 分享文件使用 .Apk；上传接口仍按原有小写 .apk 文件名接收同一份内容。
    upload_name = apk.name[:-4] + ".apk" if apk.suffix == ".Apk" else apk.name
    with apk.open("rb") as source:
        response = session.post(
            f"{BASE_URL}/upload_apk",
            files={"file": (upload_name, source, "application/octet-stream")},
            timeout=(30, 18000),
        )
    response.raise_for_status()
    uploaded = response.json()
    if not uploaded.get("ok") or not uploaded.get("data"):
        raise ValueError("服务端没有返回有效 APK 下载地址")
    apk_url = uploaded["data"]
    verify_download(session, apk_url, apk)
    payload = {
        "apk_url": apk_url,
        "update_content": notes,
        "version_code": new_code,
        "version_name": new_name,
    }
    response = session.post(f"{BASE_URL}/cyxbsAppUpdate.json", json=payload, timeout=(30, 60))
    response.raise_for_status()
    result = response.json()
    if any(result.get(key) != value for key, value in payload.items()):
        raise ValueError("服务端保存的版本信息与提交内容不一致")
    return apk_url


def main() -> None:
    """从 CI 环境读取令牌，日志仅输出非敏感的下载链接。"""
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--notes", type=Path, required=True)
    parser.add_argument("--online-file", type=Path, required=True)
    args = parser.parse_args()
    metadata = json.loads(args.metadata.read_text(encoding="utf-8"))
    notes = args.notes.read_text(encoding="utf-8").strip()
    checked_online = read_online_version(args.online_file)
    print(publish(args.apk, metadata, notes, os.environ.get("RELEASE_TOKEN", ""), checked_online))


if __name__ == "__main__":
    main()
