package com.hammad.autohzmax;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Status page: whether LSPosed loaded the module into this app, what the hook in system_server reported (it writes its
 * state to Settings.Global, so no log reading is needed), and how to use it. Nothing to configure.
 */
public class MainActivity extends Activity {
    /** Replaced by the hook when the module is active (the module hooks its own isActive()). */
    static boolean isActive() { return false; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        t.setPadding(p, p, p, p);
        t.setTextSize(15);
        t.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        sv.addView(t);
        setContentView(sv);
        t.setText(text());
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        ScrollView sv = (ScrollView) ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        if (sv != null && sv.getChildCount() > 0) ((TextView) sv.getChildAt(0)).setText(text());
    }

    private String text() {
        String fw = null;
        try { fw = Settings.Global.getString(getContentResolver(), Hook.STATUS_KEY); } catch (Throwable ignored) { }
        boolean active = isActive();
        if (!Hook.supported()) {
            return "Android " + android.os.Build.VERSION.RELEASE + " is not supported.\n\n"
                    + "AutoHz Max now needs Android 16 (RedMagic OS 11). On Android 15 (RedMagic OS 10) running 165 Hz through the "
                    + "module caused flicker and reboots, so since version 1.3 it hooks nothing on Android 15. You can switch it off "
                    + "in LSPosed / Vector or uninstall it.\n\n"
                    + "If you picked the 165 Hz choice that version 1.2 added to Settings, the module sets the refresh rate back to "
                    + "144 Hz after the next reboot."
                    + (fw != null ? "\n\nSystem framework: " + fw : "");
        }
        return "Module in LSPosed: " + (active ? "enabled" : "NOT ENABLED. Open LSPosed / Vector \u203A Modules \u203A AutoHz Max, switch it on, tick System Framework, Settings and AutoHz Max in its scope, then reboot.")
                + "\n\nSystem framework: " + (fw != null ? fw : active ? "no report yet. Reboot once after enabling the module (framework modules load at boot)." : "not loaded")
                + "\n\nHow to use: Settings \u203A Display \u203A Refresh rate \u203A Auto. The phone then switches between 60 Hz and the highest rate the panel offers "
                + "(144 Hz stock, 165 Hz with a 165 Hz DTBO) instead of stopping at 120 Hz. Idle still drops to 60; games keep their own Game Space setting. "
                + "If Auto was already selected, pick another rate and Auto again once."
                + "\n\nAndroid 16 (RedMagic OS 11) only. Android 15 is not supported since version 1.3."
                + "\n\nLogs: LSPosed / Vector \u203A Logs \u203A Modules, lines starting with \"AutoHzMax:\" (or adb logcat -s AutoHzMax).";
    }
}
