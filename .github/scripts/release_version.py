#!/usr/bin/env python3
"""按线上版本计算候选包版本，并在 release 上预备下一版。"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


VERSION_PATTERN = re.compile(r"v(\d+)\.(\d+)\.(\d+)\Z")
PROPERTY_PATTERN = re.compile(r"^(cyxbs\.version(?:Code|Name))=(.*)$", re.MULTILINE)


def read_versions(path: Path) -> tuple[int, str]:
    """读取已提交的 Gradle 版本；两个受控键必须各出现一次。"""
    matches = PROPERTY_PATTERN.findall(path.read_text(encoding="utf-8"))
    properties = dict(matches)
    if len(matches) != 2 or set(properties) != {"cyxbs.versionCode", "cyxbs.versionName"}:
        raise ValueError(f"{path} 的版本属性缺失或重复")
    code = int(properties["cyxbs.versionCode"])
    name = properties["cyxbs.versionName"]
    if code <= 0 or not re.fullmatch(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", name):
        raise ValueError(f"{path} 的版本值非法")
    return code, name


def parse_online_version_name(name: object) -> tuple[int, int, int] | None:
    """线上版本名只有符合 X.Y.Z 时才参与比较；异常值允许覆盖发版。"""
    if not isinstance(name, str) or not re.fullmatch(r"\d+\.\d+\.\d+", name):
        return None
    try:
        return tuple(int(part) for part in name.split("."))
    except ValueError:
        # 过长数字段也可能无法转成整数，按异常线上值处理。
        return None


def read_online_version(path: Path) -> tuple[int, object]:
    """读取 CI 保存的线上快照；code 必须有效，name 原样保留供发布前复核。"""
    data = json.loads(path.read_text(encoding="utf-8"))
    code = data.get("versionCode")
    if type(code) is not int or code <= 0:
        raise ValueError("线上 versionCode 非法")
    if "versionName" not in data:
        raise ValueError("线上版本快照缺少 versionName 字段")
    return code, data["versionName"]


def fetch_online_version(output: Path) -> None:
    """无令牌读取掌邮公开版本接口，只把版本名与版本号写入快照。"""
    import requests

    response = requests.get("https://app.redrock.team/cyxbsAppUpdate.json", timeout=(30, 60))
    response.raise_for_status()
    data = response.json()
    code = data["version_code"]
    name = data.get("version_name")
    if type(code) is not int or code <= 0:
        raise ValueError("线上 versionCode 非法")
    if parse_online_version_name(name) is None:
        print("::warning::线上 versionName 格式异常，本次允许以 PR 标题覆盖；仍会校验 versionCode。")
    output.write_text(json.dumps({"versionCode": code, "versionName": name}) + "\n", encoding="utf-8")


def prepare(root: Path, online_code: int, online_name: object, title: str, body: str) -> dict:
    """按线上 code 加一及 PR 标题生成候选版本，写入当前 CI 工作区。"""
    match = VERSION_PATTERN.fullmatch(title.strip())
    if not match:
        raise ValueError("PR 标题必须严格为 vX.Y.Z")
    notes = body.strip()
    if len(notes) < 30:
        raise ValueError("PR 正文必须填写至少 30 字的更新内容")
    if len(notes) > 20000:
        raise ValueError("PR 正文过长，请控制在 20000 字符以内")
    if type(online_code) is not int or online_code <= 0:
        raise ValueError("线上 versionCode 非法")
    new_name = title.strip()[1:]
    base_parts = parse_online_version_name(online_name)
    if base_parts is not None and tuple(int(part) for part in match.groups()) <= base_parts:
        raise ValueError("PR 版本号必须高于线上当前版本")
    new_code = online_code + 1
    if new_code >= 2_100_000_000:
        raise ValueError("versionCode 即将超出 Android 可用范围")

    # develop 中已有的版本值不作为发版依据；只要求两个受控键存在且唯一。
    properties_path = root / "gradle.properties"
    content = properties_path.read_text(encoding="utf-8")
    if len(PROPERTY_PATTERN.findall(content)) != 2:
        raise ValueError("gradle.properties 中的版本属性缺失或重复")
    content = re.sub(r"^cyxbs\.versionCode=.*$", f"cyxbs.versionCode={new_code}", content, flags=re.MULTILINE)
    content = re.sub(r"^cyxbs\.versionName=.*$", f"cyxbs.versionName={new_name}", content, flags=re.MULTILINE)
    properties_path.write_text(content, encoding="utf-8")
    (root / "build-logic/release-notes.txt").write_text(notes + "\n", encoding="utf-8")
    return {"versionName": new_name, "versionCode": new_code, "tag": f"v{new_name}-{new_code}"}


def bump(root: Path, metadata: dict) -> int:
    """发布后在 release 将 code 和版本名 z 位各加一，供后续合入 develop。"""
    path = root / "gradle.properties"
    code, name = read_versions(path)
    if (code, name) != (metadata["versionCode"], metadata["versionName"]):
        raise ValueError("release 的版本文件与本次发布不一致")
    next_code = code + 1
    major, minor, patch = (int(part) for part in name.split("."))
    next_name = f"{major}.{minor}.{patch + 1}-alpha"
    content = path.read_text(encoding="utf-8")
    content = re.sub(r"^cyxbs\.versionCode=.*$", f"cyxbs.versionCode={next_code}", content, flags=re.MULTILINE)
    content = re.sub(r"^cyxbs\.versionName=.*$", f"cyxbs.versionName={next_name}", content, flags=re.MULTILINE)
    path.write_text(content, encoding="utf-8")
    return next_code


def main() -> None:
    """用子命令区分线上读取、候选注入和 release 下一版回写。"""
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    online = commands.add_parser("online")
    online.add_argument("--output", type=Path, required=True)
    candidate = commands.add_parser("prepare")
    candidate.add_argument("--root", type=Path, default=Path.cwd())
    candidate.add_argument("--online-file", type=Path, required=True)
    candidate.add_argument("--title", required=True)
    candidate.add_argument("--body-file", type=Path, required=True)
    candidate.add_argument("--output", type=Path, required=True)
    next_version = commands.add_parser("bump")
    next_version.add_argument("--root", type=Path, default=Path.cwd())
    next_version.add_argument("--metadata", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "online":
        fetch_online_version(args.output)
    elif args.command == "prepare":
        code, name = read_online_version(args.online_file)
        metadata = prepare(args.root, code, name, args.title,
                           args.body_file.read_text(encoding="utf-8"))
        args.output.write_text(json.dumps(metadata, ensure_ascii=False) + "\n", encoding="utf-8")
    else:
        metadata = json.loads(args.metadata.read_text(encoding="utf-8"))
        print(bump(args.root, metadata))


if __name__ == "__main__":
    main()
