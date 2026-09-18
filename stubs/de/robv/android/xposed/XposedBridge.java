package de.robv.android.xposed;

import java.util.Set;

/** Compile-time API surface supplied by LSPatch/LSPosed at runtime. */
public final class XposedBridge {
    private XposedBridge() {}

    public static Set<XC_MethodHook.Unhook> hookAllMethods(
            Class<?> clazz, String methodName, XC_MethodHook callback) {
        return null;
    }
}
