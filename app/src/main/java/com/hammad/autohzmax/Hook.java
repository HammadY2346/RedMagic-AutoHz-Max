package com.hammad.autohzmax;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * AutoHz Max — RedMagic / ZTE system_server hook.
 *
 * ZTE's smart refresh-rate engine (com.android.server.am.ScreenRefreshRateController) switches between 60 Hz and a
 * "high step" while in the Auto mode (refresh_rate_mode = 0). That high step is computed by
 * GlobalSettingFpsController.convertModeToValue(0) and is hard-capped at 120 Hz (90/60 on lesser panels) even when the
 * panel supports 144 or 165 Hz. The fixed 144 / 165 modes exist, but then the smart engine still sends most apps to
 * 120 (getSettingsFpsByTopPackage) and never idles down.
 *
 * This module raises the Auto high step to ScreenRefreshRateController.sHighestFps (whatever the panel reports: 144 on
 * stock RedMagic 10/11 Pro, 165 with a 165 Hz DTBO). Nothing else changes: idle still drops to 60, game / per-app
 * policies still apply, the Settings page keeps working, the developer "Lock refresh rate" switch is respected.
 *
 * Verified on NX789J firmware 11.0.11 (Android 16). Class / field names are looked up defensively so other RedMagic
 * OS 11 firmware (NX809J …) should work as long as ZTE kept the same controller; misses are logged to the LSPosed log.
 */
public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "AutoHzMax: ";
    private static final String GSF = "com.android.server.am.GlobalSettingFpsController";
    private static final String SRC = "com.android.server.am.ScreenRefreshRateController";

    private static int lastLogged = -1;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if ("com.hammad.autohzmax".equals(lp.packageName)) {
            try { XposedHelpers.findAndHookMethod("com.hammad.autohzmax.MainActivity", lp.classLoader, "isActive", de.robv.android.xposed.XC_MethodReplacement.returnConstant(true)); } catch (Throwable ignored) { }
            return;
        }
        if (!"android".equals(lp.packageName)) return;
        try {
            install(lp.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "install failed: " + t);
        }
    }

    private static void install(ClassLoader cl) {
        final Class<?> gsf = XposedHelpers.findClassIfExists(GSF, cl);
        final Class<?> src = XposedHelpers.findClassIfExists(SRC, cl);
        if (gsf == null || src == null) {
            XposedBridge.log(TAG + "ZTE refresh-rate controller not found (GlobalSettingFpsController=" + (gsf != null) + ", ScreenRefreshRateController=" + (src != null) + ") — not a supported firmware");
            return;
        }
        final Field settingFps = staticInt(gsf, "sSettingFps");
        final Field mode = staticInt(gsf, "sRefreshRateMode");
        final Field lock = staticInt(gsf, "sLockRefreshRateState");
        final Field highest = staticInt(src, "sHighestFps");
        if (settingFps == null || highest == null) {
            XposedBridge.log(TAG + "fields missing (sSettingFps=" + (settingFps != null) + ", sHighestFps=" + (highest != null) + ")");
            return;
        }

        XC_MethodHook raise = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    int m = (p.args.length > 0 && p.args[0] instanceof Integer) ? (Integer) p.args[0]
                            : (mode != null ? mode.getInt(null) : 0);
                    if (m != 0) return;                                        // only the Auto mode
                    if (lock != null && lock.getInt(null) == 1) return;        // developer "Lock refresh rate" wins
                    int hi = highest.getInt(null);
                    int cur = settingFps.getInt(null);
                    if (hi <= cur) return;
                    settingFps.setInt(null, hi);
                    if (p.getResult() instanceof Integer && (Integer) p.getResult() == cur) p.setResult(hi);
                    if (lastLogged != hi) { lastLogged = hi; XposedBridge.log(TAG + "Auto high step " + cur + " -> " + hi + " Hz"); }
                } catch (Throwable t) { XposedBridge.log(TAG + "raise failed: " + t); }
            }
        };
        int n = 0;
        n += hookAll(gsf, "convertModeToValue", raise);
        n += hookAll(gsf, "getSettingFps", raise);
        XposedBridge.log(TAG + "installed (" + n + " hooks) — highest panel rate is read at runtime");
    }

    private static Field staticInt(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) return null;
            f.setAccessible(true);
            return f;
        } catch (Throwable t) { return null; }
    }

    private static int hookAll(Class<?> c, String name, XC_MethodHook cb) {
        int n = 0;
        for (Method m : c.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            try { XposedBridge.hookMethod(m, cb); n++; } catch (Throwable t) { XposedBridge.log(TAG + "hook " + name + " failed: " + t); }
        }
        if (n == 0) XposedBridge.log(TAG + "method not found: " + c.getSimpleName() + "#" + name);
        return n;
    }
}
