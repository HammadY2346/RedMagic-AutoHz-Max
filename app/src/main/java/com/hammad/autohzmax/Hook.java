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
 * Android 16 (RedMagic OS 11) only. Since 1.3 the module hooks nothing on Android 15 (RedMagic OS 10): running 165 Hz
 * there through the module caused flicker and reboots on global firmware. On Android 15 it only puts a 165 Hz choice
 * left over from 1.2 (refresh_rate_mode 5, which RedMagic OS 10 does not have) back to 144 Hz.
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

    /** Lowest supported Android: 16 (API 36, RedMagic OS 11). */
    static final int MIN_SDK = 36;
    /** RedMagic OS 11 lists 165 Hz as this refresh_rate_mode; RedMagic OS 10 stops at mode 4 (144 Hz). */
    private static final int MODE_165 = 5, MODE_144 = 4;
    private static final String MODE_KEY = "refresh_rate_mode";

    static boolean supported() { return android.os.Build.VERSION.SDK_INT >= MIN_SDK; }

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

    private static android.content.Context systemContext() {
        try {
            Object at = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null);
            return at == null ? null : (android.content.Context) at.getClass().getMethod("getSystemContext").invoke(at);
        } catch (Throwable t) { return null; }
    }

    private static void writeStatus() {
        try {
            android.content.Context ctx = systemContext();
            if (ctx != null) android.provider.Settings.Global.putString(ctx.getContentResolver(), STATUS_KEY, status);
        } catch (Throwable ignored) { }
    }

    /**
     * Android 15: AutoHz Max 1.2 could leave refresh_rate_mode at 5 (its 165 Hz choice), a mode RedMagic OS 10 does not
     * have. Puts it back to 144 Hz. Returns true once the settings provider answered (whatever the mode was).
     */
    private static boolean resetLeftover165() {
        try {
            android.content.Context ctx = systemContext();
            if (ctx == null) return false;
            android.content.ContentResolver cr = ctx.getContentResolver();
            int m = android.provider.Settings.System.getInt(cr, MODE_KEY, -1);
            if (m == MODE_165) {
                android.provider.Settings.System.putInt(cr, MODE_KEY, MODE_144);
                log("Android 15: refresh rate was on the 165 Hz choice from AutoHz Max 1.2, set back to 144 Hz");
                setStatus("off: Android 15 is not supported. The 165 Hz choice from version 1.2 was set back to 144 Hz.");
            }
            return true;
        } catch (Throwable t) { return false; }
    }

    /** After boot the settings provider exists: write the install state once it does (Android 15: undo a leftover 165 Hz first). */
    private static void writeAfterBoot() {
        final boolean a15 = !supported();
        Thread t = new Thread(() -> {
            boolean reset = !a15;
            for (int i = 0; i < 150; i++) {
                try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
                if (!reset) reset = resetLeftover165();                    // as early as the settings provider answers
                try {
                    Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, "sys.boot_completed");
                    if ("1".equals(v)) { if (!reset) resetLeftover165(); writeStatus(); return; }
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
            if (!supported()) return;                                          // Android 15: nothing is hooked
            try { SettingsRates.install(lp.classLoader); } catch (Throwable t) { log("Settings: install failed: " + t); }
            return;
        }
        if (!"android".equals(lp.packageName)) return;
        if (!supported()) {
            log("Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ") is not supported, nothing hooked. AutoHz Max needs Android 16 (RedMagic OS 11).");
            status = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date())
                    + " off: Android 15 is not supported, nothing is hooked";
            writeAfterBoot();
            return;
        }
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
