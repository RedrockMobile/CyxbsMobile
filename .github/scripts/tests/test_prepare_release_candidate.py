"""验证并行 Runner 复用版本检查快照的实际 Shell 注入流程。"""

import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


class CandidateSnapshotTests(unittest.TestCase):
    """在隔离 Git 仓库测试快照一致性，无网络或真实项目写入。"""

    def setUp(self) -> None:
        """创建可校验基线提交及最小版本文件，复用真实发版脚本。"""
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.scripts = Path(__file__).resolve().parents[1]
        target = self.root / '.github/scripts'
        target.mkdir(parents=True)
        shutil.copy2(self.scripts / 'release_version.py', target / 'release_version.py')
        (self.root / 'build-logic').mkdir()
        (self.root / 'runner-temp').mkdir()
        (self.root / 'gradle.properties').write_text(
            'other=value\ncyxbs.versionCode=12\ncyxbs.versionName=1.0.0-alpha\n', encoding='utf-8')
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        # 临时基线无需签名，避免测试依赖开发者本机的 GPG 凭据和提交钩子。
        subprocess.run(['git', '-c', 'commit.gpgsign=false', '-c', 'core.hooksPath=/dev/null', '-c', 'user.name=CI Test',
                        '-c', 'user.email=ci-test@example.com', 'commit', '-q', '--allow-empty',
                        '-m', 'test baseline'], cwd=self.root, check=True)
        base = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=self.root, text=True).strip()
        self.metadata = {'versionName': '7.0.1', 'versionCode': 95, 'tag': 'v7.0.1-95'}
        self.notes = '修复课表显示异常，优化更新弹窗文案，改善应用稳定性和操作体验。'
        self.environment = {
            **os.environ,
            'RUNNER_TEMP': str(self.root / 'runner-temp'),
            'PR_TITLE': 'v7.0.1',
            'PR_BODY': self.notes,
            'CHECKED_ONLINE_RELEASE': json.dumps({'versionCode': 94, 'versionName': '7.0.0'}),
            'CHECKED_RELEASE_METADATA': json.dumps(self.metadata),
            'CHECKED_RELEASE_BASE': base,
        }

    def run_injection(self) -> subprocess.CompletedProcess:
        """执行真实复用入口，返回进程结果以便断言中文错误和版本文件。"""
        return subprocess.run(['bash', str(self.scripts / 'prepare_release_candidate.sh'),
                               '--reuse-checked'], cwd=self.root, env=self.environment,
                              capture_output=True, text=True)

    def test_reuses_checked_snapshot_without_remote_or_network(self) -> None:
        """没有 origin 的 Runner 仍能复用快照，且版本、文案和基线完全一致。"""
        result = self.run_injection()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        temp = self.root / 'runner-temp'
        self.assertEqual(json.loads((temp / 'release-metadata.json').read_text()), self.metadata)
        self.assertEqual((temp / 'release-base.sha').read_text().strip(),
                         self.environment['CHECKED_RELEASE_BASE'])
        self.assertEqual((self.root / 'build-logic/release-notes.txt').read_text(), self.notes + '\n')
        self.assertIn('cyxbs.versionCode=95\n', (self.root / 'gradle.properties').read_text())
        self.assertIn('cyxbs.versionName=7.0.1\n', (self.root / 'gradle.properties').read_text())

    def test_rejects_different_candidate_metadata(self) -> None:
        """Runner 不能忽略检查阶段的预期版本，再自行构建另一版本。"""
        self.environment['CHECKED_RELEASE_METADATA'] = json.dumps({**self.metadata, 'versionCode': 96})
        result = self.run_injection()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('版本与版本检查结果不一致', result.stdout)

    def test_rejects_invalid_base_before_version_injection(self) -> None:
        """基线不是合法提交 SHA 时停止，防止留下无法关联合并关系的候选包。"""
        self.environment['CHECKED_RELEASE_BASE'] = 'not-a-commit'
        result = self.run_injection()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('基线 SHA 非法', result.stderr)
        self.assertIn('cyxbs.versionCode=12\n', (self.root / 'gradle.properties').read_text())


if __name__ == '__main__':
    unittest.main()
