from pathlib import Path

root = Path(__file__).resolve().parents[1]
hook = (root / "app/src/main/java/com/wolfox/gps/hook/WolFoxHook.java").read_text()
compat = (root / "app/src/main/java/com/wolfox/gps/hook/ProviderCompatibilityHooks.java").read_text()

required = [
    "ProviderCompatibilityHooks.install",
    "com.google.android.gms.location.LocationResult",
    "com.huawei.hms.location.LocationResult",
    "com.huawei.hms.location.FusedLocationProviderClient",
    "com.amap.api.location.AMapLocation",
    "com.baidu.location.BDLocation",
    "com.mapbox.android.core.location.LocationEngineResult",
]

combined = hook + compat
missing = [value for value in required if value not in combined]
if missing:
    raise SystemExit("FAIL: missing compatibility markers: " + ", ".join(missing))

if "activeModel()" not in compat or "isMocking()" not in compat:
    raise SystemExit("FAIL: compatibility hooks are not gated by active mocking")

print("PASS: optional provider compatibility is installed and state-gated")
