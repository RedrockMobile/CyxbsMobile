"""覆盖 SDK 外部列表解析及最新稳定 Android 镜像选择边界。"""

import unittest

from verify_release_update_dialog import select_emulator_api


class EmulatorApiTests(unittest.TestCase):
    """验证最新 API 选择，并避免预览包或缺失镜像造成隐式降级。"""

    def test_selects_highest_available_api(self) -> None:
        """平台和镜像顺序不固定，重复记录也不能改变最高正式 API。"""
        listing = """Available Packages:
          Path | Version | Description
          platforms;android-36 | 2 | Android SDK Platform 36
          system-images;android-35;google_apis;x86_64 | 1 | Image
          platforms;android-35 | 2 | Android SDK Platform 35
          system-images;android-36;google_apis;x86_64 | 1 | Image
          platforms;android-36 | 2 | Android SDK Platform 36
        """
        self.assertEqual(select_emulator_api(listing), 36)

    def test_ignores_installed_packages_and_updates(self) -> None:
        """只以稳定通道远端可用包为依据，忽略已安装包和更新区块。"""
        listing = """Installed packages:
          platforms;android-99 | 1 | Installed Preview
          system-images;android-99;google_apis;x86_64 | 1 | Installed Preview
        Available Packages:
          platforms;android-36 | 2 | Platform
          system-images;android-36;google_apis;x86_64 | 1 | Image
        Available Updates:
          platforms;android-100 | 2 | Other entry
        """
        self.assertEqual(select_emulator_api(listing), 36)

    def test_ignores_named_previews_and_extension_sdks(self) -> None:
        """只有整数正式 API 参与比较，命名预览和 SDK 扩展不改变选择。"""
        listing = """Available Packages:
          platforms;android-NextPreview | 1 | Preview
          system-images;android-NextPreview;google_apis;x86_64 | 1 | Preview
          platforms;android-36-ext99 | 1 | SDK Extension
          system-images;android-36-ext99;google_apis;x86_64 | 1 | Extension
          platforms;android-36 | 2 | Platform
          system-images;android-36;google_apis;x86_64 | 1 | Image
        """
        self.assertEqual(select_emulator_api(listing), 36)

    def test_rejects_latest_api_without_matching_image(self) -> None:
        """最新 API 镜像未就绪时失败，不能静默选择有镜像的旧平台。"""
        listing = """Available Packages:
          platforms;android-36 | 2 | Platform
          platforms;android-35 | 2 | Platform
          system-images;android-35;google_apis;x86_64 | 1 | Image
          system-images;android-36;google_apis;arm64-v8a | 1 | ARM Image
          system-images;android-36;google_apis_playstore;x86_64 | 1 | Play Store
        """
        with self.assertRaisesRegex(ValueError, "API 36 暂无"):
            select_emulator_api(listing)

    def test_rejects_missing_remote_platforms(self) -> None:
        """网络错误、输出格式变化或仅有本地安装包时应清楚失败。"""
        for listing in ("", "Failed to fetch packages", "Installed packages:\nplatforms;android-36 | 1 | Platform"):
            with self.subTest(listing=listing):
                with self.assertRaisesRegex(ValueError, "未返回正式 Android 平台"):
                    select_emulator_api(listing)


if __name__ == "__main__":
    unittest.main()
