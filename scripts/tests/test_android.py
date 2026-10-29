"""No SDK/device required: python3 -B -m unittest discover -s scripts/tests -v."""

import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("android_build", Path(__file__).resolve().parents[1] / "android.py")
android = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(android)


class AndroidEntryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="cyxbs build ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def write(self, relative, content):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def test_windows_properties_and_unicode_paths(self):
        path = self.write("local.properties", "sdk.dir=C\\:\\\\Users\\\\同学\\\\Android SDK\n")
        self.assertEqual(android.properties(path)["sdk.dir"], "C:\\Users\\同学\\Android SDK")
        path.write_text("sdk.dir=C:/Users/\\u540c\\u5b66/Android\\ SDK\n", encoding="utf-8")
        self.assertEqual(android.properties(path)["sdk.dir"], "C:/Users/同学/Android SDK")

    def test_local_sdk_priority_and_decimal_api(self):
        sdk = self.root / "Android SDK"
        self.write("Android SDK/platforms/android-37.0/source.properties", "AndroidVersion.ApiLevel=37.0\n")
        self.write("Android SDK/platforms/android-37.0/android.jar", "fixture")
        self.write("Android SDK/build-tools/37.0.0/source.properties", "fixture")
        self.write("local.properties", "sdk.dir=" + sdk.as_posix() + "\n")
        self.write("gradle/libs.versions.toml", 'android-compileSdk = "37"\n')
        env = {"ANDROID_HOME": "/invalid", "ANDROID_SDK_ROOT": "/also-invalid"}
        with patch.object(android, "ROOT", self.root), patch.object(android, "log"):
            self.assertEqual(android.locate_sdk(env), sdk.resolve())
        self.assertEqual(env["ANDROID_HOME"], str(sdk.resolve()))
        self.assertEqual(env["ANDROID_HOME"], env["ANDROID_SDK_ROOT"])

    def device(self, output, serial=""):
        with patch.object(android, "execute", return_value=output):
            return android.select_device("adb", serial, {})

    def test_one_device_spaces_or_tabs(self):
        for listing in ("phone\tdevice\n", "phone                  device product:sample\n"):
            self.assertEqual(self.device("List of devices attached\n" + listing), "phone")

    def test_zero_multiple_or_unusable_devices(self):
        for listing in ("", "a device\nb device\n", "a unauthorized\n", "a offline\n", "a no permissions (udev)\n"):
            with self.subTest(listing=listing), self.assertRaises(android.BuildError):
                self.device("List of devices attached\n" + listing)

    def test_explicit_device_and_missing_device(self):
        self.assertEqual(self.device("a device\nb offline\n", "a"), "a")
        with self.assertRaises(android.BuildError):
            self.device("a device\n", "b")

    def metadata(self, **element):
        self.write("out/custom name.apk", "fixture")
        item = {"outputFile": "custom name.apk", "filters": [], "versionName": "test", "versionCode": 42}
        item.update(element)
        return self.write("out/output-metadata.json", json.dumps({
            "applicationId": "com.example.test", "elements": [item],
        }))

    def test_metadata_not_hardcoded_filename_or_package(self):
        with patch.object(android, "METADATA", self.metadata()):
            apk, package, name, code = android.read_artifact()
        self.assertEqual(apk.name, "custom name.apk")
        self.assertEqual((package, name, code), ("com.example.test", "test", 42))

    def test_reject_split_missing_or_escaping_apk(self):
        for element in ({"filters": [{"filterType": "ABI", "value": "x86"}]},
                        {"outputFile": "missing.apk"}, {"outputFile": "../outside.apk"}):
            with patch.object(android, "METADATA", self.metadata(**element)):
                with self.assertRaises(android.BuildError):
                    android.read_artifact()

    def test_subprocess_keeps_paths_with_spaces_as_one_argument(self):
        command = ["path with spaces/adb", "install", "-r", "a b.apk"]
        with patch.object(android.subprocess, "run", return_value=subprocess.CompletedProcess(command, 0, "")) as run:
            android.execute(command, {}, capture=True)
        self.assertEqual(run.call_args.args[0], command)
        self.assertNotIn("shell", run.call_args.kwargs)

    def test_failed_build_never_reads_or_installs_old_apk(self):
        self.write("gradlew", "fixture")
        with patch.object(android, "ROOT", self.root), patch.object(android, "WINDOWS", False), \
                patch.object(android.sys, "argv", ["android.py", "debug"]), \
                patch.object(android, "check_java"), patch.object(android, "locate_sdk", return_value=self.root), \
                patch.object(android, "locate_adb", return_value=None), patch.object(android, "log"), \
                patch.object(android, "execute", side_effect=android.BuildError("build failed")), \
                patch.object(android, "read_artifact") as read, patch.object(android, "install_and_launch") as install:
            with self.assertRaises(android.BuildError):
                android.main()
            read.assert_not_called()
            install.assert_not_called()

    def test_windows_uses_batch_wrapper_and_debug_needs_no_device(self):
        self.write("gradlew.bat", "fixture")
        with patch.object(android, "ROOT", self.root), patch.object(android, "WINDOWS", True), \
                patch.object(android.sys, "argv", ["android.py", "debug", "--console", "plain"]), \
                patch.object(android, "check_java"), patch.object(android, "locate_sdk", return_value=self.root), \
                patch.object(android, "locate_adb", return_value=None), patch.object(android, "log"), \
                patch.object(android, "execute") as execute, patch.object(android, "read_artifact"), \
                patch.object(android, "print_artifact"), patch.object(android, "select_device") as device:
            self.assertEqual(android.main(), 0)
            command = execute.call_args.args[0]
            self.assertEqual(command[0], ".\\gradlew.bat")
            self.assertIn("--no-configuration-cache", command)
            self.assertIn("--console=plain", command)
            device.assert_not_called()

    def test_install_and_start_pin_same_device(self):
        outputs = ["Success\n", "com.example.test/.MainActivity\n", "Status: ok\n"]
        with patch.object(android, "select_device") as select, patch.object(android, "log"), \
                patch.object(android, "execute", side_effect=outputs) as execute:
            android.install_and_launch((Path("app.apk"), "com.example.test", "test", 42), "adb", "phone", {}, 5)
        select.assert_called_once_with("adb", "phone", {})
        for call in execute.call_args_list:
            self.assertEqual(call.args[0][:3], ["adb", "-s", "phone"])
        self.assertEqual(execute.call_args_list[0].args[0][3:5], ["install", "-r"])

    def test_failed_install_does_not_launch(self):
        with patch.object(android, "select_device"), patch.object(android, "log"), \
                patch.object(android, "execute", return_value="Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]\n") as execute:
            with self.assertRaises(android.BuildError):
                android.install_and_launch((Path("app.apk"), "com.example.test", "test", 42), "adb", "phone", {}, 5)
            self.assertEqual(execute.call_count, 1)

    def test_activity_error_is_not_success_even_when_adb_exits_zero(self):
        outputs = ["Success\n", "com.example.test/.MainActivity\n", "Error: Activity not started\n"]
        with patch.object(android, "select_device"), patch.object(android, "log"), \
                patch.object(android, "execute", side_effect=outputs):
            with self.assertRaises(android.BuildError):
                android.install_and_launch((Path("app.apk"), "com.example.test", "test", 42), "adb", "phone", {}, 5)


if __name__ == "__main__":
    unittest.main()
