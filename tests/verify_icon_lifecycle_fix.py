from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "com" / "wolfox" / "gps"

hook = (JAVA / "hook" / "WolFoxHook.java").read_text(encoding="utf-8")
activity = (JAVA / "hook" / "ActivityHook.java").read_text(encoding="utf-8")
bootstrap = (JAVA / "DirectBootstrap.java").read_text(encoding="utf-8")

assert "hasDirectBootstrap" not in hook, "old class-presence shortcut still exists"
assert 'HookSafety.run("install.activity"' in hook, "ActivityHook fallback is not installed"
assert "private static volatile boolean hostAttached" in bootstrap
assert bootstrap.count("hostAttached = true;") >= 2
assert "public static boolean isHostAttached()" in bootstrap
assert activity.count("DirectBootstrap.isHostAttached()") >= 4

print("PASS: floating-icon lifecycle fallback is active and direct bootstrap is runtime-gated")
