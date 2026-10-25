"""验证 Bugly 补传绑定正式产物、识别真实成功，并避免泄露上传密钥。"""

import contextlib
import io
import json
import os
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import Mock, patch

import publish_bugly_mapping as bugly


class BuglyMappingTests(unittest.TestCase):
    """使用本地候选产物和模拟网络/Java 结果，不向 Bugly 发出真实上传。"""

    def setUp(self):
        """生成含 ARM64 APK、mapping 和真实哈希的最小正式产物。"""
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.metadata = {"versionName": "7.1.1", "versionCode": 95, "tag": "v7.1.1-95"}
        (self.root / "release-metadata.json").write_text(json.dumps(self.metadata))
        (self.root / "release-head.sha").write_text("candidate-head")
        self.mapping = self.root / "proguardMapping-v7.1.1-95.txt"
        self.mapping.write_text("com.example.Original -> a:\n")
        apk = self.root / "CyxbsMobile-v7.1.1-95.Apk"
        with zipfile.ZipFile(apk, "w") as archive:
            archive.writestr("lib/arm64-v8a/libexample.so", b"arm64")
        (self.root / "SHA256SUMS").write_text("".join(
            f"{bugly.sha256_file(file)}  {file.name}\n" for file in (apk, self.mapping)))
        self.tool = self.root / "tool.jar"
        self.tool.write_bytes(b"test-tool")
        self.addCleanup(patch.stopall)
        patch.object(bugly, "TOOL_SHA256", bugly.sha256_file(self.tool)).start()
        patch.dict(os.environ, {"BUGLY_APP_ID": "ci-app-id", "BUGLY_APP_KEY": "ci-secret-key"}).start()

    def test_upload_uses_formal_version_and_same_mapping_with_redacted_logs(self):
        """预增的工作区版本不能污染上传版本，Java 日志中的凭据必须脱敏。"""
        (self.root / "gradle.properties").write_text("cyxbs.versionName=7.1.2-alpha\n")

        def run(command, **kwargs):
            """在临时目录销毁前核对输入文件和参数。"""
            self.assertEqual(command[command.index("-version") + 1], "7.1.1")
            self.assertEqual(command[command.index("-bundleid") + 1], "com.mredrock.cyxbs")
            self.assertNotIn("-buildNo", command)
            staged = Path(command[command.index("-inputMapping") + 1])
            self.assertEqual(staged.name, "mapping.txt")
            self.assertEqual(staged.read_bytes(), self.mapping.read_bytes())
            self.assertEqual(staged.parent, kwargs["cwd"])
            self.assertEqual(kwargs["timeout"], 900)
            return subprocess.CompletedProcess(command, 0, bugly.SUCCESS_MESSAGE + " ci-app-id ci-secret-key")

        output = io.StringIO()
        with patch.object(bugly.subprocess, "run", side_effect=run), contextlib.redirect_stdout(output):
            self.assertEqual(bugly.upload_mapping(self.root, "candidate-head", self.tool), self.metadata)
        self.assertNotIn("ci-secret-key", output.getvalue())
        self.assertNotIn("ci-app-id", output.getvalue())

    def test_exit_zero_without_final_success_is_failure(self):
        """工具吞异常、仅 HTTP 成功或非零退出都不能判定上传成功。"""
        for code, output in [(0, "上传符号表出错"), (0, "HTTP 200"),
                             (1, bugly.SUCCESS_MESSAGE),
                             (0, bugly.SUCCESS_MESSAGE + " 上传符号表出错")]:
            with self.subTest(code=code, output=output), self.assertRaises(RuntimeError):
                bugly.check_upload_result(code, output)
        bugly.check_upload_result(0, bugly.SUCCESS_MESSAGE)

    def test_missing_key_stops_before_downloading_or_running_java(self):
        """未配置可选上传密钥时不执行外部工具。"""
        with patch.dict(os.environ, {"BUGLY_APP_KEY": ""}), \
                patch.object(bugly, "download_tool") as download, \
                patch.object(bugly.subprocess, "run") as java:
            with self.assertRaisesRegex(ValueError, "BUGLY_APP_KEY"):
                bugly.upload_mapping(self.root, "candidate-head")
            download.assert_not_called()
            java.assert_not_called()

    def test_tampered_mapping_or_wrong_source_is_rejected(self):
        """拒绝其他 PR 的产物，以及下载后被修改的 mapping。"""
        with patch.object(bugly.subprocess, "run") as java:
            with self.assertRaisesRegex(ValueError, "PR 提交不匹配"):
                bugly.upload_mapping(self.root, "other-head", self.tool)
            self.mapping.write_text("changed")
            with self.assertRaisesRegex(ValueError, "哈希不匹配"):
                bugly.upload_mapping(self.root, "candidate-head", self.tool)
            java.assert_not_called()

    def test_unknown_tool_is_rejected(self):
        """即使显式指定本地 JAR，也不能跳过工具哈希校验。"""
        self.tool.write_bytes(b"changed")
        with patch.object(bugly.subprocess, "run") as java:
            with self.assertRaisesRegex(ValueError, "SHA256"):
                bugly.upload_mapping(self.root, "candidate-head", self.tool)
            java.assert_not_called()

    def test_timeout_does_not_expose_java_command(self):
        """超时异常自带完整命令，需转换为不含 App Key 的中文提示。"""
        with patch.object(bugly.subprocess, "run", side_effect=subprocess.TimeoutExpired(
                ["java", "-appkey", "ci-secret-key"], 900)):
            with self.assertRaisesRegex(RuntimeError, "超过 15 分钟") as error:
                bugly.upload_mapping(self.root, "candidate-head", self.tool)
        self.assertNotIn("ci-secret-key", str(error.exception))

    def test_official_zip_extracts_only_pinned_jar(self):
        """下载压缩包只取指定 JAR，忽略其他文件，防止路径越界解压。"""
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as archive:
            archive.writestr("buglyqq-upload-symbol-v3.3.5/buglyqq-upload-symbol.jar", b"test-tool")
            archive.writestr("../../unrelated.txt", b"ignore")
        response = Mock()
        response.iter_content.return_value = [data.getvalue()]
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        with patch.object(bugly.requests, "get", return_value=response) as download:
            result = bugly.download_tool(self.root)
        self.assertEqual(result.read_bytes(), b"test-tool")
        response.raise_for_status.assert_called_once()
        self.assertEqual(download.call_args.args[0], bugly.TOOL_URL)
        self.assertEqual(download.call_args.kwargs["headers"]["Referer"], "https://bugly.qq.com/v2/downloads")
        self.assertFalse((self.root.parent / "unrelated.txt").exists())

    def test_cli_failure_is_chinese_warning_with_nonzero_exit(self):
        """脚本明确报告失败，由 CI 的 continue-on-error 保持发版成功。"""
        output = io.StringIO()
        with patch("sys.argv", ["upload", "--artifacts", str(self.root), "--head", "candidate-head"]), \
                patch.object(bugly, "upload_mapping", side_effect=ValueError("缺少密钥")), \
                contextlib.redirect_stdout(output):
            with self.assertRaises(SystemExit) as error:
                bugly.main()
        self.assertEqual(error.exception.code, 1)
        self.assertIn("::warning::", output.getvalue())
        self.assertIn("不影响已完成的发版", output.getvalue())
        self.assertNotIn("::error::", output.getvalue())


if __name__ == "__main__":
    unittest.main()
