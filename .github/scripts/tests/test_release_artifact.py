"""验证发版产物的哈希与 ABI 门禁。"""

import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from release_verify import check_artifact as check, verify_github_release as verify


class ReleaseArtifactTest(unittest.TestCase):
    """用小型 APK ZIP 模拟候选包，覆盖关键拒绝路径。"""

    def setUp(self) -> None:
        """创建版本元数据、arm64 APK 和对应 mapping 的最小产物集。"""
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.metadata = {"versionName": "7.0.0", "versionCode": 95, "tag": "v7.0.0-95"}
        (self.root / "release-metadata.json").write_text(json.dumps(self.metadata))
        (self.root / "release-head.sha").write_text("head-sha\n")
        (self.root / "release-notes.txt").write_text("更新内容\n")
        self.apk = self.root / "CyxbsMobile-v7.0.0-95.Apk"
        self.mapping = self.root / "proguardMapping-v7.0.0-95.txt"
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("lib/arm64-v8a/libcyxbs.so", b"arm64")
        self.mapping.write_text("mapping")
        self._write_checksums()

    def _write_checksums(self) -> None:
        """按 GitHub Actions 的 sha256sum 格式记录两份文件。"""
        lines = [f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}"
                 for path in (self.apk, self.mapping)]
        (self.root / "SHA256SUMS").write_text("\n".join(lines) + "\n")

    def test_accepts_exact_arm64_candidate(self) -> None:
        """正确版本、哈希和 ABI 可继续进入发布流程。"""
        self.assertEqual(check(self.root, "head-sha"), self.metadata)

    def test_rejects_mutated_apk(self) -> None:
        """候选包在检查后被替换时必须拒绝。"""
        self.apk.write_bytes(self.apk.read_bytes() + b"changed")
        with self.assertRaisesRegex(ValueError, "哈希不匹配"):
            check(self.root, "head-sha")

    def test_rejects_x86_in_official_apk(self) -> None:
        """正式包混入模拟器 ABI 时必须拒绝。"""
        with zipfile.ZipFile(self.apk, "a") as archive:
            archive.writestr("lib/x86_64/libcyxbs.so", b"x86")
        self._write_checksums()
        with self.assertRaisesRegex(ValueError, "arm64-v8a"):
            check(self.root, "head-sha")

    def test_existing_release_must_match_asset_hashes(self) -> None:
        """发布工作流重跑只能接受附件内容完全一致的 Release。"""
        release = {
            "tag_name": self.metadata["tag"], "target_commitish": "merge-sha",
            "name": "v7.0.0", "body": "更新内容",
            "assets": [{"name": path.name,
                        "digest": "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()}
                       for path in (self.apk, self.mapping)],
        }
        verify(release, self.root, "merge-sha")
        release["assets"][0]["digest"] = "sha256:" + "0" * 64
        with self.assertRaisesRegex(ValueError, "哈希不匹配"):
            verify(release, self.root, "merge-sha")


if __name__ == "__main__":
    unittest.main()
