package com.wolfox.gps.hook;

import com.wolfox.gps.util.WFLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Camera-open diagnostics for the WolFox-owned laboratory package only. */
public final class CameraLabHooks {
    private CameraLabHooks() {}

    public static void install(XC_LoadPackage.LoadPackageParam param) {
        XposedHelpers.findAndHookMethod("android.hardware.Camera", param.classLoader,
                "open", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam hook) {
                        WFLog.i("CameraLab", "legacy camera opened in approved test package");
                    }
                });
    }
}
