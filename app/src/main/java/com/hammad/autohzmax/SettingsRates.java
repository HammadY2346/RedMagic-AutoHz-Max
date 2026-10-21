package com.hammad.autohzmax;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Settings › Display › Refresh rate (com.android.settings, ZTE's ScreenRefreshRateSettingsFragment), Android 16 only.
 *
 * The Auto choice's summary names the old ceiling ("switches between 60 and 120 Hz"). With the framework part working,
 * Auto goes up to the panel's top rate, so the summary says that instead. RedMagic OS 11 lists 165 Hz itself (mode 5);
 * the 165 Hz choice that 1.2 added on Android 15 is gone with Android 15 support (1.3).
 */
final class SettingsRates {
    private SettingsRates() {}

    static final String FRAGMENT = "com.zte.settings.display.screerefresh.ScreenRefreshRateSettingsFragment";

    static void install(ClassLoader cl) {
        Class<?> frag = XposedHelpers.findClassIfExists(FRAGMENT, cl);
        if (frag == null) { Hook.log("Settings: refresh-rate page not found on this firmware"); return; }
        int n = hookAll(frag, "onCreate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                try {
                    Context ctx = (Context) XposedHelpers.callMethod(p.thisObject, "getContext");
                    if (ctx == null) return;
                    int top = topRate(ctx);
                    if (top > 120) autoSummary(p.thisObject, ctx, top);
                } catch (Throwable t) { Hook.log("Settings: Auto summary: " + t); }
            }
        });
        Hook.log("Settings: refresh-rate page hooked (" + n + " hooks)");
    }

    /** Highest refresh rate among the default display's modes (165 with the DTBO mod). */
    static int topRate(Context ctx) {
        try {
            Display d = ((DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
            int top = 0;
            for (Display.Mode m : d.getSupportedModes()) top = Math.max(top, Math.round(m.getRefreshRate()));
            return top;
        } catch (Throwable t) { return 0; }
    }

    private static void autoSummary(Object frag, Context ctx, int top) {
        String status = null;
        try { status = Settings.Global.getString(ctx.getContentResolver(), Hook.STATUS_KEY); } catch (Throwable ignored) { }
        if (status == null || !(status.contains("working") || status.contains("installed"))) return;   // the framework part is not running
        Object auto = XposedHelpers.getObjectField(frag, "mRateAutoPreference");
        if (auto == null) return;
        Object s = XposedHelpers.callMethod(auto, "getSummary");
        if (!(s instanceof CharSequence)) return;
        String text = s.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{2,3}").matcher(text);
        int max = 0;
        while (m.find()) { int v = Integer.parseInt(m.group()); if (v >= 90 && v <= 144) max = Math.max(max, v); }
        if (max == 0 || max >= top) return;
        XposedHelpers.callMethod(auto, "setSummary", text.replace(String.valueOf(max), String.valueOf(top)));
        Hook.log("Settings: Auto summary now names " + top + " Hz (was " + max + ")");
    }

    private static int hookAll(Class<?> c, String name, XC_MethodHook cb) {
        int n = 0;
        for (Method m : c.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            try { XposedBridge.hookMethod(m, cb); n++; } catch (Throwable t) { Hook.log("Settings: hook " + name + " failed: " + t); }
        }
        if (n == 0) Hook.log("Settings: method not found: " + c.getSimpleName() + "#" + name);
        return n;
    }
}
