"""覆盖稳定 SDK 选择、真实更新 Intent 及本地/CI ABI 检测边界。"""

import unittest
import io
import json
import tempfile
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch

from verify_release_update_dialog import (
    MAIN_ACTIVITY, PACKAGE, UPDATE_ACTION, check_dialog_content, fetch_online_update,
    foreground_view_intent, select_emulator_api, tap_node, verify, verify_download,
)

ONLINE = {"version_name": "7.0.0", "update_content": "线上更新内容\n第二行保留空格 ",
          "apk_url": "https://cdn.redrock.team/app/掌邮.apk"}


def dialog_xml(info: dict = ONLINE) -> str:
    """生成包含完整线上正文及可点击按钮坐标的界面层级，用于异常条件测试。"""
    root = ET.Element("hierarchy")
    for text in ("有新版本更新", f"最新版本: {info['version_name']}\n{info['update_content']}\n\n点击点击，现在就更新一发吧~", "立即更新"):
        ET.SubElement(root, "node", {"package": PACKAGE, "text": text,
                                   "enabled": "true", "bounds": "[20,40][60,80]"})
    return ET.tostring(root, encoding="unicode")


def browser_dump(url: str = ONLINE["apk_url"], component: str = "com.android.chrome/.ChromeTabbedActivity") -> str:
    """模拟前台浏览器和对应历史记录，完整 URI 应位于同一 Activity 中。"""
    return f"""topResumedActivity=ActivityRecord{{abc u0 {component} t1}}
    * Hist  #1: ActivityRecord{{abc u0 {component} t1}}
      Intent {{ act=android.intent.action.VIEW dat={url} cmp={component} }}
    """


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


class UpdateIntentTests(unittest.TestCase):
    """约束检测入口必须经过应用更新服务，且 ABI 校验不能因本地复现而放宽。"""

    def setUp(self) -> None:
        """入口测试隔离网络与下载点击，正文比对和下载逻辑由独立用例覆盖。"""
        online = patch('verify_release_update_dialog.fetch_online_update', return_value=ONLINE)
        download = patch('verify_release_update_dialog.verify_download')
        online.start()
        self.download = download.start()
        self.addCleanup(online.stop)
        self.addCleanup(download.stop)

    def create_apk(self, root: Path, abi: str) -> Path:
        """生成最小 APK 容器供命令边界测试使用，不触碰真实设备。"""
        apk = root / 'release.apk'
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr(f'lib/{abi}/test.so', b'fixture')
        return apk

    def test_uses_real_update_action_without_injected_content(self) -> None:
        """CI 和 ARM 本地复现都只能发送测试 Intent，不携带模拟更新字段。"""
        xml = dialog_xml()
        for abi in ('x86_64', 'arm64-v8a'):
            with self.subTest(abi=abi), tempfile.TemporaryDirectory() as temp:
                apk = self.create_apk(Path(temp), abi)
                with patch('verify_release_update_dialog.adb', return_value=xml) as command:
                    verify(apk, abi)
                command.assert_any_call('shell', 'am', 'start', '-W', '-p', PACKAGE,
                                        '-a', UPDATE_ACTION, timeout=60)
                command.assert_any_call('shell', 'am', 'start', '-W', '-n', MAIN_ACTIVITY,
                                        '-a', 'android.intent.action.MAIN', timeout=60)
                self.assertFalse(any('cyxbs://dialog/update' in str(call) for call in command.call_args_list))
                self.download.assert_called_with(xml, ONLINE)

    def test_ci_default_rejects_arm64_before_install(self) -> None:
        """未显式指定本地 ABI 时，CI 仍必须拒绝 ARM 包。"""
        with tempfile.TemporaryDirectory() as temp:
            apk = self.create_apk(Path(temp), 'arm64-v8a')
            with patch('verify_release_update_dialog.adb') as command:
                with self.assertRaisesRegex(RuntimeError, '必须只有 x86_64'):
                    verify(apk)
                command.assert_not_called()

    def test_missing_version_information_cannot_pass(self) -> None:
        """只有标题和按钮时不能放行，超时仍报告应用可见文本。"""
        xml = f'''<hierarchy><node package="{PACKAGE}" text="有新版本更新" />
          <node package="{PACKAGE}" text="立即更新" /></hierarchy>'''
        with tempfile.TemporaryDirectory() as temp:
            apk = self.create_apk(Path(temp), 'x86_64')
            with patch('verify_release_update_dialog.adb', return_value=xml), \
                 patch('verify_release_update_dialog.time.monotonic', side_effect=[0, 1, 1, 2, 32]), \
                 patch('verify_release_update_dialog.time.sleep'):
                with self.assertRaisesRegex(RuntimeError, '未显示完整更新弹窗'):
                    verify(apk)

    def test_waits_for_update_without_touching_login_or_privacy(self) -> None:
        """协议框先出现时只等待更新，禁止点击协议、游客入口或输入账号。"""
        privacy = f'''<hierarchy><node package="{PACKAGE}" text="温馨提示" />
          <node package="{PACKAGE}" text="同意并继续" /></hierarchy>'''
        update = dialog_xml()
        # 空窗口不能触发 Intent；登录页协议框出现后，再等待真实更新框。
        snapshots = iter(('<hierarchy />', privacy, update))

        def respond(*args: str, **kwargs: object) -> str:
            """按顺序返回协议框、更新框，模拟真实冷启动窗口切换。"""
            return next(snapshots) if args[:2] == ('exec-out', 'cat') else ''

        with tempfile.TemporaryDirectory() as temp:
            apk = self.create_apk(Path(temp), 'x86_64')
            with patch('verify_release_update_dialog.adb', side_effect=respond) as command, \
                 patch('verify_release_update_dialog.time.sleep'):
                verify(apk)
        self.assertFalse(any(call.args[:2] == ('shell', 'input') for call in command.call_args_list))
        reads = [index for index, call in enumerate(command.call_args_list)
                 if call.args[:2] == ('exec-out', 'cat')]
        triggers = [index for index, call in enumerate(command.call_args_list)
                    if UPDATE_ACTION in call.args]
        self.assertEqual(len(triggers), 1)
        self.assertTrue(reads[1] < triggers[0] < reads[2])


class OnlineContentTests(unittest.TestCase):
    """线上字段与弹窗正文必须完整一致，不能只检查版本前缀。"""

    def test_exact_content_including_whitespace(self) -> None:
        """接口中的换行及末尾空格不应在比较前被删除。"""
        check_dialog_content(dialog_xml(), ONLINE)

    def test_rejects_wrong_version_or_modified_content(self) -> None:
        """候选包版本名、固定测试文案及空格被编码成加号都不能放行。"""
        for field, value, error in (
            ('version_name', '7.0.1', '版本名与线上不一致'),
            ('update_content', 'CI 更新弹窗测试', '完整文案与线上不一致'),
            ('update_content', ONLINE['update_content'].replace(' ', '+'), '完整文案与线上不一致'),
        ):
            with self.subTest(field=field, value=value):
                with self.assertRaisesRegex(RuntimeError, error):
                    check_dialog_content(dialog_xml({**ONLINE, field: value}), ONLINE)

    def test_fetches_unmodified_online_fields(self) -> None:
        """公开接口返回的字段原样用于断言，不依赖第三方 requests 安装。"""
        with patch('verify_release_update_dialog.urlopen', return_value=io.BytesIO(json.dumps(ONLINE).encode())):
            self.assertEqual(fetch_online_update(), ONLINE)

    def test_rejects_unavailable_or_invalid_online_data(self) -> None:
        """接口异常、空字段或无效地址必须阻止检查，不能退化成结构检查。"""
        for info in ([], {**ONLINE, 'version_name': ''}, {**ONLINE, 'apk_url': 'file:///tmp/test.apk'}):
            with self.subTest(info=info), patch('verify_release_update_dialog.urlopen', return_value=io.BytesIO(json.dumps(info).encode())):
                with self.assertRaises(ValueError):
                    fetch_online_update()
        with patch('verify_release_update_dialog.urlopen', side_effect=OSError('连接失败')):
            with self.assertRaisesRegex(RuntimeError, '无法读取线上更新数据'):
                fetch_online_update()


class DownloadTests(unittest.TestCase):
    """真实点击、浏览器目标 URI 及 APK 响应必须全部通过。"""

    def test_ignores_historical_url_when_app_is_foreground(self) -> None:
        """历史任务曾打开正确 URL，也不能证明本次按钮发生了跳转。"""
        dump = browser_dump().replace('topResumedActivity=ActivityRecord{abc', 'topResumedActivity=ActivityRecord{other', 1)
        self.assertIsNone(foreground_view_intent(dump)[1])

    def test_taps_button_and_checks_full_download_url(self) -> None:
        """点击真实按钮坐标，完整路径一致后才读取下载数据。"""
        def respond(*args: str, **kwargs: object) -> str:
            """模拟浏览器解析与实际前台 Intent，保留线上 Unicode URL。"""
            return 'com.android.chrome/.IntentDispatcher\n' if args[0:1] == ('shell',) and len(args) == 2 else browser_dump()

        with patch('verify_release_update_dialog.adb', side_effect=respond) as command, \
             patch('verify_release_update_dialog.urlopen', return_value=io.BytesIO(b'PK\x03\x04')) as request:
            verify_download(dialog_xml(), ONLINE)
        command.assert_any_call('shell', 'input', 'tap', '40', '60')
        self.assertEqual(request.call_args.args[0].get_header('Range'), 'bytes=0-3')

    def test_rejects_wrong_url_and_html_response(self) -> None:
        """同域名下错误路径和 HTTP 200 的 HTML 页面都不能当成有效 APK。"""
        for url, data, error in ((ONLINE['apk_url'] + '?wrong=1', b'PK\x03\x04', '地址与线上不一致'),
                                 (ONLINE['apk_url'], b'<htm', '不是 APK/ZIP')):
            with self.subTest(url=url), \
                 patch('verify_release_update_dialog.adb', side_effect=lambda *args, **kwargs: 'com.android.chrome/.IntentDispatcher\n' if len(args) == 2 else browser_dump(url)), \
                 patch('verify_release_update_dialog.urlopen', return_value=io.BytesIO(data)):
                with self.assertRaisesRegex(RuntimeError, error):
                    verify_download(dialog_xml(), ONLINE)

    def test_rejects_disabled_or_invalid_button_bounds(self) -> None:
        """不允许对禁用按钮、空区域或缺失坐标发出点击。"""
        for attrs in ({'enabled': 'false', 'bounds': '[20,40][60,80]'},
                      {'enabled': 'true', 'bounds': '[0,0][0,0]'}, {'enabled': 'true'}):
            with self.subTest(attrs=attrs), patch('verify_release_update_dialog.adb') as command:
                with self.assertRaises(RuntimeError):
                    tap_node(ET.Element('node', attrs))
                command.assert_not_called()


if __name__ == "__main__":
    unittest.main()
