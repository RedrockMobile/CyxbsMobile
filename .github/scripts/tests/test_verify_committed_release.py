"""验证发布工作流重跑时只能复用正确的 release 元数据提交。"""

import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from release_verify import verify_committed_release as verify, verify_next_version_commit
from release_version import bump


class CommittedReleaseTests(unittest.TestCase):
    """使用临时 Git 仓库检查提交父节点及候选内容约束。"""

    def setUp(self) -> None:
        """准备模拟合并提交与候选版本文件，不触碰项目仓库。"""
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "repo"
        self.root.mkdir()
        self.artifacts = Path(self.temp.name) / "artifacts"
        self.artifacts.mkdir()
        (self.root / "build-logic").mkdir()
        self.git("init", "-q")
        self.git("config", "user.name", "Test Bot")
        self.git("config", "user.email", "bot@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        (self.root / "gradle.properties").write_text(
            "cyxbs.versionCode=95\ncyxbs.versionName=7.0.0-alpha\n", encoding="utf-8")
        (self.root / "build-logic/release-notes.txt").write_text("旧版\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-q", "-m", "merged PR")
        self.parent = self.git("rev-parse", "HEAD").strip()
        (self.root / "gradle.properties").write_text(
            "cyxbs.versionCode=95\ncyxbs.versionName=7.0.1\n", encoding="utf-8")
        (self.root / "build-logic/release-notes.txt").write_text("更新课表\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-q", "-m", ":bookmark: 发布7.0.1版本（versionCode 95）")
        self.published = self.git("rev-parse", "HEAD").strip()
        (self.artifacts / "release-metadata.json").write_text(json.dumps(
            {"versionCode": 95, "versionName": "7.0.1", "tag": "v7.0.1-95"}), encoding="utf-8")
        (self.artifacts / "release-notes.txt").write_text("更新课表\n", encoding="utf-8")

    def git(self, *args: str) -> str:
        """在临时仓库执行 Git 读写，返回输出供断言父提交。"""
        return subprocess.check_output(["git", *args], cwd=self.root, text=True)

    def test_accepts_exact_recorded_release(self) -> None:
        """回写提交与候选产物完全一致时允许安全重跑。"""
        verify(self.root, self.parent, self.artifacts)

    def test_rejects_mismatched_notes(self) -> None:
        """候选文案变化后不能复用已经回写的提交。"""
        (self.artifacts / "release-notes.txt").write_text("其他内容\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "文案"):
            verify(self.root, self.parent, self.artifacts)

    def test_rejects_wrong_merge_parent(self) -> None:
        """原合并提交 SHA 不匹配时不把其他发布的提交当作本次结果。"""
        with self.assertRaisesRegex(ValueError, "合并"):
            verify(self.root, "0" * 40, self.artifacts)

    def test_rejects_wrong_release_title(self) -> None:
        """回写提交标题的发布版本或 code 不一致时拒绝重跑复用。"""
        self.git("commit", "-q", "--amend", "-m", ":bookmark: 发布7.0.1版本（versionCode 96）")
        with self.assertRaisesRegex(ValueError, "版本标题"):
            verify(self.root, self.parent, self.artifacts)

    def test_accepts_second_commit_for_next_version(self) -> None:
        """正式版发布后在 release 单独提交下一版，保留第一次提交供 tag 指向。"""
        metadata = json.loads((self.artifacts / "release-metadata.json").read_text(encoding="utf-8"))
        bump(self.root, metadata)
        self.git("add", "gradle.properties")
        self.git("commit", "-q", "-m", ":bookmark: 预备7.0.2-alpha版本（versionCode 96）")
        verify_next_version_commit(self.root, self.published, self.artifacts)
        self.assertEqual(self.git("rev-parse", "HEAD^").strip(), self.published)

    def test_next_commit_rejects_unrelated_gradle_change(self) -> None:
        """第二次提交即使版本号正确，也不能夹带其他构建配置变更。"""
        metadata = json.loads((self.artifacts / "release-metadata.json").read_text(encoding="utf-8"))
        bump(self.root, metadata)
        with (self.root / "gradle.properties").open("a", encoding="utf-8") as properties:
            properties.write("other=value\n")
        self.git("add", "gradle.properties")
        self.git("commit", "-q", "-m", ":bookmark: 预备7.0.2-alpha版本（versionCode 96）")
        with self.assertRaisesRegex(ValueError, "以外"):
            verify_next_version_commit(self.root, self.published, self.artifacts)


if __name__ == "__main__":
    unittest.main()
