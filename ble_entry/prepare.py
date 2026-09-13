"""Refresh the ignored, configured firmware and dad's APK package."""
import json
import re
import shutil
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
target = ROOT / "Shared/PreparedForDad/BLEEntry"
target.mkdir(exist_ok=True)
source = (ROOT / "firmware/BLEEntry/BLEEntry.ino").read_text()
old = (ROOT / "Shared/PreparedForDad/BoardOTA/BoardOTA.ino").read_text()
for name in ["WIFI_SSID", "WIFI_PASSWORD"]:
    value = re.search(r'const char \*' + name + r' = (".*");', old).group(1)
    source = source.replace('"CHANGE_ME_' + name + '"', value)
cfg = json.loads((ROOT / "Shared/ble_remote.json").read_text())
source = source.replace('"CHANGE_ME_OTA_PASSWORD"', json.dumps(cfg["ota_password"]))
(target / "BLEEntry.ino").write_text(source)
for name in ["Entry.h", "Decision.h", "Household.h"]:
    shutil.copyfile(ROOT / "firmware/BLEEntry" / name, target / name)
shutil.copyfile(ROOT / "ble_entry/README.md", target / "Instructions.md")
apk = ROOT / "ble_entry/android/app/build/outputs/apk/debug/app-debug.apk"
if apk.exists():
    if json.loads((apk.parent / "output-metadata.json").read_text())["elements"][0]["versionCode"] >= 3:
        preview = ROOT / "Shared/Preview"
        preview.mkdir(exist_ok=True)
        shutil.copyfile(apk, preview / "Door-preview.apk")
    else:
        shutil.copyfile(apk, target.parent / "DoorBLE-dryrun.apk")
print("Private firmware and APK package refreshed; credentials not printed.")
