package com.wolfox.gps.hook;

import com.wolfox.gps.util.WFLog;

/** Prevents an optional hook failure from crashing the host application. */
final class HookSafety {
    interface Action { void run() throws Throwable; }

    static void run(String name, Action action) {
        try {
            action.run();
        } catch (Throwable error) {
            WFLog.e("HookSafety", name + ": " + error.getClass().getSimpleName()
                    + " " + String.valueOf(error.getMessage()));
        }
    }

    private HookSafety() {}
}
