"""验证线上版本变化会阻止上传，且 .Apk 正式产物能兼容官网上传。"""

import importlib
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch


class PublishUpdateServiceTests(unittest.TestCase):
    """覆盖线上版本快照失效及 .Apk 正式产物上传边界。"""

    def test_rejects_changed_online_name_before_upload(self) -> None:
        """线上 code 未变但版本名改变时，也不能上传旧候选 APK。"""
        # 本地无需安装网络库；使用替身只验证上传前的版本门禁。
        with patch.dict(sys.modules, {"requests": MagicMock()}):
            service = importlib.import_module("publish_update_service")
        with tempfile.TemporaryDirectory() as temp, \
                patch.object(service.requests, "get") as get, \
                patch.object(service.requests, "Session") as session_type:
            apk = Path(temp) / "candidate.apk"
            apk.write_bytes(b"apk")
            get.return_value.json.return_value = {
                "version_code": 94,
                "version_name": "7.0.2",
            }
            metadata = {"versionCode": 95, "versionName": "7.1.1"}
            with self.assertRaisesRegex(ValueError, "线上版本在候选检查后发生变化"):
                service.publish(apk, metadata, "更新内容", "test-token", (94, "7.0.0"))
            session_type.return_value.post.assert_not_called()

    def test_rejects_changed_invalid_online_name_before_upload(self) -> None:
        """允许异常版本名发版时，发布前仍须匹配检查时的原始值。"""
        with patch.dict(sys.modules, {"requests": MagicMock()}):
            service = importlib.import_module("publish_update_service")
        with tempfile.TemporaryDirectory() as temp, \
                patch.object(service.requests, "get") as get, \
                patch.object(service.requests, "Session") as session_type:
            apk = Path(temp) / "candidate.apk"
            apk.write_bytes(b"apk")
            get.return_value.json.return_value = {
                "version_code": 94,
                "version_name": "错误值 B",
            }
            metadata = {"versionCode": 95, "versionName": "7.1.1"}
            with self.assertRaisesRegex(ValueError, "线上版本在候选检查后发生变化"):
                service.publish(apk, metadata, "更新内容", "test-token", (94, "错误值 A"))
            session_type.return_value.post.assert_not_called()

    def test_uploads_apk_artifact_with_compatible_multipart_name(self) -> None:
        """GitHub 分享产物保持 .Apk，官网上传沿用小写 .apk 文件名且复用原始字节。"""
        with patch.dict(sys.modules, {"requests": MagicMock()}):
            service = importlib.import_module("publish_update_service")
        with tempfile.TemporaryDirectory() as temp, \
                patch.object(service.requests, "get") as get, \
                patch.object(service.requests, "Session") as session_type, \
                patch.object(service, "verify_download") as verify_download:
            apk = Path(temp) / "CyxbsMobile-v7.1.1-95.Apk"
            apk.write_bytes(b"signed apk")
            get.return_value.json.return_value = {"version_code": 94, "version_name": "7.0.0"}
            uploaded_url = "https://app.redrock.team/CyxbsMobile-v7.1.1-95.apk"
            upload = MagicMock()
            upload.json.return_value = {"ok": True, "data": uploaded_url}
            written = MagicMock()
            written.json.return_value = {
                "apk_url": uploaded_url, "update_content": "更新内容",
                "version_code": 95, "version_name": "7.1.1",
            }
            session_type.return_value.post.side_effect = [upload, written]

            result = service.publish(apk, {"versionCode": 95, "versionName": "7.1.1"},
                                     "更新内容", "test-token", (94, "7.0.0"))

            self.assertEqual(result, uploaded_url)
            self.assertEqual(session_type.return_value.post.call_args_list[0].kwargs["files"]["file"][0],
                             "CyxbsMobile-v7.1.1-95.apk")
            self.assertTrue(apk.is_file())
            verify_download.assert_called_once()


if __name__ == "__main__":
    unittest.main()
