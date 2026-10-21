package com.hammad.autohzmax;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Settings › Display › Refresh rate (com.android.settings, ZTE's ScreenRefreshRateSettingsFragment).
 *
 * The page lists the panel's display modes, but on RedMagic OS 10 (Android 15) it only knows how to show 60 / 90 / 120 /
 * 144 Hz (createRadioButtonPreference returns nothing for any other rate), so a 165 Hz panel (DTBO mod) gets no 165 Hz
 * choice. RedMagic OS 11 (Android 16) shows it natively as refresh_rate_mode 5. This adds the panel's top rate the same
 * way when the page lacks it: a "165 Hz" choice that writes mode 5, checked when mode 5 is set. The system framework
 * part (Hook) makes mode 5 run at the panel's highest rate on firmware that has no mode 5.
 */
final class SettingsRates {
    private SettingsRates() {}

    static final String FRAGMENT = "com.zte.settings.display.screerefresh.ScreenRefreshRateSettingsFragment";
    static final int MODE_TOP = 5;
    private static final String MODE_KEY = "refresh_rate_mode";

    /** fragment -> the choice this module added */
    private static final Map<Object, Object> added = new WeakHashMap<>();

    static void install(ClassLoader cl) {
        Class<?> frag = XposedHelpers.findClassIfExists(FRAGMENT, cl);
        if (frag == null) { Hook.log("Settings: refresh-rate page not found on this firmware"); return; }
        int n = 0;
        n += hookAll(frag, "onCreate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                try { addTop(p.thisObject); } catch (Throwable t) { Hook.log("Settings: adding the top rate failed: " + t); }
            }
        });
        n += hookAll(frag, "onRadioButtonClicked", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object mine = added.get(p.thisObject);
                if (mine == null || p.args.length == 0 || p.args[0] != mine) return;
                try {
                    Context ctx = (Context) XposedHelpers.callMethod(p.thisObject, "getContext");
                    Settings.System.putInt(ctx.getContentResolver(), MODE_KEY, MODE_TOP);
                    XposedHelpers.callMethod(p.thisObject, "updateUI");
                    Hook.log("Settings: refresh rate set to the top rate (mode " + MODE_TOP + ")");
                    p.setResult(null);
                } catch (Throwable t) { Hook.log("Settings: selecting the top rate failed: " + t); }
            }
        });
        n += hookAll(frag, "updateUI", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object mine = added.get(p.thisObject);
                if (mine == null) return;
                try {
                    Context ctx = (Context) XposedHelpers.callMethod(p.thisObject, "getContext");
                    int mode = Settings.System.getInt(ctx.getContentResolver(), MODE_KEY, 0);
                    XposedHelpers.callMethod(mine, "setChecked", mode == MODE_TOP);
                } catch (Throwable ignored) { }
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

    private static void addTop(Object frag) {
        Context ctx = (Context) XposedHelpers.callMethod(frag, "getContext");
        if (ctx == null) return;
        int top = topRate(ctx);
        if (top <= 144) { Hook.log("Settings: the panel's highest rate is " + top + " Hz, nothing to add"); return; }
        Object cat = XposedHelpers.getObjectField(frag, "mPreferenceCategory");
        if (cat == null) return;
        String key = "rate_" + top;
        if (XposedHelpers.callMethod(cat, "findPreference", key) != null) { Hook.log("Settings: " + top + " Hz is already listed by the firmware"); return; }
        Class<?> radio = XposedHelpers.findClass("com.android.settings.widget.RadioButtonPreference", frag.getClass().getClassLoader());
        Object pref = XposedHelpers.newInstance(radio, ctx);
        // title and summary in the style of the 144 Hz choice ("144Hz" -> "165Hz"), plain "165 Hz" otherwise
        CharSequence title = top + " Hz", summary = null;
        try {
            Object p144 = XposedHelpers.getObjectField(frag, "mRate144Preference");
            if (p144 != null) {
                Object t = XposedHelpers.callMethod(p144, "getTitle");
                if (t != null && t.toString().contains("144")) title = t.toString().replace("144", String.valueOf(top));
                Object s = XposedHelpers.callMethod(p144, "getSummary");
                if (s instanceof CharSequence) summary = (CharSequence) s;
            }
        } catch (Throwable ignored) { }
        XposedHelpers.callMethod(pref, "setTitle", title);
        if (summary != null) XposedHelpers.callMethod(pref, "setSummary", summary);
        XposedHelpers.callMethod(pref, "setKey", key);
        XposedHelpers.callMethod(pref, "setOrder", MODE_TOP);
        XposedHelpers.callMethod(pref, "setChecked", false);
        XposedHelpers.callMethod(pref, "setOnClickListener", frag);
        XposedHelpers.callMethod(cat, "addPreference", pref);
        try { @SuppressWarnings("unchecked") Set<Integer> rates = (Set<Integer>) XposedHelpers.getObjectField(frag, "rateSet"); rates.add(MODE_TOP); } catch (Throwable ignored) { }
        added.put(frag, pref);
        Hook.log("Settings: added a " + top + " Hz choice (refresh_rate_mode " + MODE_TOP + ")");
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
