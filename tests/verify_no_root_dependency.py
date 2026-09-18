from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main"
MANIFEST = (MAIN / "AndroidManifest.xml").read_text(encoding="utf-8")

FORBIDDEN_PERMISSIONS = (
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.WRITE_SECURE_SETTINGS",
)

FORBIDDEN_RUNTIME_MARKERS = (
    "Runtime.getRuntime().exec",
    "new ProcessBuilder(",
    '"/system/bin/su"',
    '"/system/xbin/su"',
    '"magisk"',
    '"supersu"',
)

for permission in FORBIDDEN_PERMISSIONS:
    assert permission not in MANIFEST, f"forbidden permission found: {permission}"

java_text = "\n".join(
    path.read_text(encoding="utf-8")
    for path in sorted((MAIN / "java").rglob("*.java"))
)
lower_java = java_text.lower()
for marker in FORBIDDEN_RUNTIME_MARKERS:
    assert marker.lower() not in lower_java, f"forbidden runtime marker found: {marker}"

storage = (MAIN / "java" / "com" / "wolfox" / "gps" / "util" / "WFStorage.java")
storage_text = storage.read_text(encoding="utf-8")
assert "getBoolean(KEY_BLOCK_PLAY_REDIRECT, false)" in storage_text
assert 'android:allowBackup="false"' in MANIFEST
assert "android.permission.INTERNET" in MANIFEST

print("PASS: no root command dependency, no overlay/admin permission, safe store-flow default")
