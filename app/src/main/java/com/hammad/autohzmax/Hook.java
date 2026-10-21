package com.hammad.autohzmax;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
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
 * Settings: on RedMagic OS 10 (Android 15) the refresh-rate page has no choice above 144 Hz; SettingsRates adds the
 * panel's top rate there (refresh_rate_mode 5, as RedMagic OS 11 does), and the hook here runs mode 5 at that rate.
 *
 * Verified on NX789J firmware 11.0.11 (Android 16). Class / field names are looked up defensively so other RedMagic
 * OS 11 firmware (NX809J …) should work as long as ZTE kept the same controller; misses are logged to the LSPosed log.
 */
public class Hook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String TAG = "AutoHzMax: ";
    /** Settings.Global key the hook in system_server writes its state to, so the app can show it without LSPosed logs. */
    static final String STATUS_KEY = "autohzmax_status";
    private static volatile String status = "loaded in system_server";
    private static final String GSF = "com.android.server.am.GlobalSettingFpsController";
    private static final String SRC = "com.android.server.am.ScreenRefreshRateController";

    private static int lastLogged = -1;

    @Override public void initZygote(StartupParam sp) {
        // proves the module itself is loaded (Vector / LSPosed log, and logcat -s AutoHzMax)
        log("module loaded (" + sp.modulePath + ")");
    }

    /** LSPosed module log (Logs › Modules) and logcat (tag AutoHzMax). */
    static void log(String msg) {
        try { XposedBridge.log(TAG + msg); } catch (Throwable ignored) { }
        try { android.util.Log.i("AutoHzMax", msg); } catch (Throwable ignored) { }
    }

    /** Remembers the state and writes it to Settings.Global (once the settings provider is up). */
    private static void setStatus(String s) {
        status = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " " + s;
        writeStatus();
    }

    private static void writeStatus() {
        try {
            Object at = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null);
            android.content.Context ctx = at == null ? null : (android.content.Context) at.getClass().getMethod("getSystemContext").invoke(at);
            if (ctx != null) android.provider.Settings.Global.putString(ctx.getContentResolver(), STATUS_KEY, status);
        } catch (Throwable ignored) { }
    }

    /** After boot the settings provider exists: write the install state once it does. */
    private static void writeAfterBoot() {
        Thread t = new Thread(() -> {
            for (int i = 0; i < 150; i++) {
                try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
                try {
                    Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, "sys.boot_completed");
                    if ("1".equals(v)) { writeStatus(); return; }
                } catch (Throwable ignored) { }
            }
        }, "autohzmax-status");
        t.setDaemon(true);
        t.start();
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if ("com.hammad.autohzmax".equals(lp.packageName)) {
            try { XposedHelpers.findAndHookMethod("com.hammad.autohzmax.MainActivity", lp.classLoader, "isActive", de.robv.android.xposed.XC_MethodReplacement.returnConstant(true)); } catch (Throwable ignored) { }
            return;
        }
        if ("com.android.settings".equals(lp.packageName)) {
            try { SettingsRates.install(lp.classLoader); } catch (Throwable t) { log("Settings: install failed: " + t); }
            return;
        }
        if (!"android".equals(lp.packageName)) return;
        log("system_server: installing");
        try {
            install(lp.classLoader);
        } catch (Throwable t) {
            log("install failed: " + t);
            setStatus("install failed: " + t);
        }
        writeAfterBoot();
    }

    private static void install(ClassLoader cl) {
        final Class<?> gsf = XposedHelpers.findClassIfExists(GSF, cl);
        final Class<?> src = XposedHelpers.findClassIfExists(SRC, cl);
        if (gsf == null || src == null) {
            log("ZTE refresh-rate controller not found (GlobalSettingFpsController=" + (gsf != null) + ", ScreenRefreshRateController=" + (src != null) + ") — not a supported firmware");
            setStatus("not working: this firmware has no ZTE refresh-rate controller");
            return;
        }
        final Field settingFps = staticInt(gsf, "sSettingFps");
        final Field mode = staticInt(gsf, "sRefreshRateMode");
        final Field lock = staticInt(gsf, "sLockRefreshRateState");
        final Field highest = staticInt(src, "sHighestFps");
        if (settingFps == null || highest == null) {
            log("fields missing (sSettingFps=" + (settingFps != null) + ", sHighestFps=" + (highest != null) + ")");
            setStatus("not working: the controller changed on this firmware (fields missing)");
            return;
        }

        XC_MethodHook raise = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    int m = (p.args.length > 0 && p.args[0] instanceof Integer) ? (Integer) p.args[0]
                            : (mode != null ? mode.getInt(null) : 0);
                    if (m == SettingsRates.MODE_TOP) {
                        // the top-rate choice (165 Hz): firmware without that mode (RedMagic OS 10) gets the panel's highest rate
                        int hi = highest.getInt(null);
                        if (hi > 144 && settingFps.getInt(null) != hi) {
                            settingFps.setInt(null, hi);
                            if (p.getResult() instanceof Integer) p.setResult(hi);
                            log("fixed mode " + m + " -> " + hi + " Hz");
                            setStatus("working: fixed " + hi + " Hz (the top-rate choice in Settings)");
                        }
                        return;
                    }
                    if (m != 0) return;                                        // only the Auto mode
                    if (lock != null && lock.getInt(null) == 1) return;        // developer "Lock refresh rate" wins
                    int hi = highest.getInt(null);
                    int cur = settingFps.getInt(null);
                    if (hi <= cur) {
                        // already at the panel maximum (e.g. RM Tweaks' own switch did the same first)
                        if (lastLogged != cur && cur > 120) { lastLogged = cur; log("Auto high step is " + cur + " Hz"); setStatus("working: Auto goes up to " + cur + " Hz"); }
                        return;
                    }
                    settingFps.setInt(null, hi);
                    if (p.getResult() instanceof Integer && (Integer) p.getResult() == cur) p.setResult(hi);
                    if (lastLogged != hi) { lastLogged = hi; log("Auto high step " + cur + " -> " + hi + " Hz"); setStatus("working: Auto now goes " + cur + " -> " + hi + " Hz"); }
                } catch (Throwable t) { log("raise failed: " + t); }
            }
        };
        int n = 0;
        n += hookAll(gsf, "convertModeToValue", raise);
        n += hookAll(gsf, "getSettingFps", raise);
        log("installed (" + n + " hooks) — highest panel rate is read at runtime");
        setStatus(n > 0 ? "installed (" + n + " hooks), waiting for Auto mode to be used" : "not working: hook targets not found on this firmware");
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
            try { XposedBridge.hookMethod(m, cb); n++; } catch (Throwable t) { log("hook " + name + " failed: " + t); }
        }
        if (n == 0) log("method not found: " + c.getSimpleName() + "#" + name);
        return n;
    }
}
