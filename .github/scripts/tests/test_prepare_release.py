"""发版元数据按线上版本递进的本地回归测试。"""

import json
import tempfile
import unittest
from pathlib import Path

from release_version import bump, prepare, read_online_version


VALID_NOTES = "修复课表显示异常，优化更新弹窗文案，改善应用稳定性和操作体验。"


class PrepareReleaseTests(unittest.TestCase):
    """覆盖线上基线、PR 版本递进及 release 上的下一版预增。"""

    def setUp(self) -> None:
        """为每例创建独立版本文件，避免修改真实项目配置。"""
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "build-logic").mkdir()
        # develop 中的旧值故意与线上无关，发版必须覆盖而不是依赖它。
        (self.root / "gradle.properties").write_text(
            "other=value\ncyxbs.versionCode=12\ncyxbs.versionName=1.0.0-alpha\n", encoding="utf-8")

    def test_prepares_from_online_version_and_multiline_notes(self) -> None:
        """候选包使用线上 code 加一和 PR 版本名，并保留正文换行。"""
        metadata = prepare(self.root, 94, "7.0.0", "v7.0.1", VALID_NOTES + "\n- 优化课表")
        self.assertEqual(metadata, {"versionName": "7.0.1", "versionCode": 95, "tag": "v7.0.1-95"})
        self.assertEqual((self.root / "build-logic/release-notes.txt").read_text(encoding="utf-8"),
                         VALID_NOTES + "\n- 优化课表\n")
        properties = (self.root / "gradle.properties").read_text(encoding="utf-8")
        self.assertIn("other=value\n", properties)
        self.assertIn("cyxbs.versionCode=95\n", properties)
        self.assertIn("cyxbs.versionName=7.0.1\n", properties)

    def test_rejects_old_version_without_writing(self) -> None:
        """PR 版本号不高于线上时不能污染构建输入。"""
        before = (self.root / "gradle.properties").read_text(encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "高于线上"):
            prepare(self.root, 94, "7.0.0", "v7.0.0", VALID_NOTES)
        self.assertEqual((self.root / "gradle.properties").read_text(encoding="utf-8"), before)

    def test_rejects_invalid_title_or_empty_body(self) -> None:
        """无效 PR 信息不会触发发版。"""
        with self.assertRaisesRegex(ValueError, "标题"):
            prepare(self.root, 94, "7.0.0", "release 7.0.1", VALID_NOTES)
        with self.assertRaisesRegex(ValueError, "正文"):
            prepare(self.root, 94, "7.0.0", "v7.0.1", "   ")

    def test_notes_length_boundary(self) -> None:
        """正文按去除首尾空白后的字符数计，30 字可通过，29 字会拒绝。"""
        notes = "更新" * 15
        self.assertEqual(prepare(self.root, 94, "7.0.0", "v7.0.1", notes)["versionCode"], 95)
        with self.assertRaisesRegex(ValueError, "至少 30 字"):
            prepare(self.root, 94, "7.0.0", "v7.0.1", notes[:-1])

    def test_bumps_release_code_and_patch_before_develop_sync(self) -> None:
        """发布 7.1.1/95 后，release 先预留 7.1.2-alpha/96 再合入 develop。"""
        metadata = prepare(self.root, 94, "7.0.0", "v7.1.1", VALID_NOTES)
        self.assertEqual(bump(self.root, metadata), 96)
        properties = (self.root / "gradle.properties").read_text(encoding="utf-8")
        self.assertIn("cyxbs.versionCode=96\n", properties)
        self.assertIn("cyxbs.versionName=7.1.2-alpha\n", properties)

    def test_reads_online_name_and_code_snapshot(self) -> None:
        """线上快照保留异常版本名，同时继续拒绝非法 versionCode。"""
        path = self.root / "online.json"
        path.write_text(json.dumps({"versionCode": 94, "versionName": "7.0.0"}), encoding="utf-8")
        self.assertEqual(read_online_version(path), (94, "7.0.0"))
        path.write_text(json.dumps({"versionCode": 94, "versionName": "7.0.0-alpha"}), encoding="utf-8")
        self.assertEqual(read_online_version(path), (94, "7.0.0-alpha"))
        path.write_text(json.dumps({"versionCode": 0, "versionName": "7.0.0-alpha"}), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "versionCode"):
            read_online_version(path)

    def test_allows_release_over_invalid_online_name(self) -> None:
        """手动写错线上 versionName 时，PR 可凭有效标题和新 code 覆盖。"""
        metadata = prepare(self.root, 94, "手动填错的版本", "v7.0.1", VALID_NOTES)
        self.assertEqual(metadata, {"versionName": "7.0.1", "versionCode": 95, "tag": "v7.0.1-95"})

    def test_requires_unique_gradle_version_keys(self) -> None:
        """当前数值可过期，但发版写入的两个属性必须各有一项。"""
        (self.root / "gradle.properties").write_text("cyxbs.versionCode=12\n", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "缺失或重复"):
            prepare(self.root, 94, "7.0.0", "v7.0.1", VALID_NOTES)


if __name__ == "__main__":
    unittest.main()
