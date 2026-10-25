#!/usr/bin/env python3
"""复核候选 APK、PR 合并提交、版本回写及 GitHub Release。"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import zipfile
from pathlib import Path

from release_version import parse_online_version_name, read_online_version, read_versions


def sha256_file(path: Path) -> str:
    """分块计算候选附件哈希，避免把 APK 整体读入内存。"""
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def check_artifact(directory: Path, expected_head: str) -> dict:
    """核对候选包版本、来源提交、SHA256、ABI 与 mapping。"""
    metadata = json.loads((directory / "release-metadata.json").read_text(encoding="utf-8"))
    name = metadata.get("versionName")
    code = metadata.get("versionCode")
    if not isinstance(name, str) or not re.fullmatch(r"\d+\.\d+\.\d+", name):
        raise ValueError("候选版本名非法")
    if not isinstance(code, int) or code <= 0 or metadata.get("tag") != f"v{name}-{code}":
        raise ValueError("候选版本号或 tag 非法")
    if (directory / "release-head.sha").read_text(encoding="utf-8").strip() != expected_head:
        raise ValueError("候选产物的 PR 提交不匹配")

    apk = directory / f"CyxbsMobile-{metadata['tag']}.Apk"
    mapping = directory / f"proguardMapping-{metadata['tag']}.txt"
    expected = {apk.name, mapping.name}
    checksums = (directory / "SHA256SUMS").read_text(encoding="utf-8").splitlines()
    actual = set()
    for line in checksums:
        digest, separator, filename = line.partition("  ")
        if not separator or filename not in expected or filename in actual or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise ValueError("候选产物的 SHA256SUMS 非法")
        path = directory / filename
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f"候选产物不存在或为空：{filename}")
        if sha256_file(path) != digest:
            raise ValueError(f"候选产物哈希不匹配：{filename}")
        actual.add(filename)
    if actual != expected:
        raise ValueError("APK 与混淆文件必须各有一份哈希记录")
    with zipfile.ZipFile(apk) as archive:
        abis = {entry.split("/")[1] for entry in archive.namelist()
                if entry.startswith("lib/") and entry.endswith(".so")}
    if abis != {"arm64-v8a"}:
        raise ValueError(f"正式 APK 必须只含 arm64-v8a，实际为 {sorted(abis)}")
    return metadata


def verify_merged_release(root: Path, pr: dict, artifacts: Path,
                          merge_sha: str, repository: str) -> dict:
    """把合并 PR、候选产物及检查时的线上版本绑定到同一次发版。"""
    head = pr["head"]["sha"]
    metadata = check_artifact(artifacts, head)
    notes = (artifacts / "release-notes.txt").read_text(encoding="utf-8").strip()
    if (not pr.get("merged_at") or pr.get("merge_commit_sha") != merge_sha
            or pr["base"]["ref"] != "release" or pr["head"]["ref"] != "develop"
            or pr["head"]["repo"]["full_name"] != repository
            or pr["title"] != f"v{metadata['versionName']}"
            or (pr.get("body") or "").strip() != notes):
        raise ValueError("合并 PR 与候选版本信息不一致")
    parents = subprocess.check_output(
        ["git", "rev-list", "--parents", "-n", "1", merge_sha], cwd=root, text=True,
    ).split()
    base = (artifacts / "release-base.sha").read_text(encoding="utf-8").strip()
    if parents != [merge_sha, base, head]:
        raise ValueError("release 提交必须是已检查基线与 PR 提交的合并提交")
    # 正式版本由 PR 检查时保存的线上快照推导，不依赖 develop 文件中的旧值。
    online_code, online_name = read_online_version(artifacts / "online-release.json")
    if metadata["versionCode"] != online_code + 1:
        raise ValueError("候选 APK 的 versionCode 不是线上版本加一")
    candidate = tuple(int(part) for part in metadata["versionName"].split("."))
    current = parse_online_version_name(online_name)
    if current is not None and candidate <= current:
        raise ValueError("候选 APK 的 versionName 未高于线上版本")
    return metadata


def verify_committed_release(root: Path, parent: str, artifacts: Path) -> None:
    """重跑时只接受紧邻原合并提交且与候选包一致的版本回写提交。"""
    commit = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=root, text=True,
    ).strip()
    parents = subprocess.check_output(
        ["git", "rev-list", "--parents", "-n", "1", commit], cwd=root, text=True,
    ).split()
    if parents != [commit, parent]:
        raise ValueError("release 当前提交不是本次 PR 合并后的版本回写提交")
    subject = subprocess.check_output(
        ["git", "log", "-1", "--format=%s"], cwd=root, text=True,
    ).strip()
    metadata = json.loads((artifacts / "release-metadata.json").read_text(encoding="utf-8"))
    if subject != f":bookmark: 发布{metadata['versionName']}版本（versionCode {metadata['versionCode']}）":
        raise ValueError("release 回写提交的版本标题与候选产物不一致")
    changed = set(subprocess.check_output(
        ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD"],
        cwd=root, text=True,
    ).splitlines())
    if not changed <= {"gradle.properties", "build-logic/release-notes.txt"}:
        raise ValueError("release 回写提交包含版本文件以外的修改")
    code, name = read_versions(root / "gradle.properties")
    if (code, name) != (metadata["versionCode"], metadata["versionName"]):
        raise ValueError("release 回写提交的版本号与候选 APK 不一致")
    notes = (root / "build-logic/release-notes.txt").read_text(encoding="utf-8")
    if notes != (artifacts / "release-notes.txt").read_text(encoding="utf-8"):
        raise ValueError("release 回写提交的更新文案与候选 APK 不一致")


def verify_next_version_commit(root: Path, published: str, artifacts: Path) -> None:
    """核对第二次 release 提交只预增版本号，并且父节点是正式发布提交。"""
    commit = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=root, text=True,
    ).strip()
    parents = subprocess.check_output(
        ["git", "rev-list", "--parents", "-n", "1", commit], cwd=root, text=True,
    ).split()
    if parents != [commit, published]:
        raise ValueError("下一版本提交不是本次正式发布提交的直接子节点")
    metadata = json.loads((artifacts / "release-metadata.json").read_text(encoding="utf-8"))
    major, minor, patch = (int(part) for part in metadata["versionName"].split("."))
    next_name = f"{major}.{minor}.{patch + 1}-alpha"
    next_code = metadata["versionCode"] + 1
    subject = subprocess.check_output(
        ["git", "log", "-1", "--format=%s"], cwd=root, text=True,
    ).strip()
    if subject != f":bookmark: 预备{next_name}版本（versionCode {next_code}）":
        raise ValueError("下一版本提交标题与预增版本不一致")
    changed = set(subprocess.check_output(
        ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD"],
        cwd=root, text=True,
    ).splitlines())
    if changed != {"gradle.properties"}:
        raise ValueError("下一版本提交必须只修改 gradle.properties")
    current = (root / "gradle.properties").read_text(encoding="utf-8")
    published_content = subprocess.check_output(
        ["git", "show", f"{published}:gradle.properties"], cwd=root, text=True,
    )
    # 第二次提交只允许替换两个版本属性，其他 Gradle 配置必须与正式发布提交相同。
    without_values = lambda content: re.sub(
        r"^(cyxbs\.version(?:Code|Name))=.*$", r"\1=", content, flags=re.MULTILINE,
    )
    if without_values(current) != without_values(published_content):
        raise ValueError("下一版本提交修改了版本属性以外的 Gradle 配置")
    if read_versions(root / "gradle.properties") != (next_code, next_name):
        raise ValueError("下一版本提交的 versionCode 或 versionName 不正确")
    if (root / "build-logic/release-notes.txt").read_text(encoding="utf-8") != (artifacts / "release-notes.txt").read_text(encoding="utf-8"):
        raise ValueError("下一版本提交改动了已发布的更新文案")


def verify_github_release(release: dict, artifacts: Path, target: str) -> None:
    """重跑时逐项核对已有 GitHub Release 与候选包、文案和目标提交。"""
    metadata = json.loads((artifacts / "release-metadata.json").read_text(encoding="utf-8"))
    notes = (artifacts / "release-notes.txt").read_text(encoding="utf-8").strip()
    if (release.get("tag_name") != metadata["tag"]
            or release.get("target_commitish") != target
            or release.get("name") != f"v{metadata['versionName']}"
            or (release.get("body") or "").strip() != notes):
        raise ValueError("已有 GitHub Release 的版本、目标提交或更新内容不匹配")
    expected = {
        f"CyxbsMobile-{metadata['tag']}.Apk",
        f"proguardMapping-{metadata['tag']}.txt",
    }
    assets = {asset["name"]: asset for asset in release.get("assets", [])}
    if set(assets) != expected:
        raise ValueError("已有 GitHub Release 的附件不匹配")
    for name in expected:
        digest = sha256_file(artifacts / name)
        # GitHub Assets API 返回 sha256 digest；缺失时拒绝跳过二进制内容校验。
        if assets[name].get("digest") != f"sha256:{digest}":
            raise ValueError(f"已有 GitHub Release 附件哈希不匹配：{name}")


def main() -> None:
    """为 PR 产物、合并提交及发布重跑提供独立的校验子命令。"""
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="command", required=True)
    artifact = commands.add_parser("artifact")
    artifact.add_argument("--directory", type=Path, required=True)
    artifact.add_argument("--head", required=True)
    merged = commands.add_parser("merge")
    merged.add_argument("--root", type=Path, default=Path.cwd())
    merged.add_argument("--pr", type=Path, required=True)
    merged.add_argument("--artifacts", type=Path, required=True)
    committed = commands.add_parser("commit")
    committed.add_argument("--root", type=Path, default=Path.cwd())
    committed.add_argument("--parent", required=True)
    committed.add_argument("--artifacts", type=Path, required=True)
    next_version = commands.add_parser("next")
    next_version.add_argument("--root", type=Path, default=Path.cwd())
    next_version.add_argument("--published", required=True)
    next_version.add_argument("--artifacts", type=Path, required=True)
    github = commands.add_parser("github-release")
    github.add_argument("--release", type=Path, required=True)
    github.add_argument("--artifacts", type=Path, required=True)
    github.add_argument("--target", required=True)
    args = parser.parse_args()
    if args.command == "artifact":
        print(json.dumps(check_artifact(args.directory, args.head), ensure_ascii=False))
    elif args.command == "merge":
        pr = json.loads(args.pr.read_text(encoding="utf-8"))
        result = verify_merged_release(args.root, pr, args.artifacts,
                                       os.environ["GITHUB_SHA"], os.environ["GITHUB_REPOSITORY"])
        print(json.dumps(result, ensure_ascii=False))
    elif args.command == "commit":
        verify_committed_release(args.root, args.parent, args.artifacts)
    elif args.command == "next":
        verify_next_version_commit(args.root, args.published, args.artifacts)
    else:
        release = json.loads(args.release.read_text(encoding="utf-8"))
        verify_github_release(release, args.artifacts, args.target)


if __name__ == "__main__":
    main()
